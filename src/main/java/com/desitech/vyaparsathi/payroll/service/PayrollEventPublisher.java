package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.payroll.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PayrollEventPublisher {

    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private PayslipDispatchService dispatchService;
    @Autowired private PayrollService payrollService;

    @Value("${gl.posting.enabled:true}")
    private boolean glPostingEnabled;

    @Value("${form16.auto.generate.on.payroll.complete:false}")
    private boolean autoGenerateForm16;

    /**
     * Called when payroll run status changes to DISBURSED
     */
    @Async
    public void onPayrollDisbursed(PayrollRun payrollRun, Long shopId) {
        log.info("Payroll run {} disbursed, triggering post-disbursal events", payrollRun.getId());

        try {
            // 1. Post to General Ledger
            if (glPostingEnabled) {
                log.debug("Posting payroll {} to GL", payrollRun.getId());
                payrollService.postPayrollToGL(payrollRun.getId());
            }

            // 2. Dispatch payslips (async)
            log.debug("Dispatching payslips for run {}", payrollRun.getId());
            dispatchService.dispatchPayslipsForRun(payrollRun.getId());

            // 3. Auto-generate Form16 if configured
            if (autoGenerateForm16) {
                log.debug("Auto-generating Form16 for payroll {}", payrollRun.getId());
                payrollService.generateForm16ForAllEmployees(
                    payrollRun.getPayrollYear() + "-" + payrollRun.getPayrollMonth()
                );
            }

            log.info("Post-disbursal events completed for payroll run {}", payrollRun.getId());
        } catch (Exception e) {
            log.error("Error processing post-disbursal events for payroll run {}", payrollRun.getId(), e);
            // Don't re-throw to prevent rollback of the disbursal itself
        }
    }

    /**
     * Called when approval is requested
     */
    public void onPayrollApprovalRequested(PayrollRun payrollRun) {
        log.info("Payroll run {} approval requested", payrollRun.getId());
        // Can send notification to approver here
    }

    /**
     * Called when advance request is approved
     */
    @Async
    public void onAdvanceRequestApproved(AdvanceRequest request) {
        log.info("Advance request {} approved", request.getId());
        // Schedule automatic deduction in upcoming payrolls
        // Can trigger notification to employee
    }

    /**
     * Called when payslip is generated
     */
    @Async
    public void onPayslipGenerated(PayrollSlip slip) {
        log.info("Payslip {} generated for employee {}", slip.getId(), slip.getEmployee().getId());
        // Trigger optional auto-dispatch if configured
    }
}
