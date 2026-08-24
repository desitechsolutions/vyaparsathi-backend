package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.payroll.entity.*;
import com.desitech.vyaparsathi.payroll.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Payroll Accounting Bridge — generates and PERSISTS double-entry GL journal entries.
 *
 * Each payroll run creates exactly one balanced journal entry:
 *   DEBITS:  Salaries Expense, Employer PF Contribution, Employer ESI Contribution
 *   CREDITS: PF Payable, ESI Payable, PT Payable, TDS Payable, Bank Account (net cash)
 *
 * Fixed: JournalEntry now persisted to payroll_journal_entries table (was in-memory only).
 * Fixed: Employer ESI debit now uses actual employer rate (3.25%), not employee rate (0.75%).
 */
@Service
public class PayrollAccountingBridge {

    private static final Logger log = LoggerFactory.getLogger(PayrollAccountingBridge.class);

    // Standard GL account codes (fallback defaults)
    private static final String ACC_SALARY_EXPENSE     = "6100";
    private static final String ACC_EMPLOYER_PF_EXPENSE= "6110";
    private static final String ACC_EMPLOYER_ESI_EXPENSE= "6120";
    private static final String ACC_PF_PAYABLE         = "2050";
    private static final String ACC_ESI_PAYABLE        = "2060";
    private static final String ACC_PT_PAYABLE         = "2070";
    private static final String ACC_TDS_PAYABLE        = "2080";
    private static final String ACC_BANK               = "1010";

    // Statutory rates for employer contributions
    private static final BigDecimal ESI_EMPLOYER_RATE = new BigDecimal("3.25");
    private static final BigDecimal ESI_EMPLOYEE_RATE = new BigDecimal("0.75");

    @Autowired private PayrollSlipRepository payrollSlipRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;

    /**
     * Post payroll run to General Ledger.
     * Creates and PERSISTS a balanced double-entry journal.
     *
     * @return the persisted JournalEntryEntity
     * @throws IllegalStateException if journal entry is not balanced or already posted
     */
    @Transactional
    public JournalEntryEntity postPayrollToGeneralLedger(PayrollRun payrollRun, Long shopId) {
        return postPayrollToGeneralLedger(payrollRun, shopId, null);
    }

    @Transactional
    public JournalEntryEntity postPayrollToGeneralLedger(PayrollRun payrollRun, Long shopId, Long postedByUserId) {
        // Idempotency check — prevent double-posting
        if (journalEntryRepository.existsByPayrollRunId(payrollRun.getId())) {
            log.warn("Journal entry already exists for payroll run {}. Skipping duplicate GL posting.", payrollRun.getId());
            return journalEntryRepository.findByPayrollRunId(payrollRun.getId()).orElseThrow();
        }

        List<PayrollSlip> slips = payrollSlipRepository
                .findByPayrollRunId(payrollRun.getId(), Pageable.unpaged()).getContent();

        // ── Aggregate all slip data ──────────────────────────────────────────
        BigDecimal totalGross     = BigDecimal.ZERO;
        BigDecimal totalNetPay    = BigDecimal.ZERO;
        BigDecimal totalPfEE      = BigDecimal.ZERO;   // Employee PF deduction
        BigDecimal totalPfER      = BigDecimal.ZERO;   // Employer PF contribution
        BigDecimal totalEsiEE     = BigDecimal.ZERO;   // Employee ESI deduction
        BigDecimal totalEsiER     = BigDecimal.ZERO;   // Employer ESI contribution (3.25%)
        BigDecimal totalPt        = BigDecimal.ZERO;   // Professional Tax
        BigDecimal totalTds       = BigDecimal.ZERO;   // TDS

        for (PayrollSlip slip : slips) {
            totalGross  = totalGross.add(slip.getGrossEarnings());
            totalNetPay = totalNetPay.add(slip.getNetSalary());
            totalPfEE   = totalPfEE.add(nvl(slip.getEpfEmployee()));
            totalPfER   = totalPfER.add(nvl(slip.getEpfEmployer()));
            totalEsiEE  = totalEsiEE.add(nvl(slip.getEsiEmployee()));

            // Fix: Employer ESI is 3.25% of ESI wage, not the same as employee ESI
            // Compute from employee ESI (0.75%) by ratio: erESI = eeESI * (3.25/0.75)
            if (nvl(slip.getEsiEmployee()).compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal erEsi = nvl(slip.getEsiEmployee())
                        .multiply(ESI_EMPLOYER_RATE)
                        .divide(ESI_EMPLOYEE_RATE, 2, RoundingMode.HALF_UP);
                totalEsiER = totalEsiER.add(erEsi);
            } else {
                totalEsiER = totalEsiER.add(nvl(slip.getEsiEmployer()));
            }

            totalPt     = totalPt.add(nvl(slip.getProfessionalTax()));
            totalTds    = totalTds.add(nvl(slip.getTdsTax()));
        }

        // ── Build double-entry journal ───────────────────────────────────────
        JournalEntryEntity entry = new JournalEntryEntity();
        entry.setShopId(shopId);
        entry.setPayrollRunId(payrollRun.getId());
        entry.setTransactionDate(LocalDate.now());
        entry.setReferenceNumber("PAYROLL-" + payrollRun.getRunNumber() + "-GL");
        entry.setDescription("Monthly Payroll GL Posting — " + payrollRun.getPayrollMonth()
                + "/" + payrollRun.getPayrollYear() + " (" + slips.size() + " employees)");
        entry.setPostedAt(LocalDateTime.now());
        entry.setPostedByUserId(postedByUserId);

        List<JournalEntryLineEntity> lines = new ArrayList<>();

        // ── DEBITS ──────────────────────────────────────────────────────────

        // DR: Salaries & Wages Expense (full gross)
        lines.add(buildLine(entry, ACC_SALARY_EXPENSE, "Salaries & Wages Expense",
                totalGross, BigDecimal.ZERO, "PAYROLL"));

        // DR: Employer PF Contribution Expense
        if (totalPfER.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(buildLine(entry, ACC_EMPLOYER_PF_EXPENSE, "Employer PF Contribution Expense",
                    totalPfER, BigDecimal.ZERO, "PAYROLL"));
        }

        // DR: Employer ESI Contribution Expense (3.25% rate — correctly computed)
        if (totalEsiER.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(buildLine(entry, ACC_EMPLOYER_ESI_EXPENSE, "Employer ESI Contribution Expense",
                    totalEsiER, BigDecimal.ZERO, "PAYROLL"));
        }

        // ── CREDITS ─────────────────────────────────────────────────────────

        // CR: PF Payable (EE + ER combined — to be remitted to EPFO)
        BigDecimal totalPfPayable = totalPfEE.add(totalPfER);
        if (totalPfPayable.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(buildLine(entry, ACC_PF_PAYABLE, "PF Payable (EE+ER)",
                    BigDecimal.ZERO, totalPfPayable, "STATUTORY"));
        }

        // CR: ESI Payable (EE + ER combined — to be remitted to ESIC)
        BigDecimal totalEsiPayable = totalEsiEE.add(totalEsiER);
        if (totalEsiPayable.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(buildLine(entry, ACC_ESI_PAYABLE, "ESI Payable (EE+ER)",
                    BigDecimal.ZERO, totalEsiPayable, "STATUTORY"));
        }

        // CR: Professional Tax Payable
        if (totalPt.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(buildLine(entry, ACC_PT_PAYABLE, "Professional Tax Payable",
                    BigDecimal.ZERO, totalPt, "STATUTORY"));
        }

        // CR: TDS Payable
        if (totalTds.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(buildLine(entry, ACC_TDS_PAYABLE, "TDS Payable (Sec 192)",
                    BigDecimal.ZERO, totalTds, "STATUTORY"));
        }

        // CR: Bank Account — net cash disbursed
        lines.add(buildLine(entry, ACC_BANK, "Bank Account — Net Salary Disbursement",
                BigDecimal.ZERO, totalNetPay, "BANK"));

        // ── Compute totals & verify balance ─────────────────────────────────
        BigDecimal sumDebit  = lines.stream().map(JournalEntryLineEntity::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumCredit = lines.stream().map(JournalEntryLineEntity::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sumDebit.compareTo(sumCredit) != 0) {
            // Attempt to auto-correct rounding by adding a tiny adjustment to bank credit
            BigDecimal diff = sumDebit.subtract(sumCredit);
            if (diff.abs().compareTo(new BigDecimal("1.00")) <= 0) {
                // Acceptable rounding difference — adjust bank line
                for (JournalEntryLineEntity l : lines) {
                    if (ACC_BANK.equals(l.getAccountCode())) {
                        l.setCredit(l.getCredit().add(diff));
                        break;
                    }
                }
                sumCredit = sumDebit; // now balanced
            } else {
                throw new IllegalStateException(
                        "Journal entry NOT balanced for payroll run " + payrollRun.getId() +
                        ": Debit=" + sumDebit + ", Credit=" + sumCredit);
            }
        }

        entry.setTotalDebit(sumDebit);
        entry.setTotalCredit(sumCredit);
        entry.setIsBalanced(true);
        entry.setLines(lines);

        // ── PERSIST to database ──────────────────────────────────────────────
        JournalEntryEntity saved = journalEntryRepository.save(entry);
        log.info("GL journal entry {} posted for payroll run {} — Total Debit/Credit: {}",
                saved.getReferenceNumber(), payrollRun.getId(), sumDebit);

        return saved;
    }

    private JournalEntryLineEntity buildLine(JournalEntryEntity entry, String code, String name,
                                              BigDecimal debit, BigDecimal credit, String costCenter) {
        JournalEntryLineEntity line = new JournalEntryLineEntity();
        line.setJournalEntry(entry);
        line.setAccountCode(code);
        line.setAccountName(name);
        line.setDebit(debit);
        line.setCredit(credit);
        line.setCostCenter(costCenter);
        return line;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /** Backward-compatible DTO returned to callers that haven't migrated to entity. */
    public record GlPostingResult(Long journalEntryId, String referenceNumber,
                                  BigDecimal totalDebit, BigDecimal totalCredit, boolean balanced) {}
}
