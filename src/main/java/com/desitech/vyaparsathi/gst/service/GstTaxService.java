package com.desitech.vyaparsathi.gst.service;

import com.desitech.vyaparsathi.accounting.entity.CreditNote;
import com.desitech.vyaparsathi.accounting.entity.CreditNoteItem;
import com.desitech.vyaparsathi.accounting.repository.CreditNoteRepository;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.gst.dto.Gstr1ExportDto;
import com.desitech.vyaparsathi.gst.dto.Gstr3bSummaryDto;
import com.desitech.vyaparsathi.purchases.entity.PurchaseInvoice;
import com.desitech.vyaparsathi.purchases.repository.PurchaseInvoiceRepository;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GstTaxService {

    private final SaleRepository saleRepo;
    private final PurchaseInvoiceRepository purchaseRepo;
    private final CreditNoteRepository creditRepo;
    private final ShopRepository shopRepo;
    private final GstJurisdictionService jurisdictionService;

    public GstTaxService(SaleRepository saleRepo,
                         PurchaseInvoiceRepository purchaseRepo,
                         CreditNoteRepository creditRepo,
                         ShopRepository shopRepo,
                         GstJurisdictionService jurisdictionService) {
        this.saleRepo = saleRepo;
        this.purchaseRepo = purchaseRepo;
        this.creditRepo = creditRepo;
        this.shopRepo = shopRepo;
        this.jurisdictionService = jurisdictionService;
    }

    @Transactional(readOnly = true)
    public Gstr1ExportDto generateGstr1(int year, int month) {
        Long shopId = TenantUtils.getCurrentShopId();
        Shop shop = shopRepo.findById(shopId).orElseThrow(() -> new IllegalArgumentException("Shop not found"));

        LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime end = start.plusMonths(1).minusNanos(1);

        List<Sale> sales = saleRepo.findAllByShopIdAndDateBetween(shopId, start, end);

        Gstr1ExportDto export = new Gstr1ExportDto();
        export.setGstin(shop.getGstin() != null ? shop.getGstin() : "UNREGISTERED");
        // GSTN's fp format is MMYYYY, e.g. "042025" for April 2025.
        export.setFp(String.format("%02d%d", month, year));

        BigDecimal totalGross = BigDecimal.ZERO;
        // HSN aggregator — keyed by (hsn, rate, uqc). One entry per unique combination
        // across the entire filing; feeds the "hsn" section at the end.
        Map<HsnKey, HsnAgg> hsnBucket = new LinkedHashMap<>();

        for (Sale sale : sales) {
            // Skip DRAFT / PROFORMA / CANCELLED sales — only real, completed
            // outward supplies belong on the return.
            if (!isReportable(sale)) continue;

            totalGross = totalGross.add(sale.getGrandTotal());
            String pos = derivePlaceOfSupply(sale, shop);
            List<Gstr1ExportDto.TaxItem> items = buildTaxItems(sale.getSaleItems());
            // Feed the HSN aggregator with every line from this sale (regardless
            // of whether the sale ends up on the B2B / B2CL / B2CS section).
            accumulateHsn(hsnBucket, sale.getSaleItems());

            boolean isB2B = sale.getCustomer() != null
                    && sale.getCustomer().getGstin() != null
                    && !sale.getCustomer().getGstin().isBlank();
            BigDecimal grandTotal = sale.getGrandTotal();
            boolean isB2CLarge = !isB2B
                    && grandTotal.compareTo(new BigDecimal("250000")) > 0
                    && !isIntraState(shop, sale);

            if (isB2B) {
                Gstr1ExportDto.B2bInvoice b2b = new Gstr1ExportDto.B2bInvoice();
                b2b.setCtin(sale.getCustomer().getGstin());
                b2b.setInvNo(sale.getInvoiceNo());
                b2b.setInvDate(sale.getDate().toLocalDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
                b2b.setVal(grandTotal);
                b2b.setPos(pos);
                // Reverse charge indicator — "Y" when the recipient is liable
                // to pay tax under §9(3)/§9(4). Otherwise "N".
                b2b.setRchrg(sale.isReverseCharge() ? "Y" : "N");
                b2b.setItems(items);
                export.getB2b().add(b2b);
            } else if (isB2CLarge) {
                Gstr1ExportDto.B2cLargeInvoice b2cl = new Gstr1ExportDto.B2cLargeInvoice();
                b2cl.setInvNo(sale.getInvoiceNo());
                b2cl.setInvDate(sale.getDate().toLocalDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
                b2cl.setVal(grandTotal);
                b2cl.setPos(pos);
                b2cl.setItems(items);
                export.getB2cl().add(b2cl);
            } else {
                // B2CS: one summary row per (POS, rate) tuple. Individual invoice
                // details are not required — GSTN wants aggregates.
                for (Gstr1ExportDto.TaxItem it : items) {
                    Gstr1ExportDto.B2cSmallSummary row = new Gstr1ExportDto.B2cSmallSummary();
                    row.setPos(pos);
                    row.setRt(it.getRt());
                    row.setTxval(it.getTxval());
                    row.setIamti(it.getIamt());
                    row.setCamti(it.getCamt());
                    row.setSamti(it.getSamt());
                    row.setUamti(it.getUamt());
                    export.getB2cs().add(row);
                }
            }
        }

        // Credit notes issued during the period. CDNR is B2B-only per GSTN;
        // credit notes to unregistered customers belong in CDNUR (not yet modeled).
        LocalDate fromDate = start.toLocalDate();
        LocalDate toDate = end.toLocalDate();
        List<CreditNote> notes = creditRepo.findAllByShopIdAndCreditNoteDateBetween(shopId, fromDate, toDate);
        for (CreditNote note : notes) {
            if (note.getSale() == null || note.getCustomer() == null) continue;
            String ctin = note.getCustomer().getGstin();
            if (ctin == null || ctin.isBlank()) continue; // B2C credit notes → CDNUR (skipped for now)

            Gstr1ExportDto.CdnrNote cn = new Gstr1ExportDto.CdnrNote();
            cn.setCtin(ctin);
            cn.setNtNo(note.getCreditNoteNo());
            cn.setNtDt(note.getCreditNoteDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
            cn.setNty("C");
            cn.setInum(note.getSale().getInvoiceNo());
            cn.setVal(note.getTotalAmount());
            cn.setItems(buildTaxItemsFromNote(note.getItems()));
            export.getCdnr().add(cn);
        }

        // Finalize HSN section. Assign sequential num within GSTR-1.
        int hsnIdx = 1;
        for (Map.Entry<HsnKey, HsnAgg> e : hsnBucket.entrySet()) {
            Gstr1ExportDto.HsnSummary hs = new Gstr1ExportDto.HsnSummary();
            hs.setNum(hsnIdx++);
            hs.setHsnSc(e.getKey().hsn);
            hs.setDesc(e.getValue().desc);
            hs.setUqc(e.getKey().uqc);
            hs.setQty(e.getValue().qty.setScale(2, RoundingMode.HALF_UP));
            hs.setVal(e.getValue().val.setScale(2, RoundingMode.HALF_UP));
            hs.setTxval(e.getValue().txval.setScale(2, RoundingMode.HALF_UP));
            hs.setIamt(e.getValue().iamt.setScale(2, RoundingMode.HALF_UP));
            hs.setCamt(e.getValue().camt.setScale(2, RoundingMode.HALF_UP));
            hs.setSamt(e.getValue().samt.setScale(2, RoundingMode.HALF_UP));
            hs.setUamt(e.getValue().uamt.setScale(2, RoundingMode.HALF_UP));
            export.getHsn().add(hs);
        }

        export.setGrossTurnover(totalGross);
        return export;
    }

    // ────────────────────────────────────────────────────────────
    // GSTR-1 helpers
    // ────────────────────────────────────────────────────────────

    /** True if the sale should appear on a filed return. Excludes drafts, proformas, and cancelled sales. */
    private boolean isReportable(Sale sale) {
        com.desitech.vyaparsathi.sales.enums.SaleStatus status = sale.getStatus();
        if (status == com.desitech.vyaparsathi.sales.enums.SaleStatus.DRAFT
                || status == com.desitech.vyaparsathi.sales.enums.SaleStatus.CANCELLED) return false;
        return !sale.isProforma();
    }

    /**
     * Bucket sale line items by GST rate to build the TaxItem list expected by
     * GSTN under each invoice. One entry per unique rate present on the invoice.
     */
    private List<Gstr1ExportDto.TaxItem> buildTaxItems(List<SaleItem> lines) {
        Map<Double, Gstr1ExportDto.TaxItem> byRate = new LinkedHashMap<>();
        for (SaleItem line : lines) {
            double rate = line.getGstType() != null ? line.getGstType().getRate() : 0.0;
            Gstr1ExportDto.TaxItem bucket = byRate.computeIfAbsent(rate, r -> {
                Gstr1ExportDto.TaxItem t = new Gstr1ExportDto.TaxItem();
                t.setRt(r);
                t.setTxval(BigDecimal.ZERO);
                return t;
            });
            bucket.setTxval(addOrZero(bucket.getTxval(), line.getTaxableValue()));
            bucket.setCamt(addOrZero(bucket.getCamt(), line.getCgstAmt()));
            bucket.setSamt(addOrZero(bucket.getSamt(), line.getSgstAmt()));
            bucket.setIamt(addOrZero(bucket.getIamt(), line.getIgstAmt()));
            bucket.setUamt(addOrZero(bucket.getUamt(), line.getUtgstAmt()));
        }
        int idx = 1;
        List<Gstr1ExportDto.TaxItem> out = new ArrayList<>(byRate.values());
        for (Gstr1ExportDto.TaxItem it : out) it.setNum(idx++);
        return out;
    }

    /**
     * Bucket credit-note line items by rate. CreditNoteItem doesn't carry a
     * gstType enum today, so the rate is derived from the ratio of tax to
     * taxable value. Zero taxable values produce a zero-rate bucket.
     */
    private List<Gstr1ExportDto.TaxItem> buildTaxItemsFromNote(List<CreditNoteItem> lines) {
        Map<Double, Gstr1ExportDto.TaxItem> byRate = new LinkedHashMap<>();
        for (CreditNoteItem line : lines) {
            BigDecimal txval = line.getTaxableValue() == null ? BigDecimal.ZERO : line.getTaxableValue();
            BigDecimal totalTax = addOrZero(line.getCgstAmt(), line.getSgstAmt());
            totalTax = addOrZero(totalTax, line.getIgstAmt());
            double rate = 0.0;
            if (txval.compareTo(BigDecimal.ZERO) > 0 && totalTax.compareTo(BigDecimal.ZERO) > 0) {
                // Effective rate = (totalTax / taxable) × 100. For intra-state notes
                // CGST+SGST already equals the full rate (two halves), so no doubling.
                rate = totalTax.divide(txval, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP)
                        .doubleValue();
            }
            double rateKey = rate;
            Gstr1ExportDto.TaxItem bucket = byRate.computeIfAbsent(rateKey, r -> {
                Gstr1ExportDto.TaxItem t = new Gstr1ExportDto.TaxItem();
                t.setRt(r);
                t.setTxval(BigDecimal.ZERO);
                return t;
            });
            bucket.setTxval(addOrZero(bucket.getTxval(), txval));
            bucket.setCamt(addOrZero(bucket.getCamt(), line.getCgstAmt()));
            bucket.setSamt(addOrZero(bucket.getSamt(), line.getSgstAmt()));
            bucket.setIamt(addOrZero(bucket.getIamt(), line.getIgstAmt()));
        }
        int idx = 1;
        List<Gstr1ExportDto.TaxItem> out = new ArrayList<>(byRate.values());
        for (Gstr1ExportDto.TaxItem it : out) it.setNum(idx++);
        return out;
    }

    /** POS is either the sale's explicit placeOfSupply or the shop's own state as fallback. */
    private String derivePlaceOfSupply(Sale sale, Shop shop) {
        return sale.getPlaceOfSupply() != null ? sale.getPlaceOfSupply() : shop.getState();
    }

    // ── HSN aggregation ─────────────────────────────────────────

    private static final class HsnKey {
        final String hsn;
        final String uqc;
        HsnKey(String hsn, String uqc) {
            this.hsn = hsn == null ? "" : hsn;
            this.uqc = uqc == null ? "OTH" : uqc;
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof HsnKey)) return false;
            HsnKey k = (HsnKey) o;
            return hsn.equals(k.hsn) && uqc.equals(k.uqc);
        }
        @Override public int hashCode() { return Objects.hash(hsn, uqc); }
    }

    private static final class HsnAgg {
        String desc = "";
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal val = BigDecimal.ZERO;
        BigDecimal txval = BigDecimal.ZERO;
        BigDecimal iamt = BigDecimal.ZERO;
        BigDecimal camt = BigDecimal.ZERO;
        BigDecimal samt = BigDecimal.ZERO;
        BigDecimal uamt = BigDecimal.ZERO;
    }

    private void accumulateHsn(Map<HsnKey, HsnAgg> bucket, List<SaleItem> lines) {
        for (SaleItem line : lines) {
            String hsn = resolveHsn(line);
            String uqc = mapToUqc(resolveUnit(line));
            HsnKey key = new HsnKey(hsn, uqc);
            HsnAgg agg = bucket.computeIfAbsent(key, k -> new HsnAgg());
            if (agg.desc.isEmpty()) {
                if (line.getItemVariant() != null
                        && line.getItemVariant().getItem() != null
                        && line.getItemVariant().getItem().getName() != null) {
                    agg.desc = line.getItemVariant().getItem().getName();
                } else if (line.getCustomItemName() != null) {
                    agg.desc = line.getCustomItemName();
                }
            }
            agg.qty = addOrZero(agg.qty, line.getQty());
            BigDecimal lineTaxable = line.getTaxableValue() == null ? BigDecimal.ZERO : line.getTaxableValue();
            BigDecimal lineTax = addOrZero(line.getCgstAmt(), line.getSgstAmt());
            lineTax = addOrZero(lineTax, line.getIgstAmt());
            lineTax = addOrZero(lineTax, line.getUtgstAmt());
            agg.txval = addOrZero(agg.txval, lineTaxable);
            agg.val = addOrZero(agg.val, lineTaxable.add(lineTax == null ? BigDecimal.ZERO : lineTax));
            agg.iamt = addOrZero(agg.iamt, line.getIgstAmt());
            agg.camt = addOrZero(agg.camt, line.getCgstAmt());
            agg.samt = addOrZero(agg.samt, line.getSgstAmt());
            agg.uamt = addOrZero(agg.uamt, line.getUtgstAmt());
        }
    }

    private String resolveHsn(SaleItem line) {
        if (line.getCustomHsnSac() != null && !line.getCustomHsnSac().isBlank()) return line.getCustomHsnSac();
        if (line.getItemVariant() != null && line.getItemVariant().getHsn() != null) return line.getItemVariant().getHsn();
        return ""; // downstream will show blank — GSTN filing will reject; shop must fix
    }

    private String resolveUnit(SaleItem line) {
        if (line.getCustomUnit() != null && !line.getCustomUnit().isBlank()) return line.getCustomUnit();
        if (line.getItemVariant() != null && line.getItemVariant().getUnit() != null) return line.getItemVariant().getUnit();
        return "";
    }

    /**
     * Map an ad-hoc unit string to the closest GSTN Unit Quantity Code. GSTN
     * accepts a fixed list; anything unrecognized falls back to "OTH" (Other).
     */
    private String mapToUqc(String unit) {
        if (unit == null) return "OTH";
        String u = unit.trim().toLowerCase(Locale.ROOT);
        if (u.isEmpty()) return "OTH";
        switch (u) {
            case "nos": case "no": case "no.": case "number": case "count": return "NOS";
            case "pcs": case "pc": case "piece": case "pieces": return "PCS";
            case "kg": case "kgs": case "kilogram": case "kilograms": return "KGS";
            case "gm": case "gms": case "g": case "gram": case "grams": return "GMS";
            case "ton": case "tonnes": case "tonne": case "mt": return "TON";
            case "l": case "ltr": case "litre": case "liter": case "litres": case "liters": return "LTR";
            case "ml": case "millilitre": case "milliliter": return "MLT";
            case "m": case "mtr": case "metre": case "meter": case "metres": case "meters": return "MTR";
            case "cm": case "centimetre": case "centimeter": return "CMS";
            case "sqm": case "sq.m": case "square metre": return "SQM";
            case "sqf": case "sq.f": case "sq.ft": case "square foot": return "SQF";
            case "box": case "boxes": return "BOX";
            case "bag": case "bags": return "BAG";
            case "pack": case "packs": case "packet": case "packets": return "PAC";
            case "unit": case "units": case "unt": return "UNT";
            case "dozen": case "doz": return "DOZ";
            case "roll": case "rolls": return "ROL";
            default: return "OTH";
        }
    }

    private static BigDecimal addOrZero(BigDecimal a, BigDecimal b) {
        BigDecimal left = a == null ? BigDecimal.ZERO : a;
        BigDecimal right = b == null ? BigDecimal.ZERO : b;
        return left.add(right);
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** True when the sale has at least one line at a non-zero GST rate. */
    private static boolean hasNonZeroGst(Sale sale) {
        return sale.getSaleItems().stream()
                .anyMatch(i -> i.getGstType() != null && i.getGstType().getRate() > 0);
    }

    @Transactional(readOnly = true)
    public Gstr3bSummaryDto generateGstr3b(int year, int month) {
        Long shopId = TenantUtils.getCurrentShopId();
        Shop shop = shopRepo.findById(shopId).orElseThrow(() -> new IllegalArgumentException("Shop not found"));

        LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime end = start.plusMonths(1).minusNanos(1);
        LocalDate fromDate = start.toLocalDate();
        LocalDate toDate = end.toLocalDate();

        List<Sale> sales = saleRepo.findAllByShopIdAndDateBetween(shopId, start, end);
        // Repo-level date filter — previously unpaged() then in-memory filter,
        // which scaled poorly. Now: only the month's purchases hit the JVM.
        List<PurchaseInvoice> purchases = purchaseRepo
                .findAllByShopIdAndPurchaseDateBetween(shopId, fromDate, toDate);

        Gstr3bSummaryDto summary = new Gstr3bSummaryDto();
        summary.setGstin(shop.getGstin() != null ? shop.getGstin() : "UNREGISTERED");
        summary.setMonthYear(String.format("%02d-%d", month, year));

        // ── Section 3.1(a): Outward Taxable Supplies (other than zero-rated,
        // nil-rated, exempt). Sales with GST charged. ─────────────────────
        // ── Section 3.1(c): Other outward supplies (nil-rated, exempt).
        // Sales without GST (isGstRequired=false or every line at GST_0). ──
        BigDecimal outTaxable = BigDecimal.ZERO;
        BigDecimal outCgst = BigDecimal.ZERO;
        BigDecimal outSgst = BigDecimal.ZERO;
        BigDecimal outIgst = BigDecimal.ZERO;
        BigDecimal outUtgst = BigDecimal.ZERO;
        BigDecimal nilRatedTaxable = BigDecimal.ZERO;

        for (Sale sale : sales) {
            if (!isReportable(sale)) continue;
            boolean isTaxable = Boolean.TRUE.equals(sale.getIsGstRequired()) && hasNonZeroGst(sale);
            if (isTaxable) {
                outTaxable = outTaxable.add(sale.getTaxableAmount());
                outCgst = outCgst.add(sale.getCgstAmount());
                outSgst = outSgst.add(sale.getSgstAmount());
                outIgst = outIgst.add(sale.getIgstAmount());
                outUtgst = outUtgst.add(sale.getUtgstAmount());
            } else {
                // Nil-rated / exempt / non-GST outward supply. Amount goes into
                // Section 3.1(c) taxable_value only — no tax breakdown expected.
                nilRatedTaxable = nilRatedTaxable.add(sale.getTaxableAmount());
            }
        }

        summary.getOutwardTaxableSupplies().setTaxableValue(outTaxable);
        summary.getOutwardTaxableSupplies().setCgst(outCgst);
        summary.getOutwardTaxableSupplies().setSgst(outSgst);
        summary.getOutwardTaxableSupplies().setIgst(outIgst);
        summary.getOutwardTaxableSupplies().setUtgst(outUtgst);

        summary.getNilRatedExemptSupplies().setTaxableValue(nilRatedTaxable);

        // ── Section 3.1(d): Inward supplies liable to reverse charge. ────
        // Section 4: Input Tax Credit. Split into 4(A) available vs 4(D)
        // ineligible based on the purchase's is_itc_eligible flag. ─────────
        BigDecimal inwardRcTaxable = BigDecimal.ZERO;
        BigDecimal inwardRcCgst = BigDecimal.ZERO;
        BigDecimal inwardRcSgst = BigDecimal.ZERO;
        BigDecimal inwardRcIgst = BigDecimal.ZERO;
        BigDecimal inwardRcUtgst = BigDecimal.ZERO;

        BigDecimal itcCgst = BigDecimal.ZERO;
        BigDecimal itcSgst = BigDecimal.ZERO;
        BigDecimal itcIgst = BigDecimal.ZERO;
        BigDecimal itcUtgst = BigDecimal.ZERO;

        BigDecimal itcInelCgst = BigDecimal.ZERO;
        BigDecimal itcInelSgst = BigDecimal.ZERO;
        BigDecimal itcInelIgst = BigDecimal.ZERO;
        BigDecimal itcInelUtgst = BigDecimal.ZERO;

        for (PurchaseInvoice purchase : purchases) {
            BigDecimal pCgst = nullSafe(purchase.getCgstAmount());
            BigDecimal pSgst = nullSafe(purchase.getSgstAmount());
            BigDecimal pIgst = nullSafe(purchase.getIgstAmount());
            BigDecimal pUtgst = nullSafe(purchase.getUtgstAmount());

            if (purchase.isReverseCharge()) {
                // 3.1(d) — captures the inward supply itself.
                inwardRcTaxable = inwardRcTaxable.add(nullSafe(purchase.getTotalTaxableAmount()));
                inwardRcCgst = inwardRcCgst.add(pCgst);
                inwardRcSgst = inwardRcSgst.add(pSgst);
                inwardRcIgst = inwardRcIgst.add(pIgst);
                inwardRcUtgst = inwardRcUtgst.add(pUtgst);
                // The buyer pays tax under RCM and then claims it as ITC (when
                // eligible) — accounted below in the ITC block.
            }

            if (purchase.isItcEligible()) {
                itcCgst = itcCgst.add(pCgst);
                itcSgst = itcSgst.add(pSgst);
                itcIgst = itcIgst.add(pIgst);
                itcUtgst = itcUtgst.add(pUtgst);
            } else {
                itcInelCgst = itcInelCgst.add(pCgst);
                itcInelSgst = itcInelSgst.add(pSgst);
                itcInelIgst = itcInelIgst.add(pIgst);
                itcInelUtgst = itcInelUtgst.add(pUtgst);
            }
        }

        summary.getInwardReverseChargeSupplies().setTaxableValue(inwardRcTaxable);
        summary.getInwardReverseChargeSupplies().setCgst(inwardRcCgst);
        summary.getInwardReverseChargeSupplies().setSgst(inwardRcSgst);
        summary.getInwardReverseChargeSupplies().setIgst(inwardRcIgst);
        summary.getInwardReverseChargeSupplies().setUtgst(inwardRcUtgst);

        summary.getItcAvailable().setCgst(itcCgst);
        summary.getItcAvailable().setSgst(itcSgst);
        summary.getItcAvailable().setIgst(itcIgst);
        summary.getItcAvailable().setUtgst(itcUtgst);

        summary.getItcIneligible().setCgst(itcInelCgst);
        summary.getItcIneligible().setSgst(itcInelSgst);
        summary.getItcIneligible().setIgst(itcInelIgst);
        summary.getItcIneligible().setUtgst(itcInelUtgst);

        // Section 5: Net tax liability per component. NOTE — GST rules 88A/88B
        // require IGST credit to be exhausted first before CGST/SGST/UTGST can
        // offset. The proper cross-head offset is deferred; this preserves the
        // pre-existing per-component subtraction that shops have been reviewing
        // manually. A later pass can add rule-88A ordering.
        summary.getNetTaxLiability().setCgstPayable(outCgst.subtract(itcCgst).max(BigDecimal.ZERO));
        summary.getNetTaxLiability().setSgstPayable(outSgst.subtract(itcSgst).max(BigDecimal.ZERO));
        summary.getNetTaxLiability().setIgstPayable(outIgst.subtract(itcIgst).max(BigDecimal.ZERO));
        summary.getNetTaxLiability().setUtgstPayable(outUtgst.subtract(itcUtgst).max(BigDecimal.ZERO));

        return summary;
    }

    /**
     * Intra-state check for a sale. Prefers the sale's captured
     * placeOfSupply string (may be formatted "27-Maharashtra") when set;
     * falls back to comparing sale.customer against the shop via the
     * jurisdiction service. Empty placeOfSupply is treated as intra-state
     * (the conservative default matching {@link GstJurisdictionService}).
     */
    private boolean isIntraState(Shop shop, Sale sale) {
        String shopCode = jurisdictionService.resolveStateCode(shop).orElse(null);
        String pos = sale.getPlaceOfSupply();
        if (pos != null && pos.length() >= 2) {
            String posPrefix = pos.substring(0, 2);
            if (posPrefix.chars().allMatch(Character::isDigit)) {
                return jurisdictionService.isIntraState(shopCode, posPrefix);
            }
        }
        String customerCode = jurisdictionService.resolveStateCode(sale.getCustomer()).orElse(null);
        return jurisdictionService.isIntraState(shopCode, customerCode);
    }
}
