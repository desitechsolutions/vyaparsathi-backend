package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.payroll.entity.*;
import com.desitech.vyaparsathi.payroll.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class PayrollAccountingBridge {

    @Autowired private PayrollSlipRepository payrollSlipRepository;

    public static class JournalEntry {
        public Long id;
        public LocalDate transactionDate;
        public String referenceNumber;
        public String description;
        public List<JournalEntryLine> lines = new ArrayList<>();
        public BigDecimal totalDebit = BigDecimal.ZERO;
        public BigDecimal totalCredit = BigDecimal.ZERO;
    }

    public static class JournalEntryLine {
        public String accountCode;
        public String accountName;
        public BigDecimal debit = BigDecimal.ZERO;
        public BigDecimal credit = BigDecimal.ZERO;
        public String costCenter;

        public JournalEntryLine(String code, String name, BigDecimal debit, BigDecimal credit) {
            this.accountCode = code;
            this.accountName = name;
            this.debit = debit;
            this.credit = credit;
        }
    }

    @Transactional
    public JournalEntry postPayrollToGeneralLedger(PayrollRun payrollRun, Long shopId) {
        List<PayrollSlip> slips = payrollSlipRepository.findByPayrollRunId(payrollRun.getId(), Pageable.unpaged()).getContent();

        JournalEntry entry = new JournalEntry();
        entry.transactionDate = LocalDate.now();
        entry.referenceNumber = "PAYROLL-" + payrollRun.getId() + "-" + System.currentTimeMillis();
        entry.description = "Monthly Payroll Run - " + payrollRun.getPayrollMonth() + "/" + payrollRun.getPayrollYear();

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalPf = BigDecimal.ZERO;
        BigDecimal totalEsi = BigDecimal.ZERO;
        BigDecimal totalPt = BigDecimal.ZERO;
        BigDecimal totalTds = BigDecimal.ZERO;
        BigDecimal totalEpf = BigDecimal.ZERO;
        BigDecimal totalEps = BigDecimal.ZERO;

        // Aggregate all slip data
        for (PayrollSlip slip : slips) {
            totalGross = totalGross.add(slip.getGrossEarnings());
            totalDeductions = totalDeductions.add(slip.getTotalDeductions());

            // Extract statutory components (would come from slip items in real impl)
            if (slip.getItems() != null) {
                for (PayrollSlipItem item : slip.getItems()) {
                    if (item.getComponentCode().contains("PF_EE")) totalPf = totalPf.add(item.getAmount());
                    if (item.getComponentCode().contains("PF_ER")) totalEpf = totalEpf.add(item.getAmount());
                    if (item.getComponentCode().contains("ESI_EE")) totalEsi = totalEsi.add(item.getAmount());
                    if (item.getComponentCode().contains("PT")) totalPt = totalPt.add(item.getAmount());
                    if (item.getComponentCode().contains("TDS")) totalTds = totalTds.add(item.getAmount());
                }
            }
        }

        // Debit: Salaries & Wages Expense
        entry.lines.add(new JournalEntryLine(
            "6100",
            "Salaries & Wages Expense",
            totalGross,
            BigDecimal.ZERO
        ));

        // Debit: Employer PF Contribution
        if (totalEpf.compareTo(BigDecimal.ZERO) > 0) {
            entry.lines.add(new JournalEntryLine(
                "6110",
                "Employer PF Contribution",
                totalEpf,
                BigDecimal.ZERO
            ));
        }

        // Debit: Employer ESI Contribution
        if (totalEsi.compareTo(BigDecimal.ZERO) > 0) {
            entry.lines.add(new JournalEntryLine(
                "6120",
                "Employer ESI Contribution",
                totalEsi,
                BigDecimal.ZERO
            ));
        }

        // Credit: PF Payable (EE + ER)
        if (totalPf.compareTo(BigDecimal.ZERO) > 0) {
            entry.lines.add(new JournalEntryLine(
                "2050",
                "PF Payable",
                BigDecimal.ZERO,
                totalPf.add(totalEpf)
            ));
        }

        // Credit: ESI Payable
        if (totalEsi.compareTo(BigDecimal.ZERO) > 0) {
            entry.lines.add(new JournalEntryLine(
                "2060",
                "ESI Payable",
                BigDecimal.ZERO,
                totalEsi
            ));
        }

        // Credit: Professional Tax Payable
        if (totalPt.compareTo(BigDecimal.ZERO) > 0) {
            entry.lines.add(new JournalEntryLine(
                "2070",
                "Professional Tax Payable",
                BigDecimal.ZERO,
                totalPt
            ));
        }

        // Credit: TDS Payable
        if (totalTds.compareTo(BigDecimal.ZERO) > 0) {
            entry.lines.add(new JournalEntryLine(
                "2080",
                "TDS Payable",
                BigDecimal.ZERO,
                totalTds
            ));
        }

        // Credit: Bank Account (net cash outflow)
        BigDecimal netPayable = totalGross.subtract(totalDeductions);
        entry.lines.add(new JournalEntryLine(
            "1010",
            "Bank Account",
            BigDecimal.ZERO,
            netPayable
        ));

        // Calculate totals
        for (JournalEntryLine line : entry.lines) {
            entry.totalDebit = entry.totalDebit.add(line.debit);
            entry.totalCredit = entry.totalCredit.add(line.credit);
        }

        // Verify balance
        if (entry.totalDebit.compareTo(entry.totalCredit) != 0) {
            throw new RuntimeException("Journal entry not balanced: Debit=" + entry.totalDebit + ", Credit=" + entry.totalCredit);
        }

        return entry;
    }
}
