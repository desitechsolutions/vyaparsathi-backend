package com.desitech.vyaparsathi.gst.service;

import com.desitech.vyaparsathi.gst.dto.Gstr3bSummaryDto;
import com.desitech.vyaparsathi.gst.dto.Gstr9SummaryDto;
import com.desitech.vyaparsathi.purchases.entity.PurchaseInvoice;
import com.desitech.vyaparsathi.purchases.repository.PurchaseInvoiceRepository;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.enums.SaleStatus;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class Gstr9Builder {

    private final SaleRepository saleRepo;
    private final PurchaseInvoiceRepository purchaseRepo;
    private final ShopRepository shopRepo;
    private final Gstr3bBuilder gstr3bBuilder;

    public Gstr9Builder(SaleRepository saleRepo,
                        PurchaseInvoiceRepository purchaseRepo,
                        ShopRepository shopRepo,
                        Gstr3bBuilder gstr3bBuilder) {
        this.saleRepo     = saleRepo;
        this.purchaseRepo = purchaseRepo;
        this.shopRepo     = shopRepo;
        this.gstr3bBuilder = gstr3bBuilder;
    }

    @Transactional(readOnly = true)
    public Gstr9SummaryDto build(int fiscalYear) {
        Long shopId = TenantUtils.getCurrentShopId();
        Shop shop   = shopRepo.findById(shopId)
                .orElseThrow(() -> new IllegalStateException("Shop not found: " + shopId));

        // FY April 1, fiscalYear → March 31, fiscalYear+1
        LocalDate fyStart = LocalDate.of(fiscalYear, 4, 1);
        LocalDate fyEnd   = LocalDate.of(fiscalYear + 1, 3, 31);
        LocalDateTime dtStart = fyStart.atStartOfDay();
        LocalDateTime dtEnd   = fyEnd.atTime(LocalTime.MAX);

        // ── Table 4: Outward supplies from locked sale fields ─────────────────
        List<Sale> sales = saleRepo.findAllByShopIdAndDateBetween(shopId, dtStart, dtEnd);

        BigDecimal t4Taxable = BigDecimal.ZERO;
        BigDecimal t4ZeroRated = BigDecimal.ZERO;
        BigDecimal t4Exempt = BigDecimal.ZERO;
        BigDecimal outIgst = BigDecimal.ZERO;
        BigDecimal outCgst = BigDecimal.ZERO;
        BigDecimal outSgst = BigDecimal.ZERO;

        for (Sale s : sales) {
            if (s.getStatus() == SaleStatus.CANCELLED) continue;
            BigDecimal txval = safe(s.getOriginalTaxableValue());
            BigDecimal igst  = safe(s.getOriginalIgst());
            BigDecimal cgst  = safe(s.getOriginalCgst());
            BigDecimal sgst  = safe(s.getOriginalSgst());
            BigDecimal totalTax = igst.add(cgst).add(sgst);

            if (totalTax.compareTo(BigDecimal.ZERO) == 0 && txval.compareTo(BigDecimal.ZERO) > 0) {
                t4Exempt = t4Exempt.add(txval);
            } else {
                t4Taxable = t4Taxable.add(txval);
            }
            outIgst = outIgst.add(igst);
            outCgst = outCgst.add(cgst);
            outSgst = outSgst.add(sgst);
        }

        // ── Table 6: ITC availed from ITC-eligible purchases ──────────────────
        List<PurchaseInvoice> itcPurchases =
                purchaseRepo.findItcEligibleByShopAndPeriod(shopId, fyStart, fyEnd);

        BigDecimal t6Igst = BigDecimal.ZERO;
        BigDecimal t6Cgst = BigDecimal.ZERO;
        BigDecimal t6Sgst = BigDecimal.ZERO;

        for (PurchaseInvoice pi : itcPurchases) {
            t6Igst = t6Igst.add(safe(pi.getTotalIgst()));
            t6Cgst = t6Cgst.add(safe(pi.getTotalCgst()));
            t6Sgst = t6Sgst.add(safe(pi.getTotalSgst()));
        }

        // ── Table 9: Tax payable vs paid — aggregate 12 FY months via 3B ─────
        BigDecimal t9PayableIgst = BigDecimal.ZERO;
        BigDecimal t9PayableCgst = BigDecimal.ZERO;
        BigDecimal t9PayableSgst = BigDecimal.ZERO;
        BigDecimal t9PaidIgst = BigDecimal.ZERO;
        BigDecimal t9PaidCgst = BigDecimal.ZERO;
        BigDecimal t9PaidSgst = BigDecimal.ZERO;

        for (int i = 0; i < 12; i++) {
            LocalDate monthDate = fyStart.plusMonths(i);
            int y = monthDate.getYear();
            int m = monthDate.getMonthValue();
            try {
                Gstr3bSummaryDto s3b = gstr3bBuilder.buildSummary(y, m);
                if (s3b != null) {
                    // Payable = outward supplies tax
                    t9PayableIgst = t9PayableIgst.add(safe(s3b.getOutwardTaxableSupplies().getIgst()));
                    t9PayableCgst = t9PayableCgst.add(safe(s3b.getOutwardTaxableSupplies().getCgst()));
                    t9PayableSgst = t9PayableSgst.add(safe(s3b.getOutwardTaxableSupplies().getSgst()));
                    // Paid = net tax liability after ITC
                    t9PaidIgst = t9PaidIgst.add(safe(s3b.getNetTaxLiability().getIgstPayable()));
                    t9PaidCgst = t9PaidCgst.add(safe(s3b.getNetTaxLiability().getCgstPayable()));
                    t9PaidSgst = t9PaidSgst.add(safe(s3b.getNetTaxLiability().getSgstPayable()));
                }
            } catch (Exception ignored) {
                // Skip months with no data rather than failing the annual report
            }
        }

        // ── Assemble DTO ──────────────────────────────────────────────────────
        Gstr9SummaryDto dto = new Gstr9SummaryDto();
        dto.setGstin(shop.getGstin());
        dto.setTradeName(shop.getTradeName());
        dto.setFiscalYear(fiscalYear);
        dto.setFyLabel(fiscalYear + "-" + String.valueOf(fiscalYear + 1).substring(2));

        dto.setTable4OutwardTaxable(t4Taxable);
        dto.setTable4ZeroRated(t4ZeroRated);
        dto.setTable4Exempt(t4Exempt);

        dto.setTable6ItcIgst(t6Igst);
        dto.setTable6ItcCgst(t6Cgst);
        dto.setTable6ItcSgst(t6Sgst);

        dto.setTable9PayableIgst(t9PayableIgst);
        dto.setTable9PayableCgst(t9PayableCgst);
        dto.setTable9PayableSgst(t9PayableSgst);
        dto.setTable9PaidIgst(t9PaidIgst);
        dto.setTable9PaidCgst(t9PaidCgst);
        dto.setTable9PaidSgst(t9PaidSgst);

        dto.setDiscrepancyIgst(t9PayableIgst.subtract(t9PaidIgst));
        dto.setDiscrepancyCgst(t9PayableCgst.subtract(t9PaidCgst));
        dto.setDiscrepancySgst(t9PayableSgst.subtract(t9PaidSgst));

        dto.setOutwardIgst(outIgst);
        dto.setOutwardCgst(outCgst);
        dto.setOutwardSgst(outSgst);

        return dto;
    }

    private BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
}
