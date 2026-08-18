package com.desitech.vyaparsathi.customer.service;

import com.desitech.vyaparsathi.accounting.entity.CreditNote;
import com.desitech.vyaparsathi.accounting.repository.CreditNoteRepository;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.customer.dto.CustomerStatsDto;
import com.desitech.vyaparsathi.payment.entity.Payment;
import com.desitech.vyaparsathi.payment.enums.PaymentStatus;
import com.desitech.vyaparsathi.payment.repository.PaymentRepository;
import com.desitech.vyaparsathi.quotation.entity.Quotation;
import com.desitech.vyaparsathi.quotation.repository.QuotationRepository;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.enums.SaleStatus;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import com.desitech.vyaparsathi.salesorder.entity.SalesOrder;
import com.desitech.vyaparsathi.salesorder.repository.SalesOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Aggregated per-customer analytics for the enterprise Customer detail
 * KPI strip. Fully tenant-scoped via TenantContext.
 */
@Service
public class CustomerStatsService {

    private final SaleRepository saleRepo;
    private final CreditNoteRepository creditRepo;
    private final PaymentRepository paymentRepo;
    private final QuotationRepository quotationRepo;
    private final SalesOrderRepository salesOrderRepo;

    public CustomerStatsService(SaleRepository saleRepo,
                                CreditNoteRepository creditRepo,
                                PaymentRepository paymentRepo,
                                QuotationRepository quotationRepo,
                                SalesOrderRepository salesOrderRepo) {
        this.saleRepo = saleRepo;
        this.creditRepo = creditRepo;
        this.paymentRepo = paymentRepo;
        this.quotationRepo = quotationRepo;
        this.salesOrderRepo = salesOrderRepo;
    }

    @Transactional(readOnly = true)
    public CustomerStatsDto compute(Long customerId) {
        CustomerStatsDto stats = new CustomerStatsDto();
        stats.setCustomerId(customerId);
        if (customerId == null) return stats;

        Long shopId = TenantContext.getCurrentShopId();

        // 1. Sales
        List<Sale> sales = saleRepo.findAllByCustomerId(customerId);
        stats.setTotalSales(sales.size());
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        LocalDate first = null, last = null;

        for (Sale s : sales) {
            BigDecimal total = s.getTotalAmount() == null ? BigDecimal.ZERO : s.getTotalAmount();
            totalValue = totalValue.add(total);

            // Outstanding based on payment status
            if (s.getStatus() != SaleStatus.CANCELLED) {
                if (s.getPaymentStatus() == PaymentStatus.PENDING || s.getPaymentStatus() == PaymentStatus.PARTIALLY_PAID) {
                    outstanding = outstanding.add(total);
                }
            }

            if (s.getDate() != null) {
                LocalDate d = s.getDate().toLocalDate();
                if (first == null || d.isBefore(first)) first = d;
                if (last == null || d.isAfter(last)) last = d;
            }
        }
        stats.setTotalSalesValue(totalValue);
        stats.setOutstandingReceivable(outstanding);
        stats.setFirstSaleDate(first);
        stats.setLastSaleDate(last);
        if (sales.size() > 0 && totalValue.signum() > 0) {
            stats.setAverageOrderValue(totalValue.divide(
                    BigDecimal.valueOf(sales.size()), 2, RoundingMode.HALF_UP));
        }

        // 2. Payments & Advance Balance
        Page<Payment> paymentPage = paymentRepo.findByCustomerId(customerId, PageRequest.of(0, 1000));
        long payCount = 0;
        BigDecimal totalPaid = BigDecimal.ZERO;
        if (paymentPage != null && paymentPage.hasContent()) {
            for (Payment p : paymentPage.getContent()) {
                if (p.getStatus() == null || p.getStatus() == PaymentStatus.PAID) {
                    payCount++;
                    if (p.getAmount() != null) {
                        totalPaid = totalPaid.add(p.getAmount());
                    }
                }
            }
        }
        stats.setPaymentCount(payCount);
        stats.setTotalPaymentsReceived(totalPaid);

        BigDecimal unallocatedAdvance = paymentRepo.getUnallocatedCreditByCustomerId(customerId);
        stats.setAdvanceBalance(unallocatedAdvance != null ? unallocatedAdvance : BigDecimal.ZERO);

        // 3. Credit Notes
        if (shopId != null) {
            Page<CreditNote> cns = creditRepo.findByShopIdAndCustomerId(shopId, customerId, PageRequest.of(0, 1000));
            if (cns != null && cns.hasContent()) {
                stats.setTotalCreditNotes(cns.getTotalElements());
                BigDecimal cnTotal = BigDecimal.ZERO;
                BigDecimal cnApplied = BigDecimal.ZERO;
                BigDecimal cnOut = BigDecimal.ZERO;
                for (CreditNote cn : cns.getContent()) {
                    if (cn.getTotalAmount() != null) cnTotal = cnTotal.add(cn.getTotalAmount());
                    if (cn.getAppliedAmount() != null) cnApplied = cnApplied.add(cn.getAppliedAmount());
                    cnOut = cnOut.add(cn.getOutstandingAmount());
                }
                stats.setTotalCreditNoteValue(cnTotal);
                stats.setAppliedCreditNoteValue(cnApplied);
                stats.setCreditNotesOutstanding(cnOut);
            }

            // 4. Quotations
            Page<Quotation> quotes = quotationRepo.findByShopIdAndCustomer_Id(shopId, customerId, PageRequest.of(0, 1));
            stats.setQuotationCount(quotes != null ? quotes.getTotalElements() : 0);

            // 5. Sales Orders
            Page<SalesOrder> orders = salesOrderRepo.findByShopIdAndCustomer_Id(shopId, customerId, PageRequest.of(0, 1));
            stats.setSalesOrderCount(orders != null ? orders.getTotalElements() : 0);
        }

        return stats;
    }
}

