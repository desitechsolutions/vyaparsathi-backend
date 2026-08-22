package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.audit.helper.AuditHelper;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class PayrollAuditService {

    private static final Logger logger = LoggerFactory.getLogger(PayrollAuditService.class);

    @Autowired private AuditHelper auditHelper;

    // Audit payroll run creation
    public void auditPayrollRunCreation(Long payrollRunId, String month, Integer year) {
        Map<String, Object> details = new HashMap<>();
        details.put("payrollRunId", payrollRunId);
        details.put("month", month);
        details.put("year", year);
        details.put("timestamp", LocalDateTime.now());

        auditHelper.log("PAYROLL_RUN", "CREATE", payrollRunId.toString(), details.toString());
        logger.info("Payroll run created: {}", payrollRunId);
    }

    // Audit payroll processing
    public void auditPayrollProcessing(Long payrollRunId, Integer employeeCount, String status) {
        Map<String, Object> details = new HashMap<>();
        details.put("payrollRunId", payrollRunId);
        details.put("employeeCount", employeeCount);
        details.put("newStatus", status);
        details.put("timestamp", LocalDateTime.now());

        auditHelper.log("PAYROLL_RUN", "PROCESS", payrollRunId.toString(), details.toString());
        logger.info("Payroll run {} processing completed for {} employees. Status: {}",
                   payrollRunId, employeeCount, status);
    }

    // Audit payroll approval
    public void auditPayrollApproval(Long payrollRunId, Long approverId, String remarks) {
        Map<String, Object> details = new HashMap<>();
        details.put("payrollRunId", payrollRunId);
        details.put("approverId", approverId);
        details.put("remarks", remarks);
        details.put("timestamp", LocalDateTime.now());

        auditHelper.log("PAYROLL_RUN", "APPROVE", payrollRunId.toString(), details.toString());
        logger.info("Payroll run {} approved by user {}. Remarks: {}",
                   payrollRunId, approverId, remarks);
    }

    // Audit payroll disbursal
    public void auditPayrollDisbursal(Long payrollRunId, Long disbursalUserId, String mode) {
        Map<String, Object> details = new HashMap<>();
        details.put("payrollRunId", payrollRunId);
        details.put("disbursalUserId", disbursalUserId);
        details.put("disbursalMode", mode);
        details.put("timestamp", LocalDateTime.now());

        auditHelper.log("PAYROLL_RUN", "DISBURSE", payrollRunId.toString(), details.toString());
        logger.info("Payroll run {} disbursed by user {} via {}. Shop ID: {}",
                   payrollRunId, disbursalUserId, mode, TenantContext.getCurrentShopId());
    }

    // Audit payslip creation
    public void auditPayslipCreation(Long slipId, Long employeeId, Long payrollRunId) {
        Map<String, Object> details = new HashMap<>();
        details.put("slipId", slipId);
        details.put("employeeId", employeeId);
        details.put("payrollRunId", payrollRunId);
        details.put("timestamp", LocalDateTime.now());

        auditHelper.log("PAYROLL_SLIP", "CREATE", slipId.toString(), details.toString());
        logger.info("Payslip {} created for employee {}. Run: {}",
                   slipId, employeeId, payrollRunId);
    }

    // Audit statutory compliance operations
    public void auditStatutoryDeduction(Long slipId, String deductionType, String amount) {
        Map<String, Object> details = new HashMap<>();
        details.put("slipId", slipId);
        details.put("deductionType", deductionType);
        details.put("amount", amount);
        details.put("timestamp", LocalDateTime.now());

        auditHelper.log("STATUTORY", "CALCULATE", slipId.toString(), details.toString());
        logger.info("Statutory deduction {} (₹{}) calculated for payslip {}",
                   deductionType, amount, slipId);
    }

    // Audit banking operations
    public void auditBankingOperation(String operationType, Long payrollRunId, String status) {
        Map<String, Object> details = new HashMap<>();
        details.put("operationType", operationType);
        details.put("payrollRunId", payrollRunId);
        details.put("status", status);
        details.put("timestamp", LocalDateTime.now());

        auditHelper.log("BANKING", operationType, payrollRunId.toString(), details.toString());
        logger.info("Banking operation {} for payroll run {} completed with status: {}",
                   operationType, payrollRunId, status);
    }

    // Audit payslip dispatch
    public void auditPayslipDispatch(Long slipId, String channel, String status) {
        Map<String, Object> details = new HashMap<>();
        details.put("slipId", slipId);
        details.put("dispatchChannel", channel);
        details.put("dispatchStatus", status);
        details.put("timestamp", LocalDateTime.now());

        auditHelper.log("PAYSLIP_DISPATCH", "SEND", slipId.toString(), details.toString());
        logger.info("Payslip {} dispatched via {} with status: {}",
                   slipId, channel, status);
    }

    // Audit error/failure scenarios
    public void auditPayrollError(String operation, Long entityId, String errorMessage) {
        Map<String, Object> details = new HashMap<>();
        details.put("operation", operation);
        details.put("entityId", entityId);
        details.put("errorMessage", errorMessage);
        details.put("timestamp", LocalDateTime.now());

        auditHelper.log("PAYROLL_ERROR", operation, entityId.toString(), details.toString());
        logger.error("Payroll error in operation {} for entity {}: {}",
                    operation, entityId, errorMessage);
    }

    // Audit reconciliation
    public void auditReconciliation(Long payrollRunId, int matched, int unmatched) {
        Map<String, Object> details = new HashMap<>();
        details.put("payrollRunId", payrollRunId);
        details.put("matchedTransactions", matched);
        details.put("unmatchedTransactions", unmatched);
        details.put("timestamp", LocalDateTime.now());

        auditHelper.log("BANK_RECONCILIATION", "RECONCILE", payrollRunId.toString(), details.toString());
        logger.info("Bank reconciliation for payroll run {}: {} matched, {} unmatched",
                   payrollRunId, matched, unmatched);
    }
}
