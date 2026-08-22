package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.payroll.entity.*;
import com.desitech.vyaparsathi.payroll.repository.*;
import com.desitech.vyaparsathi.payroll.enums.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

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

    // Get employee's payslips (read-only access)
    public Page<PayrollSlip> getEmployeePayslips(Long employeeId, Pageable pageable) {
        return payrollSlipRepository.findByEmployeeIdAndShopId(employeeId, getShopId(), pageable);
    }

    // Get single payslip details
    public PayrollSlip getPayslipDetails(Long slipId, Long employeeId) {
        PayrollSlip slip = payrollSlipRepository.findById(slipId)
                .orElseThrow(() -> new RuntimeException("Payslip not found"));
        
        if (!slip.getEmployee().getId().equals(employeeId)) {
            throw new RuntimeException("Unauthorized access to payslip");
        }
        
        return slip;
    }

    // Get attendance summary
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

        return new AttendanceSummary(presentDays, paidLeaveDays, absentDays, lopDays);
    }

    // Get tax declaration
    @Transactional(readOnly = true)
    public TaxDeclaration getTaxDeclaration(Long employeeId, String financialYear) {
        return taxDeclarationRepository.findByEmployeeIdAndFinancialYear(employeeId, financialYear)
                .orElse(new TaxDeclaration());
    }

    // Submit tax declaration
    @Transactional
    public TaxDeclaration submitTaxDeclaration(Long employeeId, TaxDeclaration declaration) {
        TaxDeclaration existing = taxDeclarationRepository
                .findByEmployeeIdAndFinancialYear(employeeId, declaration.getFinancialYear())
                .orElse(new TaxDeclaration());

        existing.setEmployee(new Employee());
        existing.getEmployee().setId(employeeId);
        existing.setTaxRegime(declaration.getTaxRegime());
        existing.setLifeInsurancePremium(declaration.getLifeInsurancePremium());
        existing.setMedicalInsurancePremium(declaration.getMedicalInsurancePremium());
        existing.setEducationExpenses(declaration.getEducationExpenses());
        existing.setHomeLoanPrincipal(declaration.getHomeLoanPrincipal());
        existing.setHomeLoanInterest(declaration.getHomeLoanInterest());
        existing.setNpsContribution(declaration.getNpsContribution());
        existing.setSubmittedAt(LocalDate.now());

        return taxDeclarationRepository.save(existing);
    }

    // Request salary advance
    @Transactional
    public AdvanceRequest requestAdvance(Long employeeId, BigDecimal amount, String reason) {
        AdvanceRequest request = new AdvanceRequest();
        request.setEmployee(new Employee());
        request.getEmployee().setId(employeeId);
        request.setAmount(amount);
        request.setReason(reason);
        request.setStatus("PENDING");
        request.setRequestedAt(LocalDate.now());

        return advanceRequestRepository.save(request);
    }

    // Get employee's advance requests
    public Page<AdvanceRequest> getEmployeeAdvanceRequests(Long employeeId, Pageable pageable) {
        return advanceRequestRepository.findByEmployeeIdOrderByRequestedAtDesc(employeeId, pageable);
    }

    // Get loan details
    public Page<StaffLoan> getEmployeeLoans(Long employeeId, Pageable pageable) {
        List<StaffLoan> loans = loanRepository.findByEmployeeId(employeeId);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), loans.size());
        List<StaffLoan> pageContent = (start >= loans.size()) ? Collections.emptyList() : loans.subList(start, end);
        return new PageImpl<>(pageContent, pageable, loans.size());
    }

    // Get dispatch history
    public Page<PayslipDispatchLog> getDispatchHistory(Long employeeId, Pageable pageable) {
        return dispatchLogRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId, pageable);
    }

    // Get Form16 data
    public Form16Data getForm16(Long employeeId, String financialYear) {
        return form16Repository.findByEmployeeIdAndFinancialYear(employeeId, financialYear)
                .orElse(new Form16Data());
    }

    // Get ESS preferences
    public EssPreferences getEssPreferences(Long employeeId) {
        return essPreferencesRepository.findByEmployeeId(employeeId)
                .orElse(new EssPreferences());
    }

    // Update ESS preferences
    @Transactional
    public EssPreferences updateEssPreferences(Long employeeId, EssPreferences preferences) {
        EssPreferences existing = essPreferencesRepository.findByEmployeeId(employeeId)
                .orElse(new EssPreferences());
        
        existing.setPayslipDispatchEmail(preferences.isPayslipDispatchEmail());
        existing.setPayslipDispatchWhatsapp(preferences.isPayslipDispatchWhatsapp());
        existing.setPayslipDispatchSms(preferences.isPayslipDispatchSms());
        existing.setAutoTaxCalculation(preferences.isAutoTaxCalculation());
        existing.setNotificationOptIn(preferences.isNotificationOptIn());

        return essPreferencesRepository.save(existing);
    }

    // Helper
    private Long getShopId() {
        return 1L; // Mock - would use TenantContext in real impl
    }

    // DTOs
    public static class AttendanceSummary {
        public long presentDays;
        public long paidLeaveDays;
        public long absentDays;
        public long lopDays;

        public AttendanceSummary(long present, long paidLeave, long absent, long lop) {
            this.presentDays = present;
            this.paidLeaveDays = paidLeave;
            this.absentDays = absent;
            this.lopDays = lop;
        }
    }
}
