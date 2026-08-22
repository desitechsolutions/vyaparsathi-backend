package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.payroll.entity.*;
import com.desitech.vyaparsathi.payroll.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BankingIntegrationService {

    @Autowired private StatutoryConfigRepository configRepository;
    @Autowired private BankTransactionRepository bankTransactionRepository;
    @Autowired private PayrollSlipRepository payrollSlipRepository;
    @Autowired private EmployeeRepository employeeRepository;

    // Mock RazorpayX API call (real implementation would use RazorpayX SDK)
    @Transactional
    public BankingResult disburseViaRazorpayX(PayrollRun payrollRun, Long shopId) {
        BankingResult result = new BankingResult();
        List<PayrollSlip> slips = payrollSlipRepository.findByPayrollRunId(payrollRun.getId(), Pageable.unpaged()).getContent();

        StatutoryConfig config = configRepository.findByShopId(shopId).orElse(null);
        if (config == null || config.getRazorpayxApiKey() == null) {
            result.setSuccess(false);
            result.setErrorMessage("RazorpayX not configured");
            return result;
        }

        List<BankTransaction> transactions = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (PayrollSlip slip : slips) {
            Employee employee = slip.getEmployee();
            if (employee.getBankAccountNumber() == null) {
                result.addFailure(employee.getFirstName() + " " + (employee.getLastName() != null ? employee.getLastName() : ""), "No bank account configured");
                continue;
            }

            // Create transaction record
            BankTransaction transaction = new BankTransaction();
            transaction.setShop(employee.getShop());
            transaction.setPayrollRun(payrollRun);
            transaction.setEmployee(employee);
            transaction.setTransactionType("SALARY");
            transaction.setAmount(slip.getNetSalary());
            transaction.setPaymentMethod("RAZORPAYX");
            transaction.setStatus("INITIATED");
            transaction.setInitiatedAt(LocalDate.now());

            // Mock API call to RazorpayX
            String utr = "UTR" + System.currentTimeMillis();
            transaction.setUtrNumber(utr);
            transaction.setStatus("COMPLETED");
            transaction.setCompletedAt(LocalDate.now());
            transaction.setBankResponseCode("0");
            transaction.setBankResponseMessage("SUCCESS");

            bankTransactionRepository.save(transaction);
            transactions.add(transaction);
            totalAmount = totalAmount.add(slip.getNetSalary());

            result.addSuccess(employee.getFirstName() + " " + (employee.getLastName() != null ? employee.getLastName() : ""), slip.getNetSalary(), utr);
        }

        result.setSuccess(true);
        result.setTotalAmount(totalAmount);
        result.setTransactionCount(transactions.size());
        return result;
    }

    @Transactional
    public String generateNEFTBatchFile(PayrollRun payrollRun, Long shopId) {
        StatutoryConfig config = configRepository.findByShopId(shopId).orElse(null);
        List<PayrollSlip> slips = payrollSlipRepository.findByPayrollRunId(payrollRun.getId(), Pageable.unpaged()).getContent();

        StringBuilder batch = new StringBuilder();
        batch.append("HEAD,").append(config.getBankAccountNumber()).append(",")
                .append(config.getBankIfsc()).append(",")
                .append(LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMuuuu"))).append("\n");

        int recordCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (PayrollSlip slip : slips) {
            Employee emp = slip.getEmployee();
            if (emp.getBankAccountNumber() == null) continue;

            recordCount++;
            totalAmount = totalAmount.add(slip.getNetSalary());

            batch.append("NEFT,")
                    .append(String.format("%010d", recordCount)).append(",")
                    .append(emp.getBankAccountNumber()).append(",")
                    .append(emp.getBankIFSCCode()).append(",")
                    .append(emp.getFirstName() + " " + (emp.getLastName() != null ? emp.getLastName() : "")).append(",")
                    .append(slip.getNetSalary().setScale(2)).append(",")
                    .append("Salary-").append(payrollRun.getPayrollMonth()).append("-").append(payrollRun.getPayrollYear()).append("\n");
        }

        batch.append("TAIL,").append(recordCount).append(",").append(totalAmount.setScale(2)).append("\n");
        return batch.toString();
    }

    @Transactional
    public String generateNACHBatchFile(PayrollRun payrollRun, Long shopId) {
        StatutoryConfig config = configRepository.findByShopId(shopId).orElse(null);
        List<PayrollSlip> slips = payrollSlipRepository.findByPayrollRunId(payrollRun.getId(), Pageable.unpaged()).getContent();

        StringBuilder batch = new StringBuilder();
        batch.append("101").append("NACH0CITI0000123456").append(String.format("%-40s", "COMPANY NAME"))
                .append(config.getBankIfsc()).append("\n");

        int recordCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (PayrollSlip slip : slips) {
            Employee emp = slip.getEmployee();
            if (emp.getBankAccountNumber() == null || emp.getUpiId() == null) continue;

            recordCount++;
            totalAmount = totalAmount.add(slip.getNetSalary());

            batch.append("110").append(String.format("%010d", recordCount))
                    .append(emp.getUpiId()).append(emp.getBankAccountNumber())
                    .append(String.format("%012.2f", slip.getNetSalary().doubleValue())).append("\n");
        }

        batch.append("900").append(String.format("%010d", recordCount))
                .append(String.format("%012.2f", totalAmount.doubleValue())).append("\n");

        return batch.toString();
    }

    @Transactional
    public String generateECRFile(PayrollRun payrollRun, Long shopId) {
        StatutoryConfig config = configRepository.findByShopId(shopId).orElse(null);
        List<PayrollSlip> slips = payrollSlipRepository.findByPayrollRunId(payrollRun.getId(), Pageable.unpaged()).getContent();

        StringBuilder ecr = new StringBuilder();
        ecr.append("ESTABLISHMENT,").append(config.getPfUan()).append(",")
                .append(LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMuuuu"))).append("\n");

        BigDecimal totalEEContribution = BigDecimal.ZERO;
        BigDecimal totalERContribution = BigDecimal.ZERO;
        int employeeCount = 0;

        for (PayrollSlip slip : slips) {
            Employee emp = slip.getEmployee();
            if (!Boolean.TRUE.equals(emp.getPfEnrolled())) continue;

            employeeCount++;
            // Sum from payroll slip items (would need to add statutory breakdown)
            // For now, mock values
            totalEEContribution = totalEEContribution.add(slip.getGrossEarnings().multiply(new java.math.BigDecimal("0.12")));
            totalERContribution = totalERContribution.add(slip.getGrossEarnings().multiply(new java.math.BigDecimal("0.12")));

            ecr.append("MEMBER,").append(emp.getPanNumber()).append(",")
                    .append(emp.getFirstName() + " " + (emp.getLastName() != null ? emp.getLastName() : "")).append(",")
                    .append(slip.getGrossEarnings().setScale(2)).append("\n");
        }

        ecr.append("SUMMARY,").append(employeeCount).append(",")
                .append(totalEEContribution.setScale(2)).append(",")
                .append(totalERContribution.setScale(2)).append("\n");

        return ecr.toString();
    }

    // Bank account validation (mock)
    public BankAccountValidation validateBankDetails(Employee employee) {
        BankAccountValidation validation = new BankAccountValidation();

        if (employee.getBankAccountNumber() == null || employee.getBankAccountNumber().isEmpty()) {
            validation.setValid(false);
            validation.setError("Bank account number not configured");
            return validation;
        }

        if (employee.getBankIFSCCode() == null || employee.getBankIFSCCode().length() != 11) {
            validation.setValid(false);
            validation.setError("Invalid IFSC code");
            return validation;
        }

        // Mock IFSC validation
        validation.setValid(true);
        validation.setBankName("Test Bank");
        validation.setBranchName("Test Branch");
        return validation;
    }

    // Result DTOs
    public static class BankingResult {
        private boolean success;
        private String errorMessage;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private int transactionCount = 0;
        private List<TransactionDetail> successfulTransactions = new ArrayList<>();
        private List<TransactionFailure> failedTransactions = new ArrayList<>();

        public void addSuccess(String employeeName, BigDecimal amount, String utr) {
            successfulTransactions.add(new TransactionDetail(employeeName, amount, utr));
        }

        public void addFailure(String employeeName, String reason) {
            failedTransactions.add(new TransactionFailure(employeeName, reason));
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean v) { this.success = v; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String v) { this.errorMessage = v; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal v) { this.totalAmount = v; }
        public int getTransactionCount() { return transactionCount; }
        public void setTransactionCount(int v) { this.transactionCount = v; }
        public List<TransactionDetail> getSuccessfulTransactions() { return successfulTransactions; }
        public List<TransactionFailure> getFailedTransactions() { return failedTransactions; }

        public static class TransactionDetail {
            public String employeeName;
            public BigDecimal amount;
            public String utr;
            public TransactionDetail(String name, BigDecimal amt, String ref) {
                this.employeeName = name; this.amount = amt; this.utr = ref;
            }
        }

        public static class TransactionFailure {
            public String employeeName;
            public String reason;
            public TransactionFailure(String name, String reason) {
                this.employeeName = name; this.reason = reason;
            }
        }
    }

    public static class BankAccountValidation {
        private boolean valid;
        private String error;
        private String bankName;
        private String branchName;

        public boolean isValid() { return valid; }
        public void setValid(boolean v) { this.valid = v; }
        public String getError() { return error; }
        public void setError(String v) { this.error = v; }
        public String getBankName() { return bankName; }
        public void setBankName(String v) { this.bankName = v; }
        public String getBranchName() { return branchName; }
        public void setBranchName(String v) { this.branchName = v; }
    }
}
