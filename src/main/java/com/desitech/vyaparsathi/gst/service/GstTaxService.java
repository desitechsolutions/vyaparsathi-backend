package com.desitech.vyaparsathi.gst.service;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class GstTaxService {

    private final SaleRepository saleRepo;
    private final PurchaseInvoiceRepository purchaseRepo;
    private final CreditNoteRepository creditRepo;
    private final ShopRepository shopRepo;

    public GstTaxService(SaleRepository saleRepo,
                         PurchaseInvoiceRepository purchaseRepo,
                         CreditNoteRepository creditRepo,
                         ShopRepository shopRepo) {
        this.saleRepo = saleRepo;
        this.purchaseRepo = purchaseRepo;
        this.creditRepo = creditRepo;
        this.shopRepo = shopRepo;
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
        export.setFp(String.format("%02d%d", month, year));

        BigDecimal totalGross = BigDecimal.ZERO;

        for (Sale sale : sales) {
            totalGross = totalGross.add(sale.getGrandTotal());

            boolean isB2B = sale.getCustomer() != null && sale.getCustomer().getGstin() != null && !sale.getCustomer().getGstin().isBlank();
            BigDecimal grandTotal = sale.getGrandTotal();
            boolean isB2CLarge = !isB2B && grandTotal.compareTo(new BigDecimal("250000")) > 0 && !isIntraState(shop, sale);

            if (isB2B) {
                Gstr1ExportDto.B2bInvoice b2b = new Gstr1ExportDto.B2bInvoice();
                b2b.setCtin(sale.getCustomer().getGstin());
                b2b.setInvNo(sale.getInvoiceNo());
                b2b.setInvDate(sale.getDate().toLocalDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
                b2b.setVal(sale.getGrandTotal());
                b2b.setPos(sale.getPlaceOfSupply() != null ? sale.getPlaceOfSupply() : shop.getState());
                export.getB2b().add(b2b);
            } else if (isB2CLarge) {
                Gstr1ExportDto.B2cLargeInvoice b2cl = new Gstr1ExportDto.B2cLargeInvoice();
                b2cl.setInvNo(sale.getInvoiceNo());
                b2cl.setInvDate(sale.getDate().toLocalDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
                b2cl.setVal(sale.getGrandTotal());
                b2cl.setPos(sale.getPlaceOfSupply() != null ? sale.getPlaceOfSupply() : shop.getState());
                export.getB2cl().add(b2cl);
            } else {
                Gstr1ExportDto.B2cSmallSummary b2cs = new Gstr1ExportDto.B2cSmallSummary();
                b2cs.setPos(sale.getPlaceOfSupply() != null ? sale.getPlaceOfSupply() : shop.getState());
                b2cs.setTxval(sale.getTaxableAmount());
                b2cs.setIamti(sale.getIgstAmount());
                b2cs.setCamti(sale.getCgstAmount());
                b2cs.setSamti(sale.getSgstAmount());
                export.getB2cs().add(b2cs);
            }
        }

        export.setGrossTurnover(totalGross);
        return export;
    }

    @Transactional(readOnly = true)
    public Gstr3bSummaryDto generateGstr3b(int year, int month) {
        Long shopId = TenantUtils.getCurrentShopId();
        Shop shop = shopRepo.findById(shopId).orElseThrow(() -> new IllegalArgumentException("Shop not found"));

        LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime end = start.plusMonths(1).minusNanos(1);

        List<Sale> sales = saleRepo.findAllByShopIdAndDateBetween(shopId, start, end);
        List<PurchaseInvoice> purchases = purchaseRepo.findAllByShopId(shopId, org.springframework.data.domain.Pageable.unpaged()).getContent();

        Gstr3bSummaryDto summary = new Gstr3bSummaryDto();
        summary.setGstin(shop.getGstin() != null ? shop.getGstin() : "UNREGISTERED");
        summary.setMonthYear(String.format("%02d-%d", month, year));

        // 3.1 Outward Taxable Supplies
        BigDecimal outTaxable = BigDecimal.ZERO;
        BigDecimal outCgst = BigDecimal.ZERO;
        BigDecimal outSgst = BigDecimal.ZERO;
        BigDecimal outIgst = BigDecimal.ZERO;

        for (Sale sale : sales) {
            outTaxable = outTaxable.add(sale.getTaxableAmount());
            outCgst = outCgst.add(sale.getCgstAmount());
            outSgst = outSgst.add(sale.getSgstAmount());
            outIgst = outIgst.add(sale.getIgstAmount());
        }

        summary.getOutwardTaxableSupplies().setTaxableValue(outTaxable);
        summary.getOutwardTaxableSupplies().setCgst(outCgst);
        summary.getOutwardTaxableSupplies().setSgst(outSgst);
        summary.getOutwardTaxableSupplies().setIgst(outIgst);

        // 4. Input Tax Credit (ITC) Available from Purchase Invoices
        BigDecimal itcCgst = BigDecimal.ZERO;
        BigDecimal itcSgst = BigDecimal.ZERO;
        BigDecimal itcIgst = BigDecimal.ZERO;

        for (PurchaseInvoice purchase : purchases) {
            if (purchase.getInvoiceDate() != null &&
                purchase.getInvoiceDate().getYear() == year &&
                purchase.getInvoiceDate().getMonthValue() == month) {

                itcCgst = itcCgst.add(purchase.getCgstAmount());
                itcSgst = itcSgst.add(purchase.getSgstAmount());
                itcIgst = itcIgst.add(purchase.getIgstAmount());
            }
        }

        summary.getItcAvailable().setCgst(itcCgst);
        summary.getItcAvailable().setSgst(itcSgst);
        summary.getItcAvailable().setIgst(itcIgst);

        // 5. Net Tax Liability = Outward Tax - Eligible ITC
        summary.getNetTaxLiability().setCgstPayable(outCgst.subtract(itcCgst).max(BigDecimal.ZERO));
        summary.getNetTaxLiability().setSgstPayable(outSgst.subtract(itcSgst).max(BigDecimal.ZERO));
        summary.getNetTaxLiability().setIgstPayable(outIgst.subtract(itcIgst).max(BigDecimal.ZERO));

        return summary;
    }

    private boolean isIntraState(Shop shop, Sale sale) {
        String shopState = shop.getState();
        String pos = sale.getPlaceOfSupply();
        return pos == null || pos.equalsIgnoreCase(shopState);
    }
}
