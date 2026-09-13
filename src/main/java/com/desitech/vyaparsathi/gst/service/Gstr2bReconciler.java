package com.desitech.vyaparsathi.gst.service;

import com.desitech.vyaparsathi.gst.dto.Gstr2bEntryDto;
import com.desitech.vyaparsathi.gst.dto.Gstr2bLineDto;
import com.desitech.vyaparsathi.gst.dto.Gstr2bReconciliationSummaryDto;
import com.desitech.vyaparsathi.gst.entity.Gstr2bEntry;
import com.desitech.vyaparsathi.gst.entity.Gstr2bImport;
import com.desitech.vyaparsathi.gst.repository.Gstr2bEntryRepository;
import com.desitech.vyaparsathi.gst.repository.Gstr2bImportRepository;
import com.desitech.vyaparsathi.purchases.entity.PurchaseInvoice;
import com.desitech.vyaparsathi.purchases.repository.PurchaseInvoiceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class Gstr2bReconciler {

    private static final String EXACT_MATCH       = "EXACT_MATCH";
    private static final String PROBABLE_MATCH    = "PROBABLE_MATCH";
    private static final String MISMATCH          = "MISMATCH";
    private static final String MISSING_IN_BOOKS  = "MISSING_IN_BOOKS";
    private static final String MISSING_IN_PORTAL = "MISSING_IN_PORTAL";

    private static final BigDecimal TAX_TOLERANCE = new BigDecimal("1.00");
    private static final int LEVENSHTEIN_THRESHOLD = 2;

    private final ObjectMapper objectMapper;
    private final PurchaseInvoiceRepository purchaseRepo;
    private final Gstr2bImportRepository importRepo;
    private final Gstr2bEntryRepository entryRepo;

    public Gstr2bReconciler(ObjectMapper objectMapper,
                            PurchaseInvoiceRepository purchaseRepo,
                            Gstr2bImportRepository importRepo,
                            Gstr2bEntryRepository entryRepo) {
        this.objectMapper = objectMapper;
        this.purchaseRepo = purchaseRepo;
        this.importRepo   = importRepo;
        this.entryRepo    = entryRepo;
    }

    @Transactional
    public Gstr2bReconciliationSummaryDto reconcile(MultipartFile file, Long shopId, int year, int month) {
        List<Gstr2bLineDto> portalLines = parseJson(file);

        LocalDate periodStart = LocalDate.of(year, month, 1);
        LocalDate periodEnd   = periodStart.withDayOfMonth(periodStart.lengthOfMonth());

        List<PurchaseInvoice> purchases = purchaseRepo
                .findAllByShopIdAndPurchaseDateBetween(shopId, periodStart, periodEnd);

        // Build lookup map keyed by normalised (gstin#invoiceNo)
        Map<String, PurchaseInvoice> purchaseMap = new LinkedHashMap<>();
        Map<String, List<PurchaseInvoice>> gstinToPurchases = new HashMap<>();
        for (PurchaseInvoice pi : purchases) {
            String gstin = pi.getSupplier() != null ? pi.getSupplier().getGstin() : null;
            if (gstin == null) continue;
            String inv = pi.getSupplierInvoiceNo();
            if (inv != null) {
                purchaseMap.put(compositeKey(gstin, inv), pi);
            }
            gstinToPurchases.computeIfAbsent(normalise(gstin), k -> new ArrayList<>()).add(pi);
        }

        Set<Long> matchedPurchaseIds = new HashSet<>();
        List<Gstr2bEntry> entries = new ArrayList<>();

        // --- Classify portal lines ---
        for (Gstr2bLineDto line : portalLines) {
            Gstr2bEntry entry = buildEntryFromLine(line);
            String status;
            PurchaseInvoice matched = null;

            String key = compositeKey(line.getSupplierGstin(), line.getInvoiceNumber());
            PurchaseInvoice candidate = purchaseMap.get(key);

            if (candidate != null) {
                BigDecimal portalTax = totalTax(line.getIgstAmount(), line.getCgstAmount(), line.getSgstAmount());
                BigDecimal booksTax  = totalTax(candidate.getTotalIgst(), candidate.getTotalCgst(), candidate.getTotalSgst());
                BigDecimal diff = portalTax.subtract(booksTax).abs();

                if (diff.compareTo(TAX_TOLERANCE) <= 0) {
                    status = EXACT_MATCH;
                } else {
                    status = MISMATCH;
                }
                matched = candidate;
            } else {
                // Try Levenshtein on same-GSTIN entries
                List<PurchaseInvoice> sameGstin = gstinToPurchases.getOrDefault(
                        normalise(line.getSupplierGstin()), Collections.emptyList());
                PurchaseInvoice probable = null;
                for (PurchaseInvoice pi : sameGstin) {
                    String piNorm = normaliseInvoiceNo(pi.getSupplierInvoiceNo());
                    String portalNorm = normaliseInvoiceNo(line.getInvoiceNumber());
                    if (levenshtein(piNorm, portalNorm) <= LEVENSHTEIN_THRESHOLD) {
                        BigDecimal portalTax = totalTax(line.getIgstAmount(), line.getCgstAmount(), line.getSgstAmount());
                        BigDecimal booksTax  = totalTax(pi.getTotalIgst(), pi.getTotalCgst(), pi.getTotalSgst());
                        if (portalTax.subtract(booksTax).abs().compareTo(TAX_TOLERANCE) <= 0) {
                            probable = pi;
                            break;
                        }
                    }
                }
                if (probable != null) {
                    status = PROBABLE_MATCH;
                    matched = probable;
                } else {
                    status = MISSING_IN_BOOKS;
                }
            }

            if (matched != null) {
                matchedPurchaseIds.add(matched.getId());
                entry.setMatchedPurchaseId(matched.getId());
                fillBooksAmounts(entry, matched);
            }
            entry.setMatchStatus(status);
            entries.add(entry);
        }

        // --- Missing in Portal: ITC-eligible purchases not matched ---
        for (PurchaseInvoice pi : purchases) {
            if (pi.isItcEligible() && !matchedPurchaseIds.contains(pi.getId())) {
                Gstr2bEntry missing = buildEntryFromPurchase(pi);
                missing.setMatchStatus(MISSING_IN_PORTAL);
                entries.add(missing);
            }
        }

        // --- Persist ---
        int matched = 0, mismatched = 0, missingBooks = 0, missingPortal = 0;
        for (Gstr2bEntry e : entries) {
            switch (e.getMatchStatus()) {
                case EXACT_MATCH, PROBABLE_MATCH -> matched++;
                case MISMATCH -> mismatched++;
                case MISSING_IN_BOOKS -> missingBooks++;
                case MISSING_IN_PORTAL -> missingPortal++;
            }
        }

        Gstr2bImport imp = new Gstr2bImport();
        imp.setReturnPeriod(String.format("%02d-%d", month, year));
        imp.setFileName(file.getOriginalFilename());
        imp.setImportedAt(Instant.now());
        imp.setTotalInvoices(portalLines.size());
        imp.setMatchedCount(matched);
        imp.setMismatchedCount(mismatched);
        imp.setMissingInBooksCount(missingBooks);
        imp.setMissingInPortalCount(missingPortal);
        Gstr2bImport savedImport = importRepo.save(imp);

        for (Gstr2bEntry e : entries) {
            e.setGstr2bImport(savedImport);
        }
        entryRepo.saveAll(entries);

        // Update purchase match status
        for (Gstr2bEntry e : entries) {
            if (e.getMatchedPurchaseId() != null) {
                purchaseRepo.findById(e.getMatchedPurchaseId()).ifPresent(pi -> {
                    pi.setGstr2bMatchStatus(e.getMatchStatus());
                    purchaseRepo.save(pi);
                });
            }
        }

        return buildSummaryDto(savedImport, entries);
    }

    @Transactional(readOnly = true)
    public Gstr2bReconciliationSummaryDto getReconciliation(Long shopId, int year, int month) {
        String period = String.format("%02d-%d", month, year);
        Gstr2bImport imp = importRepo
                .findTopByShopIdAndReturnPeriodOrderByImportedAtDesc(shopId, period)
                .orElse(null);
        if (imp == null) return null;
        List<Gstr2bEntry> entries = entryRepo.findByGstr2bImportId(imp.getId());
        return buildSummaryDto(imp, entries);
    }

    @Transactional
    public Gstr2bEntryDto manualMatch(Long entryId, Long purchaseId) {
        Gstr2bEntry entry = entryRepo.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found: " + entryId));
        entry.setMatchStatus("MANUAL_MATCH");
        entry.setMatchedPurchaseId(purchaseId);
        purchaseRepo.findById(purchaseId).ifPresent(pi -> {
            pi.setGstr2bMatchStatus("MANUAL_MATCH");
            purchaseRepo.save(pi);
        });
        return toEntryDto(entryRepo.save(entry), null);
    }

    @Transactional
    public Gstr2bEntryDto acceptMismatch(Long entryId) {
        Gstr2bEntry entry = entryRepo.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found: " + entryId));
        entry.setMatchStatus("MANUAL_MATCH");
        if (entry.getMatchedPurchaseId() != null) {
            purchaseRepo.findById(entry.getMatchedPurchaseId()).ifPresent(pi -> {
                pi.setGstr2bMatchStatus("MANUAL_MATCH");
                purchaseRepo.save(pi);
            });
        }
        return toEntryDto(entryRepo.save(entry), null);
    }

    // ── JSON Parsing ──────────────────────────────────────────────────────────

    private List<Gstr2bLineDto> parseJson(MultipartFile file) {
        try {
            JsonNode root = objectMapper.readTree(file.getInputStream());
            List<Gstr2bLineDto> lines = new ArrayList<>();
            parseSection(root, "b2b", "B2B", lines);
            parseSection(root, "cdnr", "CDNR", lines);
            return lines;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse GSTR-2B JSON: " + e.getMessage(), e);
        }
    }

    private void parseSection(JsonNode root, String section, String type, List<Gstr2bLineDto> out) {
        JsonNode arr = findNode(root, section);
        if (arr == null || !arr.isArray()) return;
        for (JsonNode supplier : arr) {
            String gstin   = text(supplier, "ctin");
            String trdName = text(supplier, "trdnm");
            JsonNode invArr = supplier.path("inv");
            if (!invArr.isArray()) continue;
            for (JsonNode inv : invArr) {
                Gstr2bLineDto line = new Gstr2bLineDto();
                line.setSupplierGstin(gstin);
                line.setSupplierName(trdName);
                line.setInvoiceNumber(text(inv, "inum"));
                line.setInvoiceType(type);
                line.setInvoiceDate(parseDate(text(inv, "dt")));
                line.setInvoiceValue(decimal(inv, "val"));
                line.setItcAvailability("Y"); // default; updated from itm if present

                BigDecimal igst = BigDecimal.ZERO, cgst = BigDecimal.ZERO,
                           sgst = BigDecimal.ZERO, cess = BigDecimal.ZERO, txval = BigDecimal.ZERO;
                JsonNode itms = inv.path("itms");
                if (itms.isArray()) {
                    for (JsonNode itm : itms) {
                        JsonNode det = itm.path("itm_det");
                        txval = txval.add(safe(decimal(det, "txval")));
                        igst  = igst.add(safe(decimal(det, "igst")));
                        cgst  = cgst.add(safe(decimal(det, "cgst")));
                        sgst  = sgst.add(safe(decimal(det, "sgst")));
                        cess  = cess.add(safe(decimal(det, "cess")));
                        String elg = text(det, "elg");
                        if (elg != null && !elg.equalsIgnoreCase("Y")) line.setItcAvailability("N");
                    }
                }
                line.setTaxableValue(txval);
                line.setIgstAmount(igst);
                line.setCgstAmount(cgst);
                line.setSgstAmount(sgst);
                line.setCessAmount(cess);
                out.add(line);
            }
        }
    }

    private JsonNode findNode(JsonNode root, String key) {
        if (root == null) return null;
        if (root.has(key)) return root.get(key);
        if (root.isObject()) {
            for (JsonNode child : root) {
                JsonNode found = findNode(child, key);
                if (found != null) return found;
            }
        }
        return null;
    }

    // ── Normalisation & matching utilities ───────────────────────────────────

    static String normalise(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    static String normaliseInvoiceNo(String raw) {
        if (raw == null) return "";
        String[] tokens = raw.toUpperCase().split("[^A-Z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String t : tokens) {
            if (t.isEmpty()) continue;
            try {
                sb.append(Integer.parseInt(t)); // strips leading zeros
            } catch (NumberFormatException e) {
                sb.append(t);
            }
        }
        return sb.toString();
    }

    private String compositeKey(String gstin, String invoiceNo) {
        return normalise(gstin) + "#" + normaliseInvoiceNo(invoiceNo);
    }

    static int levenshtein(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int m = a.length(), n = b.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) dp[i][j] = dp[i - 1][j - 1];
                else dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
            }
        }
        return dp[m][n];
    }

    private BigDecimal totalTax(BigDecimal igst, BigDecimal cgst, BigDecimal sgst) {
        return safe(igst).add(safe(cgst)).add(safe(sgst));
    }

    private BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    // ── DTO builders ─────────────────────────────────────────────────────────

    private Gstr2bEntry buildEntryFromLine(Gstr2bLineDto line) {
        Gstr2bEntry e = new Gstr2bEntry();
        e.setSupplierGstin(line.getSupplierGstin());
        e.setSupplierName(line.getSupplierName());
        e.setInvoiceNumber(line.getInvoiceNumber());
        e.setInvoiceType(line.getInvoiceType());
        e.setInvoiceDate(line.getInvoiceDate());
        e.setInvoiceValue(line.getInvoiceValue());
        e.setTaxableValue(line.getTaxableValue());
        e.setIgstAmount(line.getIgstAmount());
        e.setCgstAmount(line.getCgstAmount());
        e.setSgstAmount(line.getSgstAmount());
        e.setCessAmount(line.getCessAmount());
        e.setItcAvailability(line.getItcAvailability());
        return e;
    }

    private Gstr2bEntry buildEntryFromPurchase(PurchaseInvoice pi) {
        Gstr2bEntry e = new Gstr2bEntry();
        if (pi.getSupplier() != null) {
            e.setSupplierGstin(pi.getSupplier().getGstin());
            e.setSupplierName(pi.getSupplier().getSupplierName());
        }
        e.setInvoiceNumber(pi.getSupplierInvoiceNo());
        e.setInvoiceType("B2B");
        e.setInvoiceDate(pi.getPurchaseDate());
        e.setInvoiceValue(pi.getTotalAmount());
        e.setTaxableValue(pi.getTotalTaxableAmount());
        e.setIgstAmount(pi.getTotalIgst());
        e.setCgstAmount(pi.getTotalCgst());
        e.setSgstAmount(pi.getTotalSgst());
        e.setMatchedPurchaseId(pi.getId());
        return e;
    }

    private void fillBooksAmounts(Gstr2bEntry entry, PurchaseInvoice pi) {
        // Books amounts stored back on the entry for comparison
        // (entry's igst/cgst/sgst are portal amounts; we surface both in the DTO)
        entry.setMatchedPurchaseId(pi.getId());
    }

    private Gstr2bReconciliationSummaryDto buildSummaryDto(Gstr2bImport imp, List<Gstr2bEntry> entries) {
        Gstr2bReconciliationSummaryDto dto = new Gstr2bReconciliationSummaryDto();
        dto.setImportId(imp.getId());
        dto.setReturnPeriod(imp.getReturnPeriod());
        dto.setFileName(imp.getFileName());
        dto.setImportedAt(imp.getImportedAt() != null ? imp.getImportedAt().toString() : null);
        dto.setTotalInvoices(imp.getTotalInvoices());
        dto.setMatchedCount(imp.getMatchedCount());
        dto.setMismatchedCount(imp.getMismatchedCount());
        dto.setMissingInBooksCount(imp.getMissingInBooksCount());
        dto.setMissingInPortalCount(imp.getMissingInPortalCount());
        dto.setEntries(entries.stream().map(e -> toEntryDto(e, null)).collect(Collectors.toList()));
        return dto;
    }

    private Gstr2bEntryDto toEntryDto(Gstr2bEntry e, PurchaseInvoice matched) {
        Gstr2bEntryDto dto = new Gstr2bEntryDto();
        dto.setId(e.getId());
        dto.setSupplierGstin(e.getSupplierGstin());
        dto.setSupplierName(e.getSupplierName());
        dto.setInvoiceNumber(e.getInvoiceNumber());
        dto.setInvoiceType(e.getInvoiceType());
        dto.setInvoiceDate(e.getInvoiceDate());
        dto.setInvoiceValue(e.getInvoiceValue());
        dto.setTaxableValue(e.getTaxableValue());
        dto.setPortalIgst(e.getIgstAmount());
        dto.setPortalCgst(e.getCgstAmount());
        dto.setPortalSgst(e.getSgstAmount());
        dto.setItcAvailability(e.getItcAvailability());
        dto.setMatchStatus(e.getMatchStatus());
        dto.setMatchedPurchaseId(e.getMatchedPurchaseId());
        return dto;
    }

    // ── Parse helpers ─────────────────────────────────────────────────────────

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    );

    private LocalDate parseDate(String dt) {
        if (dt == null || dt.isBlank()) return null;
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(dt.trim(), fmt); } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) return BigDecimal.ZERO;
        try { return new BigDecimal(v.asText()).setScale(2, RoundingMode.HALF_UP); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}
