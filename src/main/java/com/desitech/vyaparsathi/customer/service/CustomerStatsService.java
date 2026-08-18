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

    /**
     * Page size used when walking payment / credit-note pages to
     * compute aggregate totals. 500 keeps each query cheap while
     * still amortising the round-trip count over a large customer.
     */
    private static final int PAGE_SIZE = 500;

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

        // 1. Sales + aging buckets
        // Aging bucket definition (industry-standard):
        //   current: 0-30 days since invoice date
        //   31-60, 61-90, 90+ : days past due
        // Only non-cancelled sales with an unpaid or partially-paid
        // status count toward outstanding + aging.
        List<Sale> sales = saleRepo.findAllByCustomerId(customerId);
        stats.setTotalSales(sales.size());
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal bucketCurrent = BigDecimal.ZERO;
        BigDecimal bucket31_60   = BigDecimal.ZERO;
        BigDecimal bucket61_90   = BigDecimal.ZERO;
        BigDecimal bucket90Plus  = BigDecimal.ZERO;
        LocalDate first = null, last = null;
        LocalDate today = LocalDate.now();

        for (Sale s : sales) {
            BigDecimal total = s.getTotalAmount() == null ? BigDecimal.ZERO : s.getTotalAmount();
            totalValue = totalValue.add(total);

            // Outstanding based on payment status
            if (s.getStatus() != SaleStatus.CANCELLED) {
                if (s.getPaymentStatus() == PaymentStatus.PENDING || s.getPaymentStatus() == PaymentStatus.PARTIALLY_PAID) {
                    outstanding = outstanding.add(total);
                    LocalDate invoiceDate = s.getDate() != null ? s.getDate().toLocalDate() : today;
                    long days = java.time.temporal.ChronoUnit.DAYS.between(invoiceDate, today);
                    if (days <= 30)       bucketCurrent = bucketCurrent.add(total);
                    else if (days <= 60)  bucket31_60   = bucket31_60.add(total);
                    else if (days <= 90)  bucket61_90   = bucket61_90.add(total);
                    else                  bucket90Plus  = bucket90Plus.add(total);
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
        stats.setAgingCurrent(bucketCurrent);
        stats.setAging31_60(bucket31_60);
        stats.setAging61_90(bucket61_90);
        stats.setAging90Plus(bucket90Plus);
        stats.setFirstSaleDate(first);
        stats.setLastSaleDate(last);
        if (sales.size() > 0 && totalValue.signum() > 0) {
            stats.setAverageOrderValue(totalValue.divide(
                    BigDecimal.valueOf(sales.size()), 2, RoundingMode.HALF_UP));
        }

        // 2. Payments & Advance Balance
        // Iterate every page so stats are correct for high-volume
        // customers — the previous single-page-of-1000 cap silently
        // truncated once a customer had 1001+ payments and reported
        // wrong totals with no signal. TODO(phase-3): replace this
        // with SQL-side SUM/COUNT aggregates on PaymentRepository.
        long payCount = 0;
        BigDecimal totalPaid = BigDecimal.ZERO;
        int payPageNum = 0;
        while (true) {
            Page<Payment> paymentPage = paymentRepo.findByCustomerId(customerId, PageRequest.of(payPageNum, PAGE_SIZE));
            if (paymentPage == null || !paymentPage.hasContent()) break;
            for (Payment p : paymentPage.getContent()) {
                if (p.getStatus() == null || p.getStatus() == PaymentStatus.PAID) {
                    payCount++;
                    if (p.getAmount() != null) {
                        totalPaid = totalPaid.add(p.getAmount());
                    }
                }
            }
            if (paymentPage.isLast()) break;
            payPageNum++;
        }
        stats.setPaymentCount(payCount);
        stats.setTotalPaymentsReceived(totalPaid);

        BigDecimal unallocatedAdvance = paymentRepo.getUnallocatedCreditByCustomerId(customerId);
        stats.setAdvanceBalance(unallocatedAdvance != null ? unallocatedAdvance : BigDecimal.ZERO);

        // 3. Credit Notes — same page-walk pattern for correctness.
        if (shopId != null) {
            long cnCount = 0;
            BigDecimal cnTotal = BigDecimal.ZERO;
            BigDecimal cnApplied = BigDecimal.ZERO;
            BigDecimal cnOut = BigDecimal.ZERO;
            int cnPageNum = 0;
            while (true) {
                Page<CreditNote> cns = creditRepo.findByShopIdAndCustomerId(shopId, customerId, PageRequest.of(cnPageNum, PAGE_SIZE));
                if (cns == null || !cns.hasContent()) break;
                if (cnPageNum == 0) cnCount = cns.getTotalElements();
                for (CreditNote cn : cns.getContent()) {
                    if (cn.getTotalAmount() != null) cnTotal = cnTotal.add(cn.getTotalAmount());
                    if (cn.getAppliedAmount() != null) cnApplied = cnApplied.add(cn.getAppliedAmount());
                    cnOut = cnOut.add(cn.getOutstandingAmount());
                }
                if (cns.isLast()) break;
                cnPageNum++;
            }
            stats.setTotalCreditNotes(cnCount);
            stats.setTotalCreditNoteValue(cnTotal);
            stats.setAppliedCreditNoteValue(cnApplied);
            stats.setCreditNotesOutstanding(cnOut);

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

