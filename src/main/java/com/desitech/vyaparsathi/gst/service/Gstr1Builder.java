package com.desitech.vyaparsathi.gst.service;

import com.desitech.vyaparsathi.accounting.entity.CreditNote;
import com.desitech.vyaparsathi.accounting.entity.CreditNoteItem;
import com.desitech.vyaparsathi.accounting.repository.CreditNoteRepository;
import com.desitech.vyaparsathi.common.enums.SupplyType;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.gst.dto.Gstr1ExportDto;
import com.desitech.vyaparsathi.gst.dto.HsnPreviewResponseDto;
import com.desitech.vyaparsathi.gst.dto.HsnPreviewRowDto;
import com.desitech.vyaparsathi.gst.enums.NoteType;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import com.desitech.vyaparsathi.sales.enums.SaleStatus;
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

/**
 * Builds the GSTR-1 filing payload in the GSTN V3.2 JSON schema.
 *
 * <p>Extracted from {@link GstTaxService} so that class stays focused on
 * GSTR-3B and ITC-offset work. Every fix from the Phase-3 audit is applied here:
 *
 * <ul>
 *   <li>B2B/B2CL/CDNR grouped (not flat) — by {@code ctin}/{@code pos}/{@code ctin}
 *   <li>B2CS aggregated per {@code (pos, rate, interState)} over the whole period
 *   <li>EXP (Table 6A) routed for export + SEZ supply types
 *   <li>CDNUR (Table 10) routed for unregistered-recipient credit notes
 *   <li>HSN key includes {@code rate} — same HSN at different rates stays separate
 *   <li>Cess ({@code csamt}) wired throughout TaxItemEntry and HSN rows
 *   <li>B2CL threshold uses {@code getReportingTotalAmount()} (immutable snapshot)
 *   <li>POS emits the 2-digit state code, not the full state name
 *   <li>{@code HELD} sales excluded alongside DRAFT/CANCELLED/PROFORMA
 *   <li>Credit-note rate uses {@code CreditNoteItem.gstType.rate} (V132 field)
 *   <li>HSN UQC uses {@code SaleItem.getGstnUqc()} (canonical, "NA" for services)
 * </ul>
 */
@Service
public class Gstr1Builder {

    private static final DateTimeFormatter GST_DATE  = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final BigDecimal        B2CL_FLOOR = new BigDecimal("250000");

    private final SaleRepository          saleRepo;
    private final CreditNoteRepository    creditRepo;
    private final ShopRepository          shopRepo;
    private final GstJurisdictionService  jurisdictionService;

    public Gstr1Builder(SaleRepository saleRepo,
                        CreditNoteRepository creditRepo,
                        ShopRepository shopRepo,
                        GstJurisdictionService jurisdictionService) {
        this.saleRepo            = saleRepo;
        this.creditRepo          = creditRepo;
        this.shopRepo            = shopRepo;
        this.jurisdictionService = jurisdictionService;
    }

    // ── Internal aggregation keys ───────────────────────────────────────────────

    /** Unique key for one row in GSTN HSN Table 12: HSN + UQC + rate. */
    private static final class HsnKey {
        final String hsn;
        final String uqc;
        final double rate;

        HsnKey(String hsn, String uqc, double rate) {
            this.hsn  = hsn  == null ? ""    : hsn;
            this.uqc  = uqc  == null ? "OTH" : uqc;
            this.rate = rate;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof HsnKey)) return false;
            HsnKey k = (HsnKey) o;
            return Double.compare(k.rate, rate) == 0 && hsn.equals(k.hsn) && uqc.equals(k.uqc);
        }

        @Override public int hashCode() { return Objects.hash(hsn, uqc, rate); }
    }

    /** Running totals for one HsnKey across the filing period. */
    private static final class HsnAgg {
        String     desc  = "";
        BigDecimal qty   = BigDecimal.ZERO;
        BigDecimal val   = BigDecimal.ZERO;
        BigDecimal txval = BigDecimal.ZERO;
        BigDecimal iamt  = BigDecimal.ZERO;
        BigDecimal camt  = BigDecimal.ZERO;
        BigDecimal samt  = BigDecimal.ZERO;
        BigDecimal csamt = BigDecimal.ZERO;
    }

    /** Unique key for a B2CS summary row: POS × rate × supply direction. */
    private static final class B2csKey {
        final String  pos;
        final double  rate;
        final boolean interState;

        B2csKey(String pos, double rate, boolean interState) {
            this.pos        = pos == null ? "" : pos;
            this.rate       = rate;
            this.interState = interState;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof B2csKey)) return false;
            B2csKey k = (B2csKey) o;
            return Double.compare(k.rate, rate) == 0 && interState == k.interState && pos.equals(k.pos);
        }

        @Override public int hashCode() { return Objects.hash(pos, rate, interState); }
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Gstr1ExportDto build(int year, int month) {
        Long shopId = TenantUtils.getCurrentShopId();
        Shop shop   = shopRepo.findById(shopId)
                .orElseThrow(() -> new IllegalArgumentException("Shop not found: " + shopId));

        LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime end   = start.plusMonths(1).minusNanos(1);
        LocalDate     from  = start.toLocalDate();
        LocalDate     to    = end.toLocalDate();

        String shopCode = jurisdictionService.resolveStateCode(shop).orElse("99");

        List<Sale>       sales = saleRepo.findAllByShopIdAndDateBetween(shopId, start, end);
        List<CreditNote> notes = creditRepo.findAllByShopIdAndCreditNoteDateBetween(shopId, from, to);

        // Accumulation maps — keyed for correct GSTN grouping
        Map<String, Gstr1ExportDto.B2bPartyGroup>  b2bMap  = new LinkedHashMap<>();
        Map<String, Gstr1ExportDto.B2clPosGroup>   b2clMap = new LinkedHashMap<>();
        Map<B2csKey, Gstr1ExportDto.B2cSummary>    b2csMap = new LinkedHashMap<>();
        Map<String, Gstr1ExportDto.CdnrPartyGroup> cdnrMap = new LinkedHashMap<>();
        List<Gstr1ExportDto.CdnurEntry>            cdnurList = new ArrayList<>();
        Map<String, Gstr1ExportDto.ExpTypeGroup>   expMap  = new LinkedHashMap<>();
        Map<HsnKey, HsnAgg>                        hsnMap  = new LinkedHashMap<>();

        BigDecimal grossTurnover = BigDecimal.ZERO;

        // ── Process outward sales ───────────────────────────────────────────────
        for (Sale sale : sales) {
            if (!isReportable(sale)) continue;

            grossTurnover = grossTurnover.add(sale.getReportingTotalAmount());

            String pos      = resolvePosCode(sale, shop, shopCode);
            boolean intra   = isIntraState(shopCode, sale);
            SupplyType st   = sale.getSupplyType();

            // Accumulate HSN regardless of which output table the sale routes to
            accumulateHsn(hsnMap, sale.getSaleItems());

            // ── Route to the correct GSTN table ────────────────────────────────
            if (st != null && (st.isExport() || st.isSez())) {
                String expTyp = (st == SupplyType.EXPORT_WITH_PAYMENT
                                 || st == SupplyType.SEZ_WITH_PAYMENT) ? "WPAY" : "WOPAY";
                Gstr1ExportDto.ExpTypeGroup grp = expMap.computeIfAbsent(expTyp, k -> {
                    Gstr1ExportDto.ExpTypeGroup g = new Gstr1ExportDto.ExpTypeGroup();
                    g.setExpTyp(k);
                    return g;
                });
                Gstr1ExportDto.ExpInv inv = new Gstr1ExportDto.ExpInv();
                inv.setInum(sale.getInvoiceNo());
                inv.setIdt(sale.getDate().toLocalDate().format(GST_DATE));
                inv.setVal(sale.getReportingTotalAmount());
                inv.setItms(buildTaxItemEntries(sale.getSaleItems()));
                grp.getInv().add(inv);

            } else if (isB2B(sale)) {
                String ctin = sale.getCustomer().getGstin();
                Gstr1ExportDto.B2bPartyGroup grp = b2bMap.computeIfAbsent(ctin, k -> {
                    Gstr1ExportDto.B2bPartyGroup g = new Gstr1ExportDto.B2bPartyGroup();
                    g.setCtin(k);
                    if (sale.getCustomer().getName() != null)
                        g.setCounterpartyName(sale.getCustomer().getName());
                    return g;
                });
                Gstr1ExportDto.B2bInv inv = new Gstr1ExportDto.B2bInv();
                inv.setInum(sale.getInvoiceNo());
                inv.setIdt(sale.getDate().toLocalDate().format(GST_DATE));
                inv.setVal(sale.getReportingTotalAmount());
                inv.setPos(pos);
                inv.setRchrg(Boolean.TRUE.equals(sale.getReverseCharge()) ? "Y" : "N");
                inv.setItms(buildTaxItemEntries(sale.getSaleItems()));
                grp.getInv().add(inv);

            } else {
                // B2C — large (B2CL) or small (B2CS)
                // Use getReportingTotalAmount() so partially-returned invoices are
                // classified by their original total, not the post-return mutable amount.
                boolean isLarge = !intra && sale.getReportingTotalAmount().compareTo(B2CL_FLOOR) > 0;

                if (isLarge) {
                    Gstr1ExportDto.B2clPosGroup grp = b2clMap.computeIfAbsent(pos, k -> {
                        Gstr1ExportDto.B2clPosGroup g = new Gstr1ExportDto.B2clPosGroup();
                        g.setPos(k);
                        return g;
                    });
                    Gstr1ExportDto.B2clInv inv = new Gstr1ExportDto.B2clInv();
                    inv.setInum(sale.getInvoiceNo());
                    inv.setIdt(sale.getDate().toLocalDate().format(GST_DATE));
                    inv.setVal(sale.getReportingTotalAmount());
                    inv.setItms(buildTaxItemEntries(sale.getSaleItems()));
                    grp.getInv().add(inv);

                } else {
                    // B2CS — aggregate per (pos, rate, interState) across the whole period
                    for (SaleItem line : sale.getSaleItems()) {
                        double rate = line.getGstType() != null
                                      ? line.getGstType().getRate().doubleValue() : 0.0;
                        B2csKey key = new B2csKey(pos, rate, !intra);
                        Gstr1ExportDto.B2cSummary row = b2csMap.computeIfAbsent(key, k -> {
                            Gstr1ExportDto.B2cSummary r = new Gstr1ExportDto.B2cSummary();
                            r.setPos(k.pos);
                            r.setRt(k.rate);
                            r.setSplyTy(k.interState ? "INTER" : "INTRA");
                            return r;
                        });
                        row.setTxval(row.getTxval().add(orZero(line.getTaxableValue())));
                        row.setIamt(row.getIamt().add(orZero(line.getIgstAmt())));
                        row.setCamt(row.getCamt().add(orZero(line.getCgstAmt())));
                        row.setSamt(row.getSamt().add(orZero(line.getSgstAmt())));
                        row.setCsamt(row.getCsamt().add(orZero(line.getCessAmt())));
                    }
                }
            }
        }

        // ── Process credit notes ────────────────────────────────────────────────
        for (CreditNote note : notes) {
            if (note.getCustomer() == null) continue;

            NoteType noteType = note.getNoteType() != null
                                ? note.getNoteType()
                                : NoteType.fromCustomerGstin(note.getCustomer().getGstin());

            String refInvNum  = note.getReferenceInvoiceNumber() != null
                                ? note.getReferenceInvoiceNumber()
                                : (note.getSale() != null ? note.getSale().getInvoiceNo() : null);
            String refInvDate = (note.getSale() != null && note.getSale().getDate() != null)
                                ? note.getSale().getDate().toLocalDate().format(GST_DATE)
                                : null;
            String ntDt  = note.getCreditNoteDate().format(GST_DATE);
            String ntNum = note.getCreditNoteNo();

            if (noteType == NoteType.CDNR) {
                String ctin = note.getCustomer().getGstin();
                if (ctin == null || ctin.isBlank()) continue;

                Gstr1ExportDto.CdnrPartyGroup grp = cdnrMap.computeIfAbsent(ctin, k -> {
                    Gstr1ExportDto.CdnrPartyGroup g = new Gstr1ExportDto.CdnrPartyGroup();
                    g.setCtin(k);
                    return g;
                });
                Gstr1ExportDto.CdnrNoteEntry entry = new Gstr1ExportDto.CdnrNoteEntry();
                entry.setNtNum(ntNum);
                entry.setNtDt(ntDt);
                entry.setVal(note.getTotalAmount());
                entry.setInum(refInvNum);
                entry.setIdt(refInvDate);
                entry.setItms(buildTaxItemEntriesFromNote(note.getItems()));
                grp.getNt().add(entry);

            } else {
                // CDNUR — unregistered recipient
                Gstr1ExportDto.CdnurEntry entry = new Gstr1ExportDto.CdnurEntry();
                entry.setTyp("B2CL");
                entry.setNtNum(ntNum);
                entry.setNtDt(ntDt);
                entry.setVal(note.getTotalAmount());
                entry.setItms(buildTaxItemEntriesFromNote(note.getItems()));
                cdnurList.add(entry);
            }
        }

        // ── Assemble the output DTO ─────────────────────────────────────────────
        Gstr1ExportDto out = new Gstr1ExportDto();
        out.setGstin(shop.getGstin() != null ? shop.getGstin() : "UNREGISTERED");
        out.setFp(String.format("%02d%d", month, year));
        out.setGrossTurnover(grossTurnover);

        out.setB2b(new ArrayList<>(b2bMap.values()));
        out.setB2cl(new ArrayList<>(b2clMap.values()));
        out.setB2cs(new ArrayList<>(b2csMap.values()));
        out.setCdnr(new ArrayList<>(cdnrMap.values()));
        out.setCdnur(cdnurList);
        out.setExp(new ArrayList<>(expMap.values()));
        out.setHsn(buildHsnSection(hsnMap));

        return out;
    }

    /** Returns HSN preview data for the on-screen pre-download review widget. */
    @Transactional(readOnly = true)
    public HsnPreviewResponseDto buildHsnPreview(int year, int month) {
        Long shopId = TenantUtils.getCurrentShopId();
        shopRepo.findById(shopId)
                .orElseThrow(() -> new IllegalArgumentException("Shop not found: " + shopId));

        LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime end   = start.plusMonths(1).minusNanos(1);

        List<Sale> sales = saleRepo.findAllByShopIdAndDateBetween(shopId, start, end);

        Map<HsnKey, HsnAgg> hsnMap = new LinkedHashMap<>();
        for (Sale sale : sales) {
            if (!isReportable(sale)) continue;
            accumulateHsn(hsnMap, sale.getSaleItems());
        }

        BigDecimal totalTaxable = BigDecimal.ZERO;
        BigDecimal cgst         = BigDecimal.ZERO;
        BigDecimal sgst         = BigDecimal.ZERO;
        BigDecimal igst         = BigDecimal.ZERO;
        BigDecimal cess         = BigDecimal.ZERO;

        List<HsnPreviewRowDto> rows = new ArrayList<>();
        int rowNum = 1;
        for (Map.Entry<HsnKey, HsnAgg> e : hsnMap.entrySet()) {
            HsnKey key = e.getKey();
            HsnAgg agg = e.getValue();
            rows.add(new HsnPreviewRowDto(
                    rowNum++,
                    key.hsn,
                    agg.desc,
                    key.uqc,
                    key.rate,
                    agg.qty.setScale(2, RoundingMode.HALF_UP),
                    agg.val.setScale(2, RoundingMode.HALF_UP),
                    agg.txval.setScale(2, RoundingMode.HALF_UP),
                    agg.iamt.setScale(2, RoundingMode.HALF_UP),
                    agg.camt.setScale(2, RoundingMode.HALF_UP),
                    agg.samt.setScale(2, RoundingMode.HALF_UP),
                    agg.csamt.setScale(2, RoundingMode.HALF_UP)
            ));
            totalTaxable = totalTaxable.add(agg.txval);
            igst         = igst.add(agg.iamt);
            cgst         = cgst.add(agg.camt);
            sgst         = sgst.add(agg.samt);
            cess         = cess.add(agg.csamt);
        }

        HsnPreviewResponseDto response = new HsnPreviewResponseDto();
        response.setRows(rows);
        response.setTotalTaxable(totalTaxable.setScale(2, RoundingMode.HALF_UP));
        response.setCgst(cgst.setScale(2, RoundingMode.HALF_UP));
        response.setSgst(sgst.setScale(2, RoundingMode.HALF_UP));
        response.setIgst(igst.setScale(2, RoundingMode.HALF_UP));
        response.setCess(cess.setScale(2, RoundingMode.HALF_UP));
        return response;
    }

    // ── Tax-item builders ───────────────────────────────────────────────────────

    /**
     * Groups sale line items by GST rate into {@link Gstr1ExportDto.TaxItemEntry} with
     * nested {@link Gstr1ExportDto.ItemDetail} — the mandatory GSTN V3.2 nesting shape.
     * Cess ({@code csamt}) is summed alongside the component taxes.
     */
    private List<Gstr1ExportDto.TaxItemEntry> buildTaxItemEntries(List<SaleItem> lines) {
        Map<Double, Gstr1ExportDto.ItemDetail> byRate = new LinkedHashMap<>();
        for (SaleItem line : lines) {
            double rate = line.getGstType() != null ? line.getGstType().getRate().doubleValue() : 0.0;
            Gstr1ExportDto.ItemDetail d = byRate.computeIfAbsent(rate, r -> {
                Gstr1ExportDto.ItemDetail det = new Gstr1ExportDto.ItemDetail();
                det.setRt(r);
                return det;
            });
            d.setTxval(d.getTxval().add(orZero(line.getTaxableValue())));
            d.setCamt(d.getCamt().add(orZero(line.getCgstAmt())));
            d.setSamt(d.getSamt().add(orZero(line.getSgstAmt())));
            d.setIamt(d.getIamt().add(orZero(line.getIgstAmt())));
            d.setCsamt(d.getCsamt().add(orZero(line.getCessAmt())));
        }
        List<Gstr1ExportDto.TaxItemEntry> result = new ArrayList<>();
        int idx = 1;
        for (Map.Entry<Double, Gstr1ExportDto.ItemDetail> e : byRate.entrySet()) {
            Gstr1ExportDto.TaxItemEntry entry = new Gstr1ExportDto.TaxItemEntry();
            entry.setNum(idx++);
            entry.setItmDet(e.getValue());
            result.add(entry);
        }
        return result;
    }

    /**
     * Same as {@link #buildTaxItemEntries} but for credit note lines.
     * Uses {@code CreditNoteItem.gstType.rate} (added in V132) instead of the
     * old broken ratio-from-amounts derivation.
     */
    private List<Gstr1ExportDto.TaxItemEntry> buildTaxItemEntriesFromNote(List<CreditNoteItem> lines) {
        Map<Double, Gstr1ExportDto.ItemDetail> byRate = new LinkedHashMap<>();
        for (CreditNoteItem line : lines) {
            double rate = line.getGstType() != null ? line.getGstType().getRate().doubleValue() : 0.0;
            Gstr1ExportDto.ItemDetail d = byRate.computeIfAbsent(rate, r -> {
                Gstr1ExportDto.ItemDetail det = new Gstr1ExportDto.ItemDetail();
                det.setRt(r);
                return det;
            });
            BigDecimal txval = line.getTaxableValue() == null ? BigDecimal.ZERO : line.getTaxableValue();
            d.setTxval(d.getTxval().add(txval));
            d.setCamt(d.getCamt().add(orZero(line.getCgstAmt())));
            d.setSamt(d.getSamt().add(orZero(line.getSgstAmt())));
            d.setIamt(d.getIamt().add(orZero(line.getIgstAmt())));
            d.setCsamt(d.getCsamt().add(orZero(line.getCessAmt())));
        }
        List<Gstr1ExportDto.TaxItemEntry> result = new ArrayList<>();
        int idx = 1;
        for (Map.Entry<Double, Gstr1ExportDto.ItemDetail> e : byRate.entrySet()) {
            Gstr1ExportDto.TaxItemEntry entry = new Gstr1ExportDto.TaxItemEntry();
            entry.setNum(idx++);
            entry.setItmDet(e.getValue());
            result.add(entry);
        }
        return result;
    }

    // ── HSN aggregation ─────────────────────────────────────────────────────────

    private void accumulateHsn(Map<HsnKey, HsnAgg> bucket, List<SaleItem> lines) {
        for (SaleItem line : lines) {
            String hsn  = resolveHsn(line);
            String uqc  = line.getGstnUqc();   // canonical: "NA" for services
            double rate = line.getGstType() != null ? line.getGstType().getRate().doubleValue() : 0.0;
            HsnKey key  = new HsnKey(hsn, uqc, rate);
            HsnAgg agg  = bucket.computeIfAbsent(key, k -> new HsnAgg());

            if (agg.desc.isEmpty()) {
                if (line.getItemVariant() != null
                        && line.getItemVariant().getItem() != null
                        && line.getItemVariant().getItem().getName() != null) {
                    agg.desc = line.getItemVariant().getItem().getName();
                } else if (line.getCustomItemName() != null) {
                    agg.desc = line.getCustomItemName();
                }
            }

            BigDecimal taxable = orZero(line.getTaxableValue());
            BigDecimal taxes   = orZero(line.getCgstAmt())
                                     .add(orZero(line.getSgstAmt()))
                                     .add(orZero(line.getIgstAmt()))
                                     .add(orZero(line.getUtgstAmt()));
            agg.qty   = agg.qty.add(orZero(line.getQty()));
            agg.txval = agg.txval.add(taxable);
            agg.val   = agg.val.add(taxable).add(taxes);
            agg.iamt  = agg.iamt.add(orZero(line.getIgstAmt()));
            agg.camt  = agg.camt.add(orZero(line.getCgstAmt()));
            agg.samt  = agg.samt.add(orZero(line.getSgstAmt()));
            agg.csamt = agg.csamt.add(orZero(line.getCessAmt()));
        }
    }

    private Gstr1ExportDto.HsnSection buildHsnSection(Map<HsnKey, HsnAgg> hsnMap) {
        Gstr1ExportDto.HsnSection section = new Gstr1ExportDto.HsnSection();
        int idx = 1;
        for (Map.Entry<HsnKey, HsnAgg> e : hsnMap.entrySet()) {
            HsnKey key = e.getKey();
            HsnAgg agg = e.getValue();
            Gstr1ExportDto.HsnEntry row = new Gstr1ExportDto.HsnEntry();
            row.setNum(idx++);
            row.setHsnSc(key.hsn);
            row.setDesc(agg.desc);
            row.setUqc(key.uqc);
            row.setQty(agg.qty.setScale(2, RoundingMode.HALF_UP));
            row.setVal(agg.val.setScale(2, RoundingMode.HALF_UP));
            row.setTxval(agg.txval.setScale(2, RoundingMode.HALF_UP));
            row.setIamt(agg.iamt.setScale(2, RoundingMode.HALF_UP));
            row.setCamt(agg.camt.setScale(2, RoundingMode.HALF_UP));
            row.setSamt(agg.samt.setScale(2, RoundingMode.HALF_UP));
            row.setCsamt(agg.csamt.setScale(2, RoundingMode.HALF_UP));
            section.getData().add(row);
        }
        return section;
    }

    // ── Routing helpers ──────────────────────────────────────────────────────────

    /**
     * Returns the 2-digit numeric state/UT code for use in GSTN JSON.
     * Prefers the sale's captured {@code placeOfSupply} when it starts with digits;
     * falls back to the shop's resolved state code.
     */
    private String resolvePosCode(Sale sale, Shop shop, String shopCode) {
        String pos = sale.getPlaceOfSupply();
        if (pos != null && pos.length() >= 2) {
            String prefix = pos.substring(0, 2);
            if (prefix.chars().allMatch(Character::isDigit)) {
                return prefix;
            }
        }
        return shopCode;
    }

    private boolean isB2B(Sale sale) {
        return sale.getCustomer() != null
                && sale.getCustomer().getGstin() != null
                && !sale.getCustomer().getGstin().isBlank();
    }

    /**
     * Returns {@code true} when the sale is an intra-state supply. Mirrors the
     * same two-pass logic in {@link GstTaxService}: numeric POS prefix first,
     * then jurisdiction service code comparison.
     */
    private boolean isIntraState(String shopCode, Sale sale) {
        String pos = sale.getPlaceOfSupply();
        if (pos != null && pos.length() >= 2) {
            String prefix = pos.substring(0, 2);
            if (prefix.chars().allMatch(Character::isDigit)) {
                return jurisdictionService.isIntraState(shopCode, prefix);
            }
        }
        String customerCode = jurisdictionService.resolveStateCode(sale.getCustomer()).orElse(null);
        return jurisdictionService.isIntraState(shopCode, customerCode);
    }

    /**
     * A sale is reportable when it is not a draft, proforma, cancelled, or held.
     * HELD invoices are payment-related holds, not filed on a return.
     */
    private boolean isReportable(Sale sale) {
        SaleStatus status = sale.getStatus();
        if (status == SaleStatus.DRAFT
                || status == SaleStatus.CANCELLED
                || status == SaleStatus.HELD) return false;
        return !sale.isProforma();
    }

    private String resolveHsn(SaleItem line) {
        if (line.getCustomHsnSac() != null && !line.getCustomHsnSac().isBlank())
            return line.getCustomHsnSac();
        if (line.getItemVariant() != null && line.getItemVariant().getHsn() != null)
            return line.getItemVariant().getHsn();
        return "";
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
