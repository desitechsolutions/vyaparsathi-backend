package com.desitech.vyaparsathi.customer.service;

import com.desitech.vyaparsathi.accounting.entity.CreditNote;
import com.desitech.vyaparsathi.accounting.repository.CreditNoteRepository;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.customer.entity.CustomerLedger;
import com.desitech.vyaparsathi.customer.entity.CustomerLedgerType;
import com.desitech.vyaparsathi.customer.repository.CustomerLedgerRepository;
import com.desitech.vyaparsathi.payment.entity.Payment;
import com.desitech.vyaparsathi.payment.enums.PaymentSourceType;
import com.desitech.vyaparsathi.payment.enums.PaymentStatus;
import com.desitech.vyaparsathi.payment.repository.PaymentRepository;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.enums.SaleStatus;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Assembles a unified statement of account from real transactional
 * records — sales, payments, credit notes, plus any manual ledger
 * entries — with a running balance and 4-bucket aging breakdown.
 *
 * <p>The previous {@code CustomerStatementPdfService} rendered only
 * {@code CustomerLedger} rows (manual journal entries). For most
 * customers that table is empty, so the PDF showed "No transactions"
 * even for accounts with dozens of invoices. This builder pulls the
 * source-of-truth data instead.</p>
 */
@Service
public class CustomerStatementBuilder {

    private final SaleRepository saleRepo;
    private final PaymentRepository paymentRepo;
    private final CreditNoteRepository creditNoteRepo;
    private final CustomerLedgerRepository ledgerRepo;

    public CustomerStatementBuilder(SaleRepository saleRepo,
                                    PaymentRepository paymentRepo,
                                    CreditNoteRepository creditNoteRepo,
                                    CustomerLedgerRepository ledgerRepo) {
        this.saleRepo = saleRepo;
        this.paymentRepo = paymentRepo;
        this.creditNoteRepo = creditNoteRepo;
        this.ledgerRepo = ledgerRepo;
    }

    /** One row in the assembled statement. */
    @Getter
    @Setter
    public static class StatementLine {
        public LocalDate date;
        public String reference;      // Invoice no / Payment id / CN no
        public String description;
        public String type;           // INVOICE / PAYMENT / CREDIT_NOTE / LEDGER_DEBIT / LEDGER_CREDIT
        public BigDecimal debit;      // amount owed by customer (invoice)
        public BigDecimal credit;     // amount paid or credited (payment / CN)
        public BigDecimal runningBalance;  // + = customer owes, − = customer in credit
    }

    /** Aging bucket totals derived from open (unpaid / partially paid) invoices. */
    @Getter
    @Setter
    public static class Aging {
        public BigDecimal current = BigDecimal.ZERO;   // 0-30 days
        public BigDecimal bucket31_60 = BigDecimal.ZERO;
        public BigDecimal bucket61_90 = BigDecimal.ZERO;
        public BigDecimal bucket90Plus = BigDecimal.ZERO;
        public BigDecimal total() {
            return current.add(bucket31_60).add(bucket61_90).add(bucket90Plus);
        }
    }

    /** Bundle returned to the PDF renderer and web clients. */
    @Getter
    @Setter
    public static class Statement {
        public List<StatementLine> lines = new ArrayList<>();
        public BigDecimal openingBalance = BigDecimal.ZERO;
        public BigDecimal closingBalance = BigDecimal.ZERO;
        public BigDecimal totalInvoiced = BigDecimal.ZERO;
        public BigDecimal totalPaid = BigDecimal.ZERO;
        public BigDecimal totalCredits = BigDecimal.ZERO;
        public Aging aging = new Aging();
    }

    @Transactional(readOnly = true)
    public Statement build(Long customerId, LocalDateTime startDate, LocalDateTime endDate) {
        Statement s = new Statement();
        Long shopId = TenantContext.getCurrentShopId();

        // ─── Gather rows from all four sources ─────────────────────────
        List<Sale> sales = saleRepo.findAllByCustomerId(customerId);
        List<Payment> payments = new ArrayList<>();
        {
            int page = 0;
            while (true) {
                var pg = paymentRepo.findByCustomerId(customerId, PageRequest.of(page, 500));
                if (pg == null || !pg.hasContent()) break;
                payments.addAll(pg.getContent());
                if (pg.isLast()) break;
                page++;
            }
        }
        List<CreditNote> creditNotes = new ArrayList<>();
        if (shopId != null) {
            int page = 0;
            while (true) {
                var pg = creditNoteRepo.findByShopIdAndCustomerId(shopId, customerId, PageRequest.of(page, 500));
                if (pg == null || !pg.hasContent()) break;
                creditNotes.addAll(pg.getContent());
                if (pg.isLast()) break;
                page++;
            }
        }
        // Ledger — pulled unconstrained; we apply the date filter
        // ourselves so the same start/end logic covers all sources.
        List<CustomerLedger> ledger = ledgerRepo.findByCustomerIdAndDateRange(customerId, null, null);

        // ─── Compute opening balance = state BEFORE start date ─────────
        // Sum of (invoice - payment - credit note) whose date < startDate.
        if (startDate != null) {
            for (Sale sale : sales) {
                if (sale.getStatus() == SaleStatus.CANCELLED) continue;
                LocalDateTime saleDate = sale.getDate();
                if (saleDate != null && saleDate.isBefore(startDate)) {
                    s.openingBalance = s.openingBalance.add(nz(sale.getTotalAmount()));
                }
            }
            for (Payment pay : payments) {
                if (pay.getStatus() != null && pay.getStatus() != PaymentStatus.PAID) continue;
                LocalDateTime payDate = pay.getPaymentDate() != null ? pay.getPaymentDate() : pay.getCreatedAt();
                if (payDate != null && payDate.isBefore(startDate)) {
                    s.openingBalance = s.openingBalance.subtract(nz(pay.getAmount()));
                }
            }
            for (CreditNote cn : creditNotes) {
                LocalDateTime cnDate = cn.getCreatedAt();
                if (cnDate != null && cnDate.isBefore(startDate)) {
                    s.openingBalance = s.openingBalance.subtract(nz(cn.getTotalAmount()));
                }
            }
        }

        // Opening balance row (only when a period was specified)
        if (startDate != null) {
            StatementLine opening = new StatementLine();
            opening.date = startDate.toLocalDate();
            opening.type = "OPENING";
            opening.reference = "";
            opening.description = "Opening balance";
            opening.debit = s.openingBalance.signum() >= 0 ? s.openingBalance : BigDecimal.ZERO;
            opening.credit = s.openingBalance.signum() < 0 ? s.openingBalance.abs() : BigDecimal.ZERO;
            opening.runningBalance = s.openingBalance;
            s.lines.add(opening);
        }

        // ─── Add in-period rows ────────────────────────────────────────
        for (Sale sale : sales) {
            if (sale.getStatus() == SaleStatus.CANCELLED) continue;
            LocalDateTime saleDate = sale.getDate();
            if (saleDate == null) continue;
            if (startDate != null && saleDate.isBefore(startDate)) continue;
            if (endDate != null && saleDate.isAfter(endDate)) continue;
            StatementLine line = new StatementLine();
            line.date = saleDate.toLocalDate();
            line.type = "INVOICE";
            line.reference = sale.getInvoiceNo() != null ? sale.getInvoiceNo() : ("INV-" + sale.getId());
            line.description = "Invoice " + line.reference;
            line.debit = nz(sale.getTotalAmount());
            line.credit = BigDecimal.ZERO;
            s.lines.add(line);
            s.totalInvoiced = s.totalInvoiced.add(line.debit);
        }
        for (Payment pay : payments) {
            if (pay.getStatus() != null && pay.getStatus() != PaymentStatus.PAID) continue;
            LocalDateTime payDate = pay.getPaymentDate() != null ? pay.getPaymentDate() : pay.getCreatedAt();
            if (payDate == null) continue;
            if (startDate != null && payDate.isBefore(startDate)) continue;
            if (endDate != null && payDate.isAfter(endDate)) continue;
            StatementLine line = new StatementLine();
            line.date = payDate.toLocalDate();
            line.type = "PAYMENT";
            line.reference = pay.getTransactionId() != null ? pay.getTransactionId() :
                            (pay.getReference() != null ? pay.getReference() : ("PMT-" + pay.getId()));
            String method = pay.getPaymentMethod() != null ? pay.getPaymentMethod().name() : "Payment";
            line.description = method + " received";
            line.debit = BigDecimal.ZERO;
            line.credit = nz(pay.getAmount());
            s.lines.add(line);
            s.totalPaid = s.totalPaid.add(line.credit);
        }
        for (CreditNote cn : creditNotes) {
            LocalDateTime cnDate = cn.getCreatedAt();
            if (cnDate == null) continue;
            if (startDate != null && cnDate.isBefore(startDate)) continue;
            if (endDate != null && cnDate.isAfter(endDate)) continue;
            StatementLine line = new StatementLine();
            line.date = cnDate.toLocalDate();
            line.type = "CREDIT_NOTE";
            line.reference = cn.getCreditNoteNo() != null ? cn.getCreditNoteNo() : ("CN-" + cn.getId());
            String reason = cn.getReason() != null ? " · " + cn.getReason() : "";
            line.description = "Credit note " + line.reference + reason;
            line.debit = BigDecimal.ZERO;
            line.credit = nz(cn.getTotalAmount());
            s.lines.add(line);
            s.totalCredits = s.totalCredits.add(line.credit);
        }
        // ─── CustomerLedger entries ────────────────────────────────────
        // CustomerLedgerService uses an INVERTED DEBIT/CREDIT convention:
        //   CREDIT type = receivable increases (customer owes more)
        //   DEBIT  type = receivable decreases (customer owes less)
        //
        // Category A: system-generated entries that duplicate saleRepo/paymentRepo data.
        // Including these would double-count every sale and payment. Skip them entirely.
        // Prefixes: "Sale #" (SaleService), "Cancelled Sale #" (SaleService),
        //           "Payment for " (PaymentServiceImpl), "Bulk Payment [" (PaymentServiceImpl),
        //           "Due Payment for Sale #" (PaymentServiceImpl).
        //
        // Category B + manual: ledger-only entries with no repo equivalent
        // ("Sales Return (Debt Cancel)", "Refund ", manual adjustments).
        // Keep these but invert direction to match standard statement convention:
        //   CL DEBIT  → statement CREDIT (reduces what customer owes)
        //   CL CREDIT → statement DEBIT  (increases what customer owes)
        for (CustomerLedger le : ledger) {
            LocalDateTime le_date = le.getCreatedAt();
            if (le_date == null) continue;
            if (startDate != null && le_date.isBefore(startDate)) continue;
            if (endDate != null && le_date.isAfter(endDate)) continue;

            String leDesc = le.getDescription();
            String desc = leDesc != null ? leDesc : "";
            if (desc.startsWith("Sale #")
                    || desc.startsWith("Cancelled Sale #")
                    || desc.startsWith("Payment for ")
                    || desc.startsWith("Bulk Payment [")
                    || desc.startsWith("Due Payment for Sale #")) continue;

            StatementLine line = new StatementLine();
            line.date = le_date.toLocalDate();
            // Invert: CL DEBIT → statement credit; CL CREDIT → statement debit
            boolean statementCredit = le.getType() == CustomerLedgerType.DEBIT;
            line.type = statementCredit ? "LEDGER_CREDIT" : "LEDGER_DEBIT";
            line.reference = "";
            line.description = desc.isEmpty() ? "Manual ledger entry" : desc;
            BigDecimal amt = nz(le.getAmount());
            line.debit  = statementCredit ? BigDecimal.ZERO : amt;
            line.credit = statementCredit ? amt : BigDecimal.ZERO;
            s.lines.add(line);
            if (statementCredit) s.totalCredits  = s.totalCredits.add(amt);
            else                  s.totalInvoiced = s.totalInvoiced.add(amt);
        }

        // ─── Chronological sort + running balance ──────────────────────
        s.lines.sort(Comparator.comparing((StatementLine l) -> l.date)
                .thenComparing(l -> l.type.equals("OPENING") ? 0 : 1));
        BigDecimal running = s.openingBalance;
        boolean firstIsOpening = !s.lines.isEmpty() && "OPENING".equals(s.lines.get(0).type);
        for (int i = 0; i < s.lines.size(); i++) {
            StatementLine l = s.lines.get(i);
            if (i == 0 && firstIsOpening) {
                l.runningBalance = running;
                continue;
            }
            running = running.add(nz(l.debit)).subtract(nz(l.credit));
            l.runningBalance = running;
        }
        s.closingBalance = running;

        // ─── Aging on OPEN invoices (regardless of the statement date
        // range — aging is always current-as-of-today). ─────────────────
        // Use due = totalAmount - paid so PARTIALLY_PAID invoices contribute
        // only their remaining balance, not the full invoice total.
        LocalDate today = LocalDate.now();
        Set<Long> agingOpenIds = sales.stream()
                .filter(sale -> sale.getStatus() != SaleStatus.CANCELLED
                        && (sale.getPaymentStatus() == PaymentStatus.PENDING
                            || sale.getPaymentStatus() == PaymentStatus.PARTIALLY_PAID))
                .map(Sale::getId)
                .collect(Collectors.toSet());
        Map<Long, BigDecimal> agingPaidById = new HashMap<>();
        if (!agingOpenIds.isEmpty()) {
            List<Object[]> rows = paymentRepo.sumPaymentsBySaleIds(agingOpenIds, PaymentSourceType.SALE);
            for (Object[] row : rows) {
                Long sid = ((Number) row[0]).longValue();
                BigDecimal paid = row[1] instanceof BigDecimal ? (BigDecimal) row[1]
                        : BigDecimal.valueOf(((Number) row[1]).doubleValue());
                agingPaidById.put(sid, paid);
            }
        }
        for (Sale sale : sales) {
            if (sale.getStatus() == SaleStatus.CANCELLED) continue;
            if (sale.getPaymentStatus() != PaymentStatus.PENDING && sale.getPaymentStatus() != PaymentStatus.PARTIALLY_PAID) continue;
            LocalDate invDate = sale.getDate() != null ? sale.getDate().toLocalDate() : today;
            long days = ChronoUnit.DAYS.between(invDate, today);
            BigDecimal paid = agingPaidById.getOrDefault(sale.getId(), BigDecimal.ZERO);
            BigDecimal due = nz(sale.getTotalAmount()).subtract(paid).max(BigDecimal.ZERO);
            if (days <= 30)      s.aging.current = s.aging.current.add(due);
            else if (days <= 60) s.aging.bucket31_60 = s.aging.bucket31_60.add(due);
            else if (days <= 90) s.aging.bucket61_90 = s.aging.bucket61_90.add(due);
            else                 s.aging.bucket90Plus = s.aging.bucket90Plus.add(due);
        }

        return s;
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
