package com.desitech.vyaparsathi.gst.service;

import com.desitech.vyaparsathi.common.enums.SupplyType;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.gst.dto.Gstr3bExportDto;
import com.desitech.vyaparsathi.gst.dto.Gstr3bSummaryDto;
import com.desitech.vyaparsathi.purchases.entity.PurchaseInvoice;
import com.desitech.vyaparsathi.purchases.entity.PurchaseInvoiceItem;
import com.desitech.vyaparsathi.purchases.repository.PurchaseInvoiceRepository;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.enums.SaleStatus;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class Gstr3bBuilder {

    private final SaleRepository             saleRepo;
    private final PurchaseInvoiceRepository  purchaseRepo;
    private final ShopRepository             shopRepo;
    private final GstJurisdictionService     jurisdictionService;
    private final GstOffsetService           offsetService;

    public Gstr3bBuilder(SaleRepository saleRepo,
                         PurchaseInvoiceRepository purchaseRepo,
                         ShopRepository shopRepo,
                         GstJurisdictionService jurisdictionService,
                         GstOffsetService offsetService) {
        this.saleRepo           = saleRepo;
        this.purchaseRepo       = purchaseRepo;
        this.shopRepo           = shopRepo;
        this.jurisdictionService = jurisdictionService;
        this.offsetService      = offsetService;
    }

    @Transactional(readOnly = true)
    public Gstr3bSummaryDto buildSummary(int year, int month) {
        Long shopId = TenantUtils.getCurrentShopId();
        Shop shop = shopRepo.findById(shopId)
                .orElseThrow(() -> new IllegalArgumentException("Shop not found"));
        String shopCode = jurisdictionService.resolveStateCode(shop).orElse("99");

        LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime end   = start.plusMonths(1).minusNanos(1);
        LocalDate fromDate  = start.toLocalDate();
        LocalDate toDate    = end.toLocalDate();

        List<Sale>            sales     = saleRepo.findAllByShopIdAndDateBetween(shopId, start, end);
        List<PurchaseInvoice> purchases = purchaseRepo
                .findAllByShopIdAndPurchaseDateBetween(shopId, fromDate, toDate);

        Gstr3bSummaryDto summary = new Gstr3bSummaryDto();
        summary.setGstin(shop.getGstin() != null ? shop.getGstin() : "UNREGISTERED");
        summary.setMonthYear(String.format("%02d-%d", month, year));

        // ── Section 3.1: Outward supplies ────────────────────────────────────
        // Table 3.2 accumulator: POS code → entry for inter-state unregistered B2C
        Map<String, Gstr3bSummaryDto.InterStatePosEntry> posMap = new LinkedHashMap<>();

        for (Sale sale : sales) {
            if (!isReportable(sale)) continue;

            SupplyType supplyType = sale.getSupplyType();

            // 3.1(b) — zero-rated: exports and SEZ supplies
            if (supplyType != null && (supplyType.isExport() || supplyType.isSez())) {
                Gstr3bSummaryDto.OutwardSupplies zr = summary.getZeroRatedExportSupplies();
                zr.setTaxableValue(zr.getTaxableValue().add(nullSafe(sale.getTaxableAmount())));
                zr.setIgst(zr.getIgst().add(nullSafe(sale.getIgstAmount())));
                zr.setCgst(zr.getCgst().add(nullSafe(sale.getCgstAmount())));
                zr.setSgst(zr.getSgst().add(nullSafe(sale.getSgstAmount())));
                zr.setUtgst(zr.getUtgst().add(nullSafe(sale.getUtgstAmount())));
                continue;
            }

            boolean hasTax = Boolean.TRUE.equals(sale.getIsGstRequired()) && hasNonZeroGst(sale);

            if (hasTax) {
                // 3.1(a) — outward taxable (regular domestic supplies)
                Gstr3bSummaryDto.OutwardSupplies ot = summary.getOutwardTaxableSupplies();
                ot.setTaxableValue(ot.getTaxableValue().add(nullSafe(sale.getTaxableAmount())));
                ot.setCgst(ot.getCgst().add(nullSafe(sale.getCgstAmount())));
                ot.setSgst(ot.getSgst().add(nullSafe(sale.getSgstAmount())));
                ot.setIgst(ot.getIgst().add(nullSafe(sale.getIgstAmount())));
                ot.setUtgst(ot.getUtgst().add(nullSafe(sale.getUtgstAmount())));

                // Table 3.2 — inter-state B2C unregistered by POS
                boolean hasGstin = sale.getCustomer() != null
                        && sale.getCustomer().getGstin() != null
                        && !sale.getCustomer().getGstin().isBlank();
                if (!hasGstin && !isIntraState(shopCode, sale)) {
                    String pos = resolvePosCode(sale, shopCode);
                    Gstr3bSummaryDto.InterStatePosEntry entry =
                            posMap.computeIfAbsent(pos, p -> {
                                Gstr3bSummaryDto.InterStatePosEntry e = new Gstr3bSummaryDto.InterStatePosEntry();
                                e.setPos(p);
                                return e;
                            });
                    entry.setTaxableValue(entry.getTaxableValue().add(nullSafe(sale.getTaxableAmount())));
                    entry.setIgst(entry.getIgst().add(nullSafe(sale.getIgstAmount())));
                }
            } else {
                // 3.1(c) — nil-rated / exempt
                Gstr3bSummaryDto.OutwardSupplies nil = summary.getNilRatedExemptSupplies();
                nil.setTaxableValue(nil.getTaxableValue().add(nullSafe(sale.getTaxableAmount())));
            }
        }

        summary.setInterStateUnregistered(new ArrayList<>(posMap.values()));

        // ── Section 3.1(d) + Section 4: Purchases ────────────────────────────
        // Accumulate 3.1(d) RCM inward liability and Section 4 ITC sub-categories
        // simultaneously so we loop purchases only once.
        for (PurchaseInvoice purchase : purchases) {
            BigDecimal pCgst  = nullSafe(purchase.getCgstAmount());
            BigDecimal pSgst  = nullSafe(purchase.getSgstAmount());
            BigDecimal pIgst  = nullSafe(purchase.getIgstAmount());
            BigDecimal pUtgst = nullSafe(purchase.getUtgstAmount());
            BigDecimal pCess  = purchaseCess(purchase);

            if (purchase.isReverseCharge()) {
                // 3.1(d) — the RCM inward supply itself
                Gstr3bSummaryDto.OutwardSupplies rcm = summary.getInwardReverseChargeSupplies();
                rcm.setTaxableValue(rcm.getTaxableValue().add(nullSafe(purchase.getTotalTaxableAmount())));
                rcm.setCgst(rcm.getCgst().add(pCgst));
                rcm.setSgst(rcm.getSgst().add(pSgst));
                rcm.setIgst(rcm.getIgst().add(pIgst));
                rcm.setUtgst(rcm.getUtgst().add(pUtgst));
            }

            if (purchase.isItcEligible()) {
                if (purchase.isReverseCharge()) {
                    // 4(A)(3) — ISRC: RCM purchases where buyer pays and reclaims ITC
                    Gstr3bSummaryDto.ItcDetails rcmItc = summary.getItcRcm();
                    rcmItc.setCgst(rcmItc.getCgst().add(pCgst));
                    rcmItc.setSgst(rcmItc.getSgst().add(pSgst));
                    rcmItc.setIgst(rcmItc.getIgst().add(pIgst));
                    rcmItc.setUtgst(rcmItc.getUtgst().add(pUtgst));
                    rcmItc.setCess(rcmItc.getCess().add(pCess));
                } else {
                    // 4(A)(5) — OTH: standard eligible ITC (inputs, capital goods, services)
                    Gstr3bSummaryDto.ItcDetails oth = summary.getItcOther();
                    oth.setCgst(oth.getCgst().add(pCgst));
                    oth.setSgst(oth.getSgst().add(pSgst));
                    oth.setIgst(oth.getIgst().add(pIgst));
                    oth.setUtgst(oth.getUtgst().add(pUtgst));
                    oth.setCess(oth.getCess().add(pCess));
                }
            } else {
                // 4(D) — ineligible (Section 17(5) blocked / itcEligibility=INELIGIBLE)
                Gstr3bSummaryDto.ItcDetails inel = summary.getItcIneligible();
                inel.setCgst(inel.getCgst().add(pCgst));
                inel.setSgst(inel.getSgst().add(pSgst));
                inel.setIgst(inel.getIgst().add(pIgst));
                inel.setUtgst(inel.getUtgst().add(pUtgst));
                inel.setCess(inel.getCess().add(pCess));
            }
        }

        // 4(A) combined available = RCM + OTH
        Gstr3bSummaryDto.ItcDetails combined = summary.getItcAvailable();
        combined.setCgst(summary.getItcRcm().getCgst().add(summary.getItcOther().getCgst()));
        combined.setSgst(summary.getItcRcm().getSgst().add(summary.getItcOther().getSgst()));
        combined.setIgst(summary.getItcRcm().getIgst().add(summary.getItcOther().getIgst()));
        combined.setUtgst(summary.getItcRcm().getUtgst().add(summary.getItcOther().getUtgst()));
        combined.setCess(summary.getItcRcm().getCess().add(summary.getItcOther().getCess()));

        // 4(C) net = 4(A) − 4(B); 4(B) is zero for now, so net == available
        Gstr3bSummaryDto.ItcDetails net = summary.getItcNet();
        net.setCgst(combined.getCgst());
        net.setSgst(combined.getSgst());
        net.setIgst(combined.getIgst());
        net.setUtgst(combined.getUtgst());
        net.setCess(combined.getCess());

        // ── Section 6: Rule 88A/88B ITC offset ───────────────────────────────
        BigDecimal outIgst = summary.getOutwardTaxableSupplies().getIgst();
        BigDecimal outCgst = summary.getOutwardTaxableSupplies().getCgst();
        BigDecimal outSgst = summary.getOutwardTaxableSupplies().getSgst();
        BigDecimal outUtgst = summary.getOutwardTaxableSupplies().getUtgst();
        BigDecimal outCess = summary.getOutwardTaxableSupplies().getCess();
        BigDecimal itcIgst  = combined.getIgst();
        BigDecimal itcCgst  = combined.getCgst();
        BigDecimal itcSgst  = combined.getSgst();
        BigDecimal itcUtgst = combined.getUtgst();
        BigDecimal itcCess  = combined.getCess();

        GstOffsetService.ItcBalance   itcBalance = GstOffsetService.ItcBalance.of(
                itcIgst, itcCgst, itcSgst, itcUtgst, itcCess);
        GstOffsetService.TaxLiability liability  = GstOffsetService.TaxLiability.of(
                outIgst, outCgst, outSgst, outUtgst, outCess);
        GstOffsetService.OffsetResult offset = offsetService.applyOffset(itcBalance, liability);

        summary.getNetTaxLiability().setIgstPayable(offset.paidInCash().igst());
        summary.getNetTaxLiability().setCgstPayable(offset.paidInCash().cgst());
        summary.getNetTaxLiability().setSgstPayable(offset.paidInCash().sgst());
        summary.getNetTaxLiability().setUtgstPayable(offset.paidInCash().utgst());
        summary.getNetTaxLiability().setCessPayable(offset.paidInCash().cess());

        Gstr3bSummaryDto.ItcDetails paidItc = new Gstr3bSummaryDto.ItcDetails();
        paidItc.setIgst(offset.paidThroughItc().igst());
        paidItc.setCgst(offset.paidThroughItc().cgst());
        paidItc.setSgst(offset.paidThroughItc().sgst());
        paidItc.setUtgst(offset.paidThroughItc().utgst());
        paidItc.setCess(offset.paidThroughItc().cess());

        Gstr3bSummaryDto.ItcDetails paidCash = new Gstr3bSummaryDto.ItcDetails();
        paidCash.setIgst(offset.paidInCash().igst());
        paidCash.setCgst(offset.paidInCash().cgst());
        paidCash.setSgst(offset.paidInCash().sgst());
        paidCash.setUtgst(offset.paidInCash().utgst());
        paidCash.setCess(offset.paidInCash().cess());

        Gstr3bSummaryDto.ItcDetails closingBal = new Gstr3bSummaryDto.ItcDetails();
        closingBal.setIgst(offset.closingItcBalance().igst());
        closingBal.setCgst(offset.closingItcBalance().cgst());
        closingBal.setSgst(offset.closingItcBalance().sgst());
        closingBal.setUtgst(offset.closingItcBalance().utgst());
        closingBal.setCess(offset.closingItcBalance().cess());

        Gstr3bSummaryDto.ItcOffsetSummary offsetSummary = new Gstr3bSummaryDto.ItcOffsetSummary();
        offsetSummary.setPaidThroughItc(paidItc);
        offsetSummary.setPaidInCash(paidCash);
        offsetSummary.setClosingItcBalance(closingBal);
        summary.setItcOffset(offsetSummary);

        return summary;
    }

    @Transactional(readOnly = true)
    public Gstr3bExportDto buildExport(int year, int month) {
        Gstr3bSummaryDto s = buildSummary(year, month);

        Gstr3bExportDto dto = new Gstr3bExportDto();
        dto.setGstin(s.getGstin());
        dto.setRetPeriod(String.format("%02d%d", month, year));

        // ── sup_details ───────────────────────────────────────────────────────
        Gstr3bExportDto.SupDetails sup = dto.getSupDetails();
        copyOutwardToRow(s.getOutwardTaxableSupplies(),   sup.getOutwardTaxable());
        copyOutwardToRow(s.getZeroRatedExportSupplies(),  sup.getZeroRatedExports());
        copyOutwardToRow(s.getNilRatedExemptSupplies(),   sup.getNilExempt());
        copyOutwardToRow(s.getInwardReverseChargeSupplies(), sup.getInwardRcm());
        copyOutwardToRow(s.getNonGstSupplies(),           sup.getNonGst());

        // ── inter_sup (Table 3.2) ─────────────────────────────────────────────
        List<Gstr3bExportDto.PosEntry> unregList = new ArrayList<>();
        for (Gstr3bSummaryDto.InterStatePosEntry e : s.getInterStateUnregistered()) {
            Gstr3bExportDto.PosEntry pe = new Gstr3bExportDto.PosEntry();
            pe.setPos(e.getPos());
            pe.setTxval(e.getTaxableValue());
            pe.setIamt(e.getIgst());
            unregList.add(pe);
        }
        dto.getInterSup().setUnregDetails(unregList);

        // ── itc_elg ───────────────────────────────────────────────────────────
        Gstr3bExportDto.ItcElg itcElg = dto.getItcElg();
        itcElg.setItcAvl(List.of(
                itcEntry("IMPG", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                itcEntry("IMPS", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                itcEntry("ISRC", s.getItcRcm().getIgst(), s.getItcRcm().getCgst(), s.getItcRcm().getSgst(), s.getItcRcm().getCess()),
                itcEntry("ISD",  BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                itcEntry("OTH",  s.getItcOther().getIgst(), s.getItcOther().getCgst(), s.getItcOther().getSgst(), s.getItcOther().getCess())
        ));
        itcElg.setItcRev(List.of(
                itcEntry("RUL", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                itcEntry("OTH", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        ));
        Gstr3bExportDto.ItcAmounts netAmts = itcElg.getItcNet();
        netAmts.setIamt(s.getItcNet().getIgst());
        netAmts.setCamt(s.getItcNet().getCgst());
        netAmts.setSamt(s.getItcNet().getSgst());
        netAmts.setCsamt(s.getItcNet().getCess());

        itcElg.setItcInelg(List.of(
                itcEntry("RUL", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                itcEntry("OTH", s.getItcIneligible().getIgst(), s.getItcIneligible().getCgst(),
                        s.getItcIneligible().getSgst(), s.getItcIneligible().getCess())
        ));

        // ── inward_sup (Section 5 — zeros for now) ───────────────────────────
        dto.getInwardSup().setIsupDetails(List.of(
                new Gstr3bExportDto.InwardEntry("GST",    BigDecimal.ZERO, BigDecimal.ZERO),
                new Gstr3bExportDto.InwardEntry("NONGST", BigDecimal.ZERO, BigDecimal.ZERO)
        ));

        return dto;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void copyOutwardToRow(Gstr3bSummaryDto.OutwardSupplies src, Gstr3bExportDto.TaxRow dst) {
        dst.setTxval(src.getTaxableValue());
        dst.setIamt(src.getIgst());
        dst.setCamt(src.getCgst());
        dst.setSamt(src.getSgst());
        dst.setCsamt(src.getCess());
    }

    private static Gstr3bExportDto.ItcEntry itcEntry(String ty,
            BigDecimal iamt, BigDecimal camt, BigDecimal samt, BigDecimal csamt) {
        return new Gstr3bExportDto.ItcEntry(ty, iamt, camt, samt, csamt);
    }

    private String resolvePosCode(Sale sale, String shopCode) {
        String pos = sale.getPlaceOfSupply();
        if (pos != null && pos.length() >= 2) {
            String prefix = pos.substring(0, 2);
            if (prefix.chars().allMatch(Character::isDigit)) return prefix;
        }
        return shopCode;
    }

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

    private boolean isReportable(Sale sale) {
        SaleStatus status = sale.getStatus();
        if (status == SaleStatus.DRAFT
                || status == SaleStatus.CANCELLED
                || status == SaleStatus.HELD) return false;
        return !sale.isProforma();
    }

    private static boolean hasNonZeroGst(Sale sale) {
        return sale.getSaleItems().stream()
                .anyMatch(i -> i.getGstType() != null && i.getGstType().getRate().signum() > 0);
    }

    private static BigDecimal purchaseCess(PurchaseInvoice purchase) {
        if (purchase.getItems() == null || purchase.getItems().isEmpty()) return BigDecimal.ZERO;
        return purchase.getItems().stream()
                .map(PurchaseInvoiceItem::getCessAmt)
                .filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
