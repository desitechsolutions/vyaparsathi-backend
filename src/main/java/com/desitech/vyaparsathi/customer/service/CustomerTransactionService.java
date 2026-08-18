package com.desitech.vyaparsathi.customer.service;

import com.desitech.vyaparsathi.accounting.entity.CreditNote;
import com.desitech.vyaparsathi.accounting.repository.CreditNoteRepository;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.customer.dto.CustomerTransactionDto;
import com.desitech.vyaparsathi.payment.entity.Payment;
import com.desitech.vyaparsathi.payment.repository.PaymentRepository;
import com.desitech.vyaparsathi.quotation.entity.Quotation;
import com.desitech.vyaparsathi.quotation.repository.QuotationRepository;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import com.desitech.vyaparsathi.salesorder.entity.SalesOrder;
import com.desitech.vyaparsathi.salesorder.repository.SalesOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CustomerTransactionService {

    private final SaleRepository saleRepo;
    private final PaymentRepository paymentRepo;
    private final CreditNoteRepository creditNoteRepo;
    private final QuotationRepository quotationRepo;
    private final SalesOrderRepository salesOrderRepo;

    public CustomerTransactionService(SaleRepository saleRepo,
                                      PaymentRepository paymentRepo,
                                      CreditNoteRepository creditNoteRepo,
                                      QuotationRepository quotationRepo,
                                      SalesOrderRepository salesOrderRepo) {
        this.saleRepo = saleRepo;
        this.paymentRepo = paymentRepo;
        this.creditNoteRepo = creditNoteRepo;
        this.quotationRepo = quotationRepo;
        this.salesOrderRepo = salesOrderRepo;
    }

    @Transactional(readOnly = true)
    public Page<CustomerTransactionDto> getTransactions(Long customerId, int page, int size) {
        Long shopId = TenantContext.getCurrentShopId();
        List<CustomerTransactionDto> allTxns = new ArrayList<>();

        // 1. Sales
        List<Sale> sales = saleRepo.findAllByCustomerId(customerId);
        if (sales != null) {
            for (Sale s : sales) {
                CustomerTransactionDto dto = new CustomerTransactionDto();
                dto.setType("SALE");
                dto.setId(s.getId());
                dto.setRefNo(s.getInvoiceNo() != null ? s.getInvoiceNo() : "SALE-" + s.getId());
                dto.setDate(s.getDate());
                dto.setAmount(s.getTotalAmount() != null ? s.getTotalAmount() : BigDecimal.ZERO);
                dto.setStatus(s.getStatus() != null ? s.getStatus().name() : "COMPLETED");
                dto.setDescription("Invoice #" + (s.getInvoiceNo() != null ? s.getInvoiceNo() : s.getId()));
                allTxns.add(dto);
            }
        }

        // 2. Payments
        Page<Payment> payments = paymentRepo.findByCustomerId(customerId, PageRequest.of(0, 100));
        if (payments != null && payments.hasContent()) {
            for (Payment p : payments.getContent()) {
                CustomerTransactionDto dto = new CustomerTransactionDto();
                dto.setType("PAYMENT");
                dto.setId(p.getId());
                dto.setRefNo(p.getTransactionId() != null ? p.getTransactionId() : "PAY-" + p.getId());
                dto.setDate(p.getPaymentDate());
                dto.setAmount(p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO);
                dto.setStatus(p.getStatus() != null ? p.getStatus().name() : "PAID");
                String method = p.getPaymentMethod() != null ? p.getPaymentMethod().name() : "CASH";
                dto.setDescription("Payment received (" + method + ")");
                allTxns.add(dto);
            }
        }

        // 3. Credit Notes
        if (shopId != null) {
            Page<CreditNote> creditNotes = creditNoteRepo.findByShopIdAndCustomerId(shopId, customerId, PageRequest.of(0, 100));
            if (creditNotes != null && creditNotes.hasContent()) {
                for (CreditNote cn : creditNotes.getContent()) {
                    CustomerTransactionDto dto = new CustomerTransactionDto();
                    dto.setType("CREDIT_NOTE");
                    dto.setId(cn.getId());
                    dto.setRefNo(cn.getCreditNoteNo());
                    dto.setDate(cn.getCreditNoteDate() != null ? cn.getCreditNoteDate().atStartOfDay() : cn.getCreatedAt());
                    dto.setAmount(cn.getTotalAmount() != null ? cn.getTotalAmount() : BigDecimal.ZERO);
                    dto.setStatus(cn.getStatus() != null ? cn.getStatus().name() : "ISSUED");
                    dto.setDescription("Credit Note: " + (cn.getReason() != null ? cn.getReason() : "Return/Credit"));
                    allTxns.add(dto);
                }
            }
        }

        // 4. Quotations
        if (shopId != null) {
            Page<Quotation> quotes = quotationRepo.findByShopIdAndCustomer_Id(shopId, customerId, PageRequest.of(0, 50));
            if (quotes != null && quotes.hasContent()) {
                for (Quotation q : quotes.getContent()) {
                    CustomerTransactionDto dto = new CustomerTransactionDto();
                    dto.setType("QUOTATION");
                    dto.setId(q.getId());
                    dto.setRefNo(q.getQuotationNo());
                    dto.setDate(q.getQuotationDate() != null ? q.getQuotationDate() : q.getCreatedAt());
                    dto.setAmount(q.getTotalAmount() != null ? q.getTotalAmount() : BigDecimal.ZERO);
                    dto.setStatus(q.getStatus() != null ? q.getStatus().name() : "DRAFT");
                    dto.setDescription("Quotation #" + q.getQuotationNo());
                    allTxns.add(dto);
                }
            }
        }

        // 5. Sales Orders
        if (shopId != null) {
            Page<SalesOrder> orders = salesOrderRepo.findByShopIdAndCustomer_Id(shopId, customerId, PageRequest.of(0, 50));
            if (orders != null && orders.hasContent()) {
                for (SalesOrder so : orders.getContent()) {
                    CustomerTransactionDto dto = new CustomerTransactionDto();
                    dto.setType("SALES_ORDER");
                    dto.setId(so.getId());
                    dto.setRefNo(so.getOrderNo());
                    dto.setDate(so.getOrderDate() != null ? so.getOrderDate() : so.getCreatedAt());
                    dto.setAmount(so.getTotalAmount() != null ? so.getTotalAmount() : BigDecimal.ZERO);
                    dto.setStatus(so.getStatus() != null ? so.getStatus().name() : "DRAFT");
                    dto.setDescription("Sales Order #" + so.getOrderNo());
                    allTxns.add(dto);
                }
            }
        }


        // Sort descending by date
        allTxns.sort((a, b) -> {
            if (a.getDate() == null && b.getDate() == null) return 0;
            if (a.getDate() == null) return 1;
            if (b.getDate() == null) return -1;
            return b.getDate().compareTo(a.getDate());
        });

        int fromIndex = Math.min(page * size, allTxns.size());
        int toIndex = Math.min(fromIndex + size, allTxns.size());
        List<CustomerTransactionDto> pagedList = allTxns.subList(fromIndex, toIndex);

        return new PageImpl<>(pagedList, PageRequest.of(page, size), allTxns.size());
    }
}
