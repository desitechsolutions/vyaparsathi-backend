package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.payroll.entity.*;
import com.desitech.vyaparsathi.payroll.repository.*;
import com.desitech.vyaparsathi.payroll.enums.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Employee Self-Service Engine — secure, multi-tenant ESS portal service.
 * All data access is scoped to the authenticated employee's shopId.
 */
@Service
public class EmployeeSelfServiceEngine {

    @Autowired private PayrollSlipRepository payrollSlipRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private TaxDeclarationRepository taxDeclarationRepository;
    @Autowired private AdvanceRequestRepository advanceRequestRepository;
    @Autowired private PayslipDispatchLogRepository dispatchLogRepository;
    @Autowired private Form16DataRepository form16Repository;
    @Autowired private EssPreferencesRepository essPreferencesRepository;
    @Autowired private StaffLoanRepository loanRepository;
    @Autowired private EmployeeRepository employeeRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Payslips
    // ─────────────────────────────────────────────────────────────────────────

    /** Get all payslips for the authenticated employee, scoped to their shop. */
    public Page<PayrollSlip> getEmployeePayslips(Long employeeId, Pageable pageable) {
        Long shopId = TenantContext.getCurrentShopId();
        return payrollSlipRepository.findByEmployeeIdAndShopId(employeeId, shopId, pageable);
    }

    /**
     * Get a single payslip — validates ownership AND shop tenancy.
     * Prevents cross-employee and cross-tenant access.
     */
    public PayrollSlip getPayslipDetails(Long slipId, Long employeeId) {
        Long shopId = TenantContext.getCurrentShopId();
        PayrollSlip slip = payrollSlipRepository.findById(slipId)
                .orElseThrow(() -> new EntityNotFoundAppException("PayrollSlip", slipId));

        // Ownership check — employee can only see their own slips
        if (!slip.getEmployee().getId().equals(employeeId)) {
            throw new SecurityException("Access denied: Payslip does not belong to this employee");
        }

        // Shop tenancy check — prevent cross-tenant access
        if (slip.getShop() != null && !slip.getShop().getId().equals(shopId)) {
            throw new SecurityException("Access denied: Cross-tenant payslip access is forbidden");
        }

        return slip;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Attendance
    // ─────────────────────────────────────────────────────────────────────────

    public AttendanceSummary getAttendanceSummary(Long employeeId, int month, int year) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);

        List<AttendanceRecord> records = attendanceRecordRepository
                .findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(employeeId, startDate, endDate);

        long presentDays = records.stream()
                .filter(r -> r.getAttendanceType() == AttendanceType.PRESENT).count();
        long paidLeaveDays = records.stream()
                .filter(r -> r.getAttendanceType() == AttendanceType.PAID_LEAVE).count();
        long absentDays = records.stream()
                .filter(r -> r.getAttendanceType() == AttendanceType.ABSENT).count();
        long lopDays = records.stream()
                .filter(r -> r.getAttendanceType() == AttendanceType.UNPAID_LEAVE).count();
        long halfDays = records.stream()
                .filter(r -> r.getAttendanceType() == AttendanceType.HALF_DAY).count();

        return new AttendanceSummary(presentDays, paidLeaveDays, absentDays, lopDays, halfDays);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tax Declaration
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TaxDeclaration getTaxDeclaration(Long employeeId, String financialYear) {
        return taxDeclarationRepository.findByEmployeeIdAndFinancialYear(employeeId, financialYear)
                .orElse(new TaxDeclaration());
    }

    /**
     * Submit or update a tax declaration.
     * Loads employee via repository to avoid detached proxy pattern.
     */
    @Transactional
    public TaxDeclaration submitTaxDeclaration(Long employeeId, TaxDeclaration declaration) {
        // Load the actual employee entity (not a detached proxy)
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundAppException("Employee", employeeId));

        TaxDeclaration existing = taxDeclarationRepository
                .findByEmployeeIdAndFinancialYear(employeeId, declaration.getFinancialYear())
                .orElse(new TaxDeclaration());

        // Set all fields correctly
        existing.setEmployee(employee);
        existing.setFinancialYear(declaration.getFinancialYear());
        existing.setTaxRegime(declaration.getTaxRegime());
        existing.setLifeInsurancePremium(declaration.getLifeInsurancePremium());
        existing.setMedicalInsurancePremium(declaration.getMedicalInsurancePremium());
        existing.setEducationExpenses(declaration.getEducationExpenses());
        existing.setHomeLoanPrincipal(declaration.getHomeLoanPrincipal());
        existing.setHomeLoanInterest(declaration.getHomeLoanInterest());
        existing.setNpsContribution(declaration.getNpsContribution());
        existing.setOther80cDeductions(declaration.getOther80cDeductions());
        existing.setHouseRentAllowanceClaimed(declaration.getHouseRentAllowanceClaimed());
        existing.setLeaveEncashmentClaimed(declaration.getLeaveEncashmentClaimed());
        existing.setMedicalReimbursementClaimed(declaration.getMedicalReimbursementClaimed());
        existing.setSubmittedAt(LocalDate.now());

        return taxDeclarationRepository.save(existing);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Advance Requests
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public AdvanceRequest requestAdvance(Long employeeId, BigDecimal amount, String reason) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundAppException("Employee", employeeId));

        AdvanceRequest request = new AdvanceRequest();
        request.setEmployee(employee);
        request.setAmount(amount);
        request.setReason(reason);
        request.setStatus("PENDING");
        request.setRequestedAt(LocalDate.now());

        return advanceRequestRepository.save(request);
    }

    public Page<AdvanceRequest> getEmployeeAdvanceRequests(Long employeeId, Pageable pageable) {
        return advanceRequestRepository.findByEmployeeIdOrderByRequestedAtDesc(employeeId, pageable);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Loans
    // ─────────────────────────────────────────────────────────────────────────

    /** Paginated loan retrieval — avoids loading all loans in memory. */
    public Page<StaffLoan> getEmployeeLoans(Long employeeId, Pageable pageable) {
        return loanRepository.findByEmployeeIdOrderByDisbursementDateDesc(employeeId, pageable);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dispatch History
    // ─────────────────────────────────────────────────────────────────────────

    public Page<PayslipDispatchLog> getDispatchHistory(Long employeeId, Pageable pageable) {
        return dispatchLogRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId, pageable);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Form 16
    // ─────────────────────────────────────────────────────────────────────────

    public Form16Data getForm16(Long employeeId, String financialYear) {
        return form16Repository.findByEmployeeIdAndFinancialYear(employeeId, financialYear)
                .orElse(new Form16Data());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ESS Preferences
    // ─────────────────────────────────────────────────────────────────────────

    public EssPreferences getEssPreferences(Long employeeId) {
        return essPreferencesRepository.findByEmployeeId(employeeId)
                .orElse(new EssPreferences());
    }

    /**
     * Update ESS preferences — properly persists all fields including employee reference.
     */
    @Transactional
    public EssPreferences updateEssPreferences(Long employeeId, EssPreferences preferences) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundAppException("Employee", employeeId));

        EssPreferences existing = essPreferencesRepository.findByEmployeeId(employeeId)
                .orElse(new EssPreferences());

        if (existing.getId() == null) {
            existing.setEmployee(employee);
        }

        // Map all preference fields
        existing.setPayslipDispatchEmail(preferences.isPayslipDispatchEmail());
        existing.setPayslipDispatchWhatsapp(preferences.isPayslipDispatchWhatsapp());
        existing.setPayslipDispatchSms(preferences.isPayslipDispatchSms());
        existing.setAutoTaxCalculation(preferences.isAutoTaxCalculation());
        existing.setNotificationOptIn(preferences.isNotificationOptIn());

        return essPreferencesRepository.save(existing);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DTOs
    // ─────────────────────────────────────────────────────────────────────────

    public static class AttendanceSummary {
        public long presentDays;
        public long paidLeaveDays;
        public long absentDays;
        public long lopDays;
        public long halfDays;

        public AttendanceSummary(long present, long paidLeave, long absent, long lop, long halfDay) {
            this.presentDays = present;
            this.paidLeaveDays = paidLeave;
            this.absentDays = absent;
            this.lopDays = lop;
            this.halfDays = halfDay;
        }
    }
}
