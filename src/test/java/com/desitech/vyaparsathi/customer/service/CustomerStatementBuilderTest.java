package com.desitech.vyaparsathi.customer.service;

import com.desitech.vyaparsathi.accounting.entity.CreditNote;
import com.desitech.vyaparsathi.accounting.repository.CreditNoteRepository;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.customer.entity.CustomerLedger;
import com.desitech.vyaparsathi.customer.entity.CustomerLedgerType;
import com.desitech.vyaparsathi.customer.repository.CustomerLedgerRepository;
import com.desitech.vyaparsathi.customer.service.CustomerStatementBuilder.Statement;
import com.desitech.vyaparsathi.customer.service.CustomerStatementBuilder.StatementLine;
import com.desitech.vyaparsathi.payment.entity.Payment;
import com.desitech.vyaparsathi.payment.enums.PaymentStatus;
import com.desitech.vyaparsathi.payment.repository.PaymentRepository;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.enums.SaleStatus;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerStatementBuilderTest {

    @Mock SaleRepository saleRepo;
    @Mock PaymentRepository paymentRepo;
    @Mock CreditNoteRepository creditNoteRepo;
    @Mock CustomerLedgerRepository ledgerRepo;

    @InjectMocks CustomerStatementBuilder builder;

    private static final Long CID = 1L;
    private static final Long SHOP_ID = 42L;
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 15, 10, 0);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentShopId(SHOP_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private BigDecimal bd(double v) { return BigDecimal.valueOf(v); }

    private Sale sale(Long id, String inv, double amt, SaleStatus st, PaymentStatus pst, LocalDateTime date) {
        Sale s = mock(Sale.class);
        when(s.getId()).thenReturn(id);
        when(s.getInvoiceNo()).thenReturn(inv);
        when(s.getTotalAmount()).thenReturn(bd(amt));
        when(s.getStatus()).thenReturn(st);
        when(s.getPaymentStatus()).thenReturn(pst);
        when(s.getDate()).thenReturn(date);
        return s;
    }

    private Payment pay(Long id, double amt, LocalDateTime date) {
        Payment p = mock(Payment.class);
        when(p.getId()).thenReturn(id);
        when(p.getAmount()).thenReturn(bd(amt));
        when(p.getStatus()).thenReturn(PaymentStatus.PAID);
        when(p.getPaymentDate()).thenReturn(date);
        when(p.getPaymentMethod()).thenReturn(null);
        return p;
    }

    private CustomerLedger ledger(CustomerLedgerType type, String desc, double amt, LocalDateTime date) {
        CustomerLedger le = mock(CustomerLedger.class);
        when(le.getType()).thenReturn(type);
        when(le.getDescription()).thenReturn(desc);
        when(le.getAmount()).thenReturn(bd(amt));
        when(le.getCreatedAt()).thenReturn(date);
        return le;
    }

    private CreditNote cn(Long id, String no, double amt, LocalDateTime date) {
        CreditNote c = mock(CreditNote.class);
        when(c.getId()).thenReturn(id);
        when(c.getCreditNoteNo()).thenReturn(no);
        when(c.getTotalAmount()).thenReturn(bd(amt));
        when(c.getCreatedAt()).thenReturn(date);
        when(c.getReason()).thenReturn(null);
        return c;
    }

    private void setup(List<Sale> sales, List<Payment> pays, List<CreditNote> cns, List<CustomerLedger> les) {
        when(saleRepo.findAllByCustomerId(CID)).thenReturn(sales);
        when(paymentRepo.findByCustomerId(eq(CID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(pays, PageRequest.of(0, 500), pays.size()));
        when(creditNoteRepo.findByShopIdAndCustomerId(eq(SHOP_ID), eq(CID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(cns, PageRequest.of(0, 500), cns.size()));
        when(ledgerRepo.findByCustomerIdAndDateRange(eq(CID), isNull(), isNull()))
                .thenReturn(les);
    }

    // ─── T1: Unpaid credit sale ────────────────────────────────────────────
    @Test
    @DisplayName("T1: unpaid sale ₹250 → closing balance ₹250; Category-A CL entry skipped")
    void t1_unpaidCreditSale_closingBalanceEqualsInvoiceAmount() {
        Sale s = sale(1L, "INV-001", 250, SaleStatus.COMPLETED, PaymentStatus.PENDING, T0);
        CustomerLedger clSale = ledger(CustomerLedgerType.CREDIT, "Sale #INV-001", 250, T0);
        setup(List.of(s), List.of(), List.of(), List.of(clSale));

        Statement stmt = builder.build(CID, null, null);

        assertThat(stmt.closingBalance).isEqualByComparingTo("250.00");
        assertThat(stmt.totalInvoiced).isEqualByComparingTo("250.00");
        assertThat(stmt.totalPaid).isEqualByComparingTo("0.00");
        // CL entry with "Sale #" must be skipped → only the INVOICE row
        assertThat(stmt.lines).hasSize(1);
        assertThat(stmt.lines.get(0).type).isEqualTo("INVOICE");
    }

    // ─── T2: Fully paid sale ──────────────────────────────────────────────
    @Test
    @DisplayName("T2: fully paid sale ₹250 → closing balance ₹0; both Category-A CL entries skipped")
    void t2_fullyPaidSale_closingBalanceIsZero() {
        Sale s = sale(1L, "INV-001", 250, SaleStatus.COMPLETED, PaymentStatus.PAID, T0);
        Payment p = pay(10L, 250, T0.plusDays(2));
        // Both are Category A — must be skipped
        CustomerLedger clSale = ledger(CustomerLedgerType.CREDIT, "Sale #INV-001", 250, T0);
        CustomerLedger clPay  = ledger(CustomerLedgerType.DEBIT,  "Payment for INVOICE #1", 250, T0.plusDays(2));
        setup(List.of(s), List.of(p), List.of(), List.of(clSale, clPay));

        Statement stmt = builder.build(CID, null, null);

        assertThat(stmt.closingBalance).isEqualByComparingTo("0.00");
        assertThat(stmt.totalInvoiced).isEqualByComparingTo("250.00");
        assertThat(stmt.totalPaid).isEqualByComparingTo("250.00");
        // Only INVOICE + PAYMENT; no ledger lines
        assertThat(stmt.lines).hasSize(2);
        assertThat(stmt.lines.stream().noneMatch(l -> l.type.startsWith("LEDGER"))).isTrue();
    }

    // ─── T3: Partial payment ──────────────────────────────────────────────
    @Test
    @DisplayName("T3: ₹250 sale, ₹100 paid → closing balance ₹150")
    void t3_partialPayment_closingBalanceIsRemainder() {
        Sale s = sale(1L, "INV-001", 250, SaleStatus.COMPLETED, PaymentStatus.PARTIALLY_PAID, T0);
        Payment p = pay(10L, 100, T0.plusDays(1));
        CustomerLedger clSale = ledger(CustomerLedgerType.CREDIT, "Sale #INV-001", 250, T0);
        CustomerLedger clPay  = ledger(CustomerLedgerType.DEBIT,  "Payment for INVOICE #1", 100, T0.plusDays(1));
        setup(List.of(s), List.of(p), List.of(), List.of(clSale, clPay));

        Statement stmt = builder.build(CID, null, null);

        assertThat(stmt.closingBalance).isEqualByComparingTo("150.00");
        assertThat(stmt.totalInvoiced).isEqualByComparingTo("250.00");
        assertThat(stmt.totalPaid).isEqualByComparingTo("100.00");
        assertThat(stmt.lines).hasSize(2);
    }

    // ─── T4: Bulk payment ─────────────────────────────────────────────────
    @Test
    @DisplayName("T4: two sales ₹200+₹300 paid by bulk payment ₹500 → closing ₹0; CL entry skipped")
    void t4_bulkPayment_closingBalanceIsZero() {
        Sale s1 = sale(1L, "INV-001", 200, SaleStatus.COMPLETED, PaymentStatus.PAID, T0);
        Sale s2 = sale(2L, "INV-002", 300, SaleStatus.COMPLETED, PaymentStatus.PAID, T0.plusDays(3));
        Payment bulkPay = pay(20L, 500, T0.plusDays(5));
        CustomerLedger clBulk = ledger(CustomerLedgerType.DEBIT,
                "Bulk Payment [CASH] for Invoices: INV-001, INV-002", 500, T0.plusDays(5));
        setup(List.of(s1, s2), List.of(bulkPay), List.of(), List.of(clBulk));

        Statement stmt = builder.build(CID, null, null);

        assertThat(stmt.closingBalance).isEqualByComparingTo("0.00");
        // Bulk CL skipped → 2 invoices + 1 payment only
        assertThat(stmt.lines).hasSize(3);
        assertThat(stmt.lines.stream().noneMatch(l -> l.type.startsWith("LEDGER"))).isTrue();
    }

    // ─── T5: Cancelled sale ───────────────────────────────────────────────
    @Test
    @DisplayName("T5: cancelled sale → no invoice row; both Category-A CL entries skipped → closing ₹0")
    void t5_cancelledSale_closingBalanceIsZero() {
        Sale s = sale(1L, "INV-001", 250, SaleStatus.CANCELLED, PaymentStatus.PENDING, T0);
        CustomerLedger clSale   = ledger(CustomerLedgerType.CREDIT, "Sale #INV-001", 250, T0);
        CustomerLedger clCancel = ledger(CustomerLedgerType.DEBIT,  "Cancelled Sale #INV-001", 250, T0.plusHours(1));
        setup(List.of(s), List.of(), List.of(), List.of(clSale, clCancel));

        Statement stmt = builder.build(CID, null, null);

        assertThat(stmt.closingBalance).isEqualByComparingTo("0.00");
        assertThat(stmt.lines).isEmpty();
    }

    // ─── T6: Sales return (Category B — DEBIT → statement credit) ─────────
    @Test
    @DisplayName("T6: sales return DEBIT entry → statement CREDIT; reduces outstanding to ₹0")
    void t6_salesReturn_appearsAsStatementCreditAndReducesOutstanding() {
        Sale s = sale(1L, "INV-001", 250, SaleStatus.COMPLETED, PaymentStatus.PENDING, T0);
        // Category B: "Sales Return (Debt Cancel)" — CL DEBIT → statement CREDIT
        CustomerLedger clReturn = ledger(CustomerLedgerType.DEBIT,
                "Sales Return (Debt Cancel) - Inv #INV-001", 250, T0.plusDays(1));
        CustomerLedger clSale   = ledger(CustomerLedgerType.CREDIT, "Sale #INV-001", 250, T0);
        setup(List.of(s), List.of(), List.of(), List.of(clSale, clReturn));

        Statement stmt = builder.build(CID, null, null);

        assertThat(stmt.closingBalance).isEqualByComparingTo("0.00");
        assertThat(stmt.totalCredits).isEqualByComparingTo("250.00");
        assertThat(stmt.lines).hasSize(2); // INVOICE + LEDGER_CREDIT
        StatementLine returnLine = stmt.lines.stream()
                .filter(l -> "LEDGER_CREDIT".equals(l.type)).findFirst().orElseThrow();
        assertThat(returnLine.credit).isEqualByComparingTo("250.00");
        assertThat(returnLine.debit).isEqualByComparingTo("0.00");
    }

    // ─── T7: Refund (Category B — CREDIT → statement debit) ──────────────
    @Test
    @DisplayName("T7: refund CREDIT entry → statement DEBIT; net balance ₹0 after payment + refund")
    void t7_refund_appearsAsStatementDebit() {
        Payment p = pay(10L, 250, T0);
        // Category B: "Refund " — CL CREDIT → statement DEBIT
        CustomerLedger clRefund = ledger(CustomerLedgerType.CREDIT,
                "Refund RFD-001 against Payment #10", 250, T0.plusDays(1));
        setup(List.of(), List.of(p), List.of(), List.of(clRefund));

        Statement stmt = builder.build(CID, null, null);

        // Payment credited ₹250, refund debited ₹250 → net 0
        assertThat(stmt.closingBalance).isEqualByComparingTo("0.00");
        StatementLine refundLine = stmt.lines.stream()
                .filter(l -> "LEDGER_DEBIT".equals(l.type)).findFirst().orElseThrow();
        assertThat(refundLine.debit).isEqualByComparingTo("250.00");
        assertThat(refundLine.credit).isEqualByComparingTo("0.00");
        assertThat(stmt.totalInvoiced).isEqualByComparingTo("250.00"); // refund adds to debit total
    }

    // ─── T8: Manual DEBIT ledger entry → statement credit ─────────────────
    @Test
    @DisplayName("T8: manual CL DEBIT entry → statement CREDIT; closing balance −₹100")
    void t8_manualDebitEntry_appearsAsStatementCredit() {
        CustomerLedger clManual = ledger(CustomerLedgerType.DEBIT, "Manual adjustment", 100, T0);
        setup(List.of(), List.of(), List.of(), List.of(clManual));

        Statement stmt = builder.build(CID, null, null);

        assertThat(stmt.lines).hasSize(1);
        StatementLine line = stmt.lines.get(0);
        assertThat(line.type).isEqualTo("LEDGER_CREDIT");
        assertThat(line.credit).isEqualByComparingTo("100.00");
        assertThat(line.debit).isEqualByComparingTo("0.00");
        assertThat(stmt.totalCredits).isEqualByComparingTo("100.00");
        assertThat(stmt.closingBalance).isEqualByComparingTo("-100.00");
    }

    // ─── T9: Manual CREDIT ledger entry → statement debit ─────────────────
    @Test
    @DisplayName("T9: manual CL CREDIT entry → statement DEBIT; closing balance +₹100")
    void t9_manualCreditEntry_appearsAsStatementDebit() {
        CustomerLedger clManual = ledger(CustomerLedgerType.CREDIT, "Manual charge", 100, T0);
        setup(List.of(), List.of(), List.of(), List.of(clManual));

        Statement stmt = builder.build(CID, null, null);

        assertThat(stmt.lines).hasSize(1);
        StatementLine line = stmt.lines.get(0);
        assertThat(line.type).isEqualTo("LEDGER_DEBIT");
        assertThat(line.debit).isEqualByComparingTo("100.00");
        assertThat(line.credit).isEqualByComparingTo("0.00");
        assertThat(stmt.totalInvoiced).isEqualByComparingTo("100.00");
        assertThat(stmt.closingBalance).isEqualByComparingTo("100.00");
    }

    // ─── T10: Combined scenario ────────────────────────────────────────────
    @Test
    @DisplayName("T10: 2 sales + payment + partial return → closing ₹150")
    void t10_combined_multipleTransactionTypes() {
        // INV-001 ₹300 paid; INV-002 ₹200 unpaid with ₹50 partial return
        Sale s1 = sale(1L, "INV-001", 300, SaleStatus.COMPLETED, PaymentStatus.PAID, T0);
        Sale s2 = sale(2L, "INV-002", 200, SaleStatus.COMPLETED, PaymentStatus.PENDING, T0.plusDays(5));
        Payment p = pay(10L, 300, T0.plusDays(2));
        CustomerLedger clReturn = ledger(CustomerLedgerType.DEBIT,
                "Sales Return (Debt Cancel) - Inv #INV-002", 50, T0.plusDays(7));
        // Category A entries — all must be skipped
        CustomerLedger clS1  = ledger(CustomerLedgerType.CREDIT, "Sale #INV-001", 300, T0);
        CustomerLedger clS2  = ledger(CustomerLedgerType.CREDIT, "Sale #INV-002", 200, T0.plusDays(5));
        CustomerLedger clPay = ledger(CustomerLedgerType.DEBIT,  "Payment for INVOICE #1", 300, T0.plusDays(2));
        setup(List.of(s1, s2), List.of(p), List.of(), List.of(clS1, clS2, clPay, clReturn));

        Statement stmt = builder.build(CID, null, null);

        // ₹300 + ₹200 − ₹300 − ₹50 = ₹150
        assertThat(stmt.closingBalance).isEqualByComparingTo("150.00");
        assertThat(stmt.totalInvoiced).isEqualByComparingTo("500.00");
        assertThat(stmt.totalPaid).isEqualByComparingTo("300.00");
        assertThat(stmt.totalCredits).isEqualByComparingTo("50.00");
        // 2 invoice rows + 1 payment row + 1 return row (3 category A skipped)
        assertThat(stmt.lines).hasSize(4);
    }

    // ─── T11: Date-range statement with opening balance ────────────────────
    @Test
    @DisplayName("T11: pre-period unpaid ₹100 sale → opening balance ₹100; in-period ₹250 → closing ₹350")
    void t11_dateRange_openingBalanceIncludesPrePeriodSales() {
        LocalDateTime periodStart = LocalDateTime.of(2026, 2, 1, 0, 0);
        Sale preSale = sale(1L, "INV-000", 100, SaleStatus.COMPLETED, PaymentStatus.PENDING, T0);
        Sale inSale  = sale(2L, "INV-001", 250, SaleStatus.COMPLETED, PaymentStatus.PENDING, periodStart.plusDays(2));
        CustomerLedger clPre = ledger(CustomerLedgerType.CREDIT, "Sale #INV-000", 100, T0);
        CustomerLedger clIn  = ledger(CustomerLedgerType.CREDIT, "Sale #INV-001", 250, periodStart.plusDays(2));
        setup(List.of(preSale, inSale), List.of(), List.of(), List.of(clPre, clIn));

        Statement stmt = builder.build(CID, periodStart, null);

        assertThat(stmt.openingBalance).isEqualByComparingTo("100.00");
        assertThat(stmt.closingBalance).isEqualByComparingTo("350.00");
        // Opening row + in-period invoice row
        assertThat(stmt.lines).hasSize(2);
        assertThat(stmt.lines.get(0).type).isEqualTo("OPENING");
    }

    // ─── T12: Period totals (debit / credit / paid sum correctly) ──────────
    @Test
    @DisplayName("T12: invoice ₹500, payment ₹200, credit note ₹50 → period totals correct; closing ₹250")
    void t12_periodTotals_sumCorrectly() {
        Sale s = sale(1L, "INV-001", 500, SaleStatus.COMPLETED, PaymentStatus.PARTIALLY_PAID, T0);
        Payment p = pay(10L, 200, T0.plusDays(3));
        CreditNote c = cn(5L, "CN-001", 50, T0.plusDays(5));
        setup(List.of(s), List.of(p), List.of(c), List.of());

        Statement stmt = builder.build(CID, null, null);

        assertThat(stmt.totalInvoiced).isEqualByComparingTo("500.00");
        assertThat(stmt.totalPaid).isEqualByComparingTo("200.00");
        assertThat(stmt.totalCredits).isEqualByComparingTo("50.00");
        // 500 − 200 − 50 = ₹250
        assertThat(stmt.closingBalance).isEqualByComparingTo("250.00");
        assertThat(stmt.lines).hasSize(3); // invoice + payment + credit note
    }

    // ─── T13: "Due Payment for Sale #" prefix skipped ─────────────────────
    @Test
    @DisplayName("T13: 'Due Payment for Sale #' CL entry is Category A → skipped; balance matches paymentRepo")
    void t13_duePaymentForSalePrefix_isSkipped() {
        Sale s = sale(1L, "INV-001", 250, SaleStatus.COMPLETED, PaymentStatus.PAID, T0);
        Payment p = pay(10L, 250, T0.plusDays(1));
        CustomerLedger clSale = ledger(CustomerLedgerType.CREDIT, "Sale #INV-001", 250, T0);
        CustomerLedger clDue  = ledger(CustomerLedgerType.DEBIT,  "Due Payment for Sale #1 (CASH)", 250, T0.plusDays(1));
        setup(List.of(s), List.of(p), List.of(), List.of(clSale, clDue));

        Statement stmt = builder.build(CID, null, null);

        assertThat(stmt.closingBalance).isEqualByComparingTo("0.00");
        assertThat(stmt.lines).hasSize(2); // invoice + payment only
        assertThat(stmt.lines.stream().noneMatch(l -> l.type.startsWith("LEDGER"))).isTrue();
    }
}
