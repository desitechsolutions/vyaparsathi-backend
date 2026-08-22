package com.desitech.vyaparsathi.payroll.controller;

import com.desitech.vyaparsathi.payroll.dto.*;
import com.desitech.vyaparsathi.payroll.entity.AttendanceRecord;
import com.desitech.vyaparsathi.payroll.entity.BankTransaction;
import com.desitech.vyaparsathi.payroll.entity.PayrollSlip;
import com.desitech.vyaparsathi.payroll.enums.AttendanceType;
import com.desitech.vyaparsathi.payroll.enums.EmploymentStatus;
import com.desitech.vyaparsathi.payroll.service.BankingIntegrationService;
import com.desitech.vyaparsathi.payroll.service.PayrollService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/payroll")
public class PayrollController {

    @Autowired
    private PayrollService payrollService;

    // --- STAFF ENDPOINTS ---

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @PostMapping("/staff")
    public ResponseEntity<StaffDto> addStaff(@Valid @RequestBody StaffDto dto) {
        return new ResponseEntity<>(payrollService.addStaff(dto), HttpStatus.CREATED);
    }

    @PreAuthorize("hasAnyRole('ADMIN','PAYROLL_ADMIN','MANAGER')")
    @GetMapping("/staff")
    public ResponseEntity<Page<StaffResponseDto>> listStaff(
            @RequestParam String month,
            @RequestParam Integer year,
            Pageable pageable) {
        return ResponseEntity.ok(payrollService.listStaff(month, year, pageable));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PAYROLL_ADMIN','MANAGER')")
    @GetMapping("/staff/{id}")
    public ResponseEntity<StaffDto> getStaff(@PathVariable Long id) {
        return ResponseEntity.ok(payrollService.getStaff(id));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @PutMapping("/staff/{id}")
    public ResponseEntity<StaffDto> updateStaff(@PathVariable Long id, @Valid @RequestBody StaffDto dto) {
        return ResponseEntity.ok(payrollService.updateStaff(id, dto));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @DeleteMapping("/staff/{id}")
    public ResponseEntity<Void> deleteStaff(@PathVariable Long id) {
        payrollService.deleteStaff(id);
        return ResponseEntity.noContent().build();
    }

    // --- ADVANCE ENDPOINTS ---

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @PostMapping("/staff/{id}/advance")
    public ResponseEntity<Void> issueAdvance(
            @PathVariable Long id,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String remarks) {
        payrollService.issueAdvance(id, amount, remarks);
        return ResponseEntity.ok().build();
    }

    // --- SALARY PROCESSING ENDPOINTS ---

    /**
     * Process single salary payment.
     */
    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @PostMapping("/process")
    public ResponseEntity<PayrollResponseDto> processSalary(@Valid @RequestBody PayrollRequestDto dto) {
        return ResponseEntity.ok(payrollService.processSalary(dto));
    }

    /**
     * Planned for Bulk Selection: Process multiple salaries at once.
     */
    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @PostMapping("/process/bulk")
    public ResponseEntity<List<PayrollResponseDto>> processBulkSalary(@Valid @RequestBody List<PayrollRequestDto> dtos) {
        // You can implement this in your service using a loop over processSalary
        List<PayrollResponseDto> responses = dtos.stream()
                .map(payrollService::processSalary)
                .toList();
        return ResponseEntity.ok(responses);
    }

    // --- HISTORY ENDPOINTS ---

    @PreAuthorize("hasAnyRole('ADMIN','PAYROLL_ADMIN','MANAGER')")
    @GetMapping("/history/staff/{staffId}")
    public ResponseEntity<Page<PayrollResponseDto>> getStaffPaymentHistory(
            @PathVariable Long staffId,
            Pageable pageable) {
        return ResponseEntity.ok(payrollService.listPayments(staffId, pageable));
    }

    // --- PHASE 1: EMPLOYEE ENDPOINTS ---

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @PostMapping("/employees")
    public ResponseEntity<EmployeeDto> addEmployee(@Valid @RequestBody EmployeeDto dto) {
        return new ResponseEntity<>(payrollService.addEmployee(dto), HttpStatus.CREATED);
    }

    @PreAuthorize("hasAnyRole('ADMIN','PAYROLL_ADMIN','MANAGER')")
    @GetMapping("/employees")
    public ResponseEntity<Page<EmployeeDto>> listEmployees(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        com.desitech.vyaparsathi.payroll.enums.EmploymentStatus employmentStatus = null;
        if (status != null) {
            try {
                employmentStatus = com.desitech.vyaparsathi.payroll.enums.EmploymentStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        }
        return ResponseEntity.ok(payrollService.listEmployees(employmentStatus, pageable));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PAYROLL_ADMIN','MANAGER','EMPLOYEE')")
    @GetMapping("/employees/{id}")
    public ResponseEntity<EmployeeDto> getEmployee(@PathVariable Long id) {
        return ResponseEntity.ok(payrollService.getEmployee(id));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @PutMapping("/employees/{id}")
    public ResponseEntity<EmployeeDto> updateEmployee(@PathVariable Long id, @Valid @RequestBody EmployeeDto dto) {
        return ResponseEntity.ok(payrollService.updateEmployee(id, dto));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @DeleteMapping("/employees/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        payrollService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    // --- PHASE 1: SALARY STRUCTURE ENDPOINTS ---

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @PostMapping("/structures")
    public ResponseEntity<SalaryStructureDto> createSalaryStructure(@Valid @RequestBody SalaryStructureDto dto) {
        return new ResponseEntity<>(payrollService.createSalaryStructure(dto), HttpStatus.CREATED);
    }

    @PreAuthorize("hasAnyRole('ADMIN','PAYROLL_ADMIN','MANAGER')")
    @GetMapping("/structures")
    public ResponseEntity<Page<SalaryStructureDto>> listSalaryStructures(Pageable pageable) {
        return ResponseEntity.ok(payrollService.listSalaryStructures(pageable));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PAYROLL_ADMIN','MANAGER')")
    @GetMapping("/structures/{id}")
    public ResponseEntity<SalaryStructureDto> getSalaryStructure(@PathVariable Long id) {
        return ResponseEntity.ok(payrollService.getSalaryStructure(id));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @PutMapping("/structures/{id}")
    public ResponseEntity<SalaryStructureDto> updateSalaryStructure(@PathVariable Long id, @Valid @RequestBody SalaryStructureDto dto) {
        return ResponseEntity.ok(payrollService.updateSalaryStructure(id, dto));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @PostMapping("/structures/{id}/components")
    public ResponseEntity<SalaryComponentDto> addComponentToStructure(
            @PathVariable Long id,
            @Valid @RequestBody SalaryComponentDto dto) {
        return new ResponseEntity<>(payrollService.addComponentToStructure(id, dto), HttpStatus.CREATED);
    }

    // --- PHASE 1: STAFF LOAN ENDPOINTS ---

    @PreAuthorize("hasRole(\'ADMIN\') or hasRole(\'PAYROLL_ADMIN\')")
    @PostMapping("/loans")
    public ResponseEntity<StaffLoanDto> createLoan(
            @RequestParam Long employeeId,
            @Valid @RequestBody StaffLoanDto dto) {
        return new ResponseEntity<>(payrollService.createLoan(employeeId, dto), HttpStatus.CREATED);
    }

    @PreAuthorize("hasAnyRole(\'ADMIN\',\'PAYROLL_ADMIN\',\'MANAGER\',\'EMPLOYEE\')")
    @GetMapping("/loans/{employeeId}/schedule")
    public ResponseEntity<List<StaffLoanRepaymentDto>> getLoanSchedule(@PathVariable Long employeeId) {
        return ResponseEntity.ok(payrollService.getLoanSchedule(employeeId));
    }

    // --- PHASE 2: PAYROLL RUN ENDPOINTS ---

    @PreAuthorize("hasRole(\'ADMIN\') or hasRole(\'PAYROLL_ADMIN\')")
    @PostMapping("/runs")
    public ResponseEntity<PayrollRunDto> createPayrollRun(
            @RequestParam int month,
            @RequestParam int year) {
        return new ResponseEntity<>(payrollService.createPayrollRun(month, year), HttpStatus.CREATED);
    }

    @PreAuthorize("hasAnyRole(\'ADMIN\',\'PAYROLL_ADMIN\',\'MANAGER\')")
    @GetMapping("/runs")
    public ResponseEntity<Page<PayrollRunDto>> listPayrollRuns(Pageable pageable) {
        return ResponseEntity.ok(payrollService.listPayrollRuns(pageable));
    }

    @PreAuthorize("hasAnyRole(\'ADMIN\',\'PAYROLL_ADMIN\',\'MANAGER\')")
    @GetMapping("/runs/{id}")
    public ResponseEntity<PayrollRunDto> getPayrollRun(@PathVariable Long id) {
        return ResponseEntity.ok(payrollService.getPayrollRun(id));
    }

    @PreAuthorize("hasRole(\'ADMIN\') or hasRole(\'PAYROLL_ADMIN\')")
    @PostMapping("/runs/{id}/process")
    public ResponseEntity<PayrollRunDto> markPayrollRunAsProcessing(@PathVariable Long id) {
        return ResponseEntity.ok(payrollService.markPayrollRunAsProcessing(id));
    }

    @PreAuthorize("hasRole(\'ADMIN\') or hasRole(\'PAYROLL_ADMIN\')")
    @PostMapping("/runs/{id}/approve")
    public ResponseEntity<PayrollRunDto> approvePayrollRun(
            @PathVariable Long id,
            @RequestParam Long userId) {
        return ResponseEntity.ok(payrollService.approvePayrollRun(id, userId));
    }

    @PreAuthorize("hasRole(\'ADMIN\') or hasRole(\'PAYROLL_ADMIN\')")
    @PostMapping("/runs/{id}/disburse")
    public ResponseEntity<PayrollRunDto> disbursePayrollRun(
            @PathVariable Long id,
            @RequestParam Long userId) {
        return ResponseEntity.ok(payrollService.disbursePayrollRun(id, userId));
    }

    // --- PHASE 2: PAYROLL SLIP ENDPOINTS ---

    @PreAuthorize("hasAnyRole(\'ADMIN\',\'PAYROLL_ADMIN\',\'MANAGER\')")
    @GetMapping("/runs/{runId}/slips")
    public ResponseEntity<Page<PayrollSlipDto>> listPayrollSlips(
            @PathVariable Long runId,
            Pageable pageable) {
        return ResponseEntity.ok(payrollService.listPayrollSlipsForRun(runId, pageable));
    }

    @PreAuthorize("hasAnyRole(\'ADMIN\',\'PAYROLL_ADMIN\',\'MANAGER\',\'EMPLOYEE\')")
    @GetMapping("/slips/{slipId}")
    public ResponseEntity<PayrollSlipDto> getPayrollSlip(@PathVariable Long slipId) {
        return ResponseEntity.ok(payrollService.getPayrollSlip(slipId));
    }

    @PreAuthorize("hasRole(\'ADMIN\') or hasRole(\'PAYROLL_ADMIN\')")
    @PutMapping("/slips/{slipId}")
    public ResponseEntity<PayrollSlipDto> updatePayrollSlip(
            @PathVariable Long slipId,
            @Valid @RequestBody PayrollSlipDto dto) {
        return ResponseEntity.ok(payrollService.updatePayrollSlip(slipId, dto));
    }

    // --- PHASE 2: ATTENDANCE ENDPOINTS ---

    @PreAuthorize("hasRole(\'ADMIN\') or hasRole(\'PAYROLL_ADMIN\')")
    @PostMapping("/attendance")
    public ResponseEntity<AttendanceRecordDto> recordAttendance(
            @RequestParam Long employeeId,
            @RequestParam String date,
            @RequestParam String type) {
        LocalDate attendanceDate = LocalDate.parse(date);
        AttendanceType attendanceType = AttendanceType.valueOf(type);
        return new ResponseEntity<>(payrollService.recordAttendance(employeeId, attendanceDate, attendanceType), HttpStatus.CREATED);
    }

    @PreAuthorize("hasAnyRole(\'ADMIN\',\'PAYROLL_ADMIN\',\'MANAGER\')")
    @GetMapping("/attendance")
    public ResponseEntity<List<AttendanceRecord>> getAttendanceForPeriod(
            @RequestParam Long employeeId,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        return ResponseEntity.ok(payrollService.getAttendanceForPeriod(employeeId, start, end));
    }

    // --- PHASE 3: STATUTORY COMPLIANCE ENDPOINTS ---

    @PreAuthorize("hasRole(\'ADMIN\') or hasRole(\'PAYROLL_ADMIN\')")
    @PostMapping("/runs/{id}/statutory")
    public ResponseEntity<Void> applyStatutoryDeductions(@PathVariable Long id) {
        payrollService.applyStatutoryDeductions(id);
        return ResponseEntity.ok().build();
    }

    // --- PHASE 3: BANKING INTEGRATION ENDPOINTS ---

    @PreAuthorize("hasRole(\'ADMIN\') or hasRole(\'PAYROLL_ADMIN\')")
    @PostMapping("/runs/{id}/disburse-razorpayx")
    public ResponseEntity<Object> disburseViaRazorpayX(@PathVariable Long id) {
        BankingIntegrationService.BankingResult result = payrollService.disburseViaRazorpayX(id);
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasRole(\'ADMIN\') or hasRole(\'PAYROLL_ADMIN\')")
    @GetMapping("/runs/{id}/export-neft")
    public ResponseEntity<String> exportNEFT(@PathVariable Long id) {
        String batch = payrollService.generateNEFTBatchFile(id);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"NEFT_" + id + ".csv\"")
                .body(batch);
    }

    @PreAuthorize("hasRole(\'ADMIN\') or hasRole(\'PAYROLL_ADMIN\')")
    @GetMapping("/runs/{id}/export-nach")
    public ResponseEntity<String> exportNACH(@PathVariable Long id) {
        String batch = payrollService.generateNACHBatchFile(id);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"NACH_" + id + ".txt\"")
                .body(batch);
    }

    @PreAuthorize("hasRole(\'ADMIN\') or hasRole(\'PAYROLL_ADMIN\')")
    @GetMapping("/runs/{id}/export-ecr")
    public ResponseEntity<String> exportECR(@PathVariable Long id) {
        String ecr = payrollService.generateECRFile(id);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"ECR_" + id + ".txt\"")
                .body(ecr);
    }

    @PreAuthorize("hasAnyRole(\'ADMIN\',\'PAYROLL_ADMIN\')")
    @GetMapping("/bank-transactions")
    public ResponseEntity<Page<BankTransaction>> listBankTransactions(
            @RequestParam(defaultValue = "PENDING") String status,
            Pageable pageable) {
        return ResponseEntity.ok(payrollService.listBankTransactions(status, pageable));
    }

    @PreAuthorize("hasAnyRole(\'ADMIN\',\'PAYROLL_ADMIN\')")
    @GetMapping("/employees/{id}/validate-bank")
    public ResponseEntity<Object> validateBankDetails(@PathVariable Long id) {
        BankingIntegrationService.BankAccountValidation validation = payrollService.validateBankDetails(id);
        return ResponseEntity.ok(validation);
    }

    // --- PHASE 4: EMPLOYEE SELF-SERVICE ENDPOINTS ---

    @PreAuthorize("hasRole(\'EMPLOYEE\')")
    @GetMapping("/employee/payslips")
    public ResponseEntity<Page<PayrollSlip>> getMyPayslips(Pageable pageable) {
        Long employeeId = getCurrentEmployeeId();
        return ResponseEntity.ok(payrollService.getEmployeePayslips(employeeId, pageable));
    }

    @PreAuthorize("hasRole(\'EMPLOYEE\')")
    @GetMapping("/employee/payslips/{id}")
    public ResponseEntity<PayrollSlip> getMyPayslip(@PathVariable Long id) {
        Long employeeId = getCurrentEmployeeId();
        return ResponseEntity.ok(payrollService.getPayslipDetails(id, employeeId));
    }

    @PreAuthorize("hasRole(\'EMPLOYEE\')")
    @GetMapping("/employee/attendance")
    public ResponseEntity<Object> getMyAttendance(
            @RequestParam int month,
            @RequestParam int year) {
        Long employeeId = getCurrentEmployeeId();
        return ResponseEntity.ok(payrollService.getAttendanceSummary(employeeId, month, year));
    }

    @PreAuthorize("hasRole(\'EMPLOYEE\')")
    @GetMapping("/employee/tax-declaration")
    public ResponseEntity<Object> getMyTaxDeclaration(@RequestParam String financialYear) {
        Long employeeId = getCurrentEmployeeId();
        return ResponseEntity.ok(payrollService.getTaxDeclaration(employeeId, financialYear));
    }

    @PreAuthorize("hasRole(\'EMPLOYEE\')")
    @PostMapping("/employee/tax-declaration")
    public ResponseEntity<Object> submitTaxDeclaration(@Valid @RequestBody Object declaration) {
        Long employeeId = getCurrentEmployeeId();
        return ResponseEntity.ok(payrollService.submitTaxDeclaration(employeeId, declaration));
    }

    @PreAuthorize("hasRole(\'EMPLOYEE\')")
    @PostMapping("/employee/advance-requests")
    public ResponseEntity<Object> requestAdvance(
            @RequestParam BigDecimal amount,
            @RequestParam String reason) {
        Long employeeId = getCurrentEmployeeId();
        return new ResponseEntity<>(payrollService.requestAdvance(employeeId, amount, reason), HttpStatus.CREATED);
    }

    @PreAuthorize("hasRole(\'EMPLOYEE\')")
    @GetMapping("/employee/advance-requests")
    public ResponseEntity<Page<Object>> getMyAdvanceRequests(Pageable pageable) {
        Long employeeId = getCurrentEmployeeId();
        return ResponseEntity.ok(payrollService.getAdvanceRequests(employeeId, pageable));
    }

    @PreAuthorize("hasRole(\'EMPLOYEE\')")
    @GetMapping("/employee/loans")
    public ResponseEntity<Page<Object>> getMyLoans(Pageable pageable) {
        Long employeeId = getCurrentEmployeeId();
        return ResponseEntity.ok(payrollService.getEmployeeLoans(employeeId, pageable));
    }

    @PreAuthorize("hasRole(\'EMPLOYEE\')")
    @GetMapping("/employee/dispatch-history")
    public ResponseEntity<Page<Object>> getDispatchHistory(Pageable pageable) {
        Long employeeId = getCurrentEmployeeId();
        return ResponseEntity.ok(payrollService.getDispatchHistory(employeeId, pageable));
    }

    @PreAuthorize("hasRole(\'EMPLOYEE\')")
    @GetMapping("/employee/form16")
    public ResponseEntity<Object> getForm16(@RequestParam String financialYear) {
        Long employeeId = getCurrentEmployeeId();
        return ResponseEntity.ok(payrollService.getForm16Data(employeeId, financialYear));
    }

    @PreAuthorize("hasRole(\'EMPLOYEE\')")
    @GetMapping("/employee/ess-preferences")
    public ResponseEntity<Object> getEssPreferences() {
        Long employeeId = getCurrentEmployeeId();
        return ResponseEntity.ok(payrollService.getEssPreferences(employeeId));
    }

    @PreAuthorize("hasRole(\'EMPLOYEE\')")
    @PutMapping("/employee/ess-preferences")
    public ResponseEntity<Object> updateEssPreferences(@Valid @RequestBody Object preferences) {
        Long employeeId = getCurrentEmployeeId();
        return ResponseEntity.ok(payrollService.updateEssPreferences(employeeId, preferences));
    }

    // Get authenticated employee ID from security context
    private Long getCurrentEmployeeId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("User not authenticated");
        }

        try {
            // Try extracting from principal attributes (works for JWT and OAuth2)
            Object principal = auth.getPrincipal();
            if (principal instanceof java.util.Map) {
                Object empId = ((java.util.Map<?, ?>) principal).get("employeeId");
                if (empId != null) return Long.valueOf(empId.toString());
            }

            // Fallback: extract from username if in format "emp_123"
            String username = auth.getName();
            if (username != null && username.startsWith("emp_")) {
                return Long.valueOf(username.substring(4));
            }
        } catch (Exception e) {
            // Fall through to error
        }

        throw new IllegalStateException("Cannot extract employee ID from authentication: " + auth.getName());
    }

    // --- LEAVE MANAGEMENT ENDPOINTS (P1) ---

    @Autowired
    private com.desitech.vyaparsathi.payroll.service.LeaveManagementService leaveManagementService;

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @PostMapping("/leave-types")
    public ResponseEntity<com.desitech.vyaparsathi.payroll.dto.LeaveTypeDto> createLeaveType(
            @Valid @RequestBody com.desitech.vyaparsathi.payroll.dto.LeaveTypeDto dto) {
        return new ResponseEntity<>(leaveManagementService.createLeaveType(dto), HttpStatus.CREATED);
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @PostMapping("/employee/leave-applications")
    public ResponseEntity<com.desitech.vyaparsathi.payroll.dto.LeaveApplicationDto> applyLeave(
            @RequestParam LocalDate fromDate,
            @RequestParam LocalDate toDate,
            @RequestParam Long leaveTypeId) {
        Long employeeId = getCurrentEmployeeId();
        return new ResponseEntity<>(leaveManagementService.applyLeave(employeeId, fromDate, toDate, leaveTypeId, ""), HttpStatus.CREATED);
    }

    // --- HOLIDAY CALENDAR ENDPOINTS (P1) ---

    @Autowired
    private com.desitech.vyaparsathi.payroll.service.HolidayCalendarService holidayCalendarService;

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @PostMapping("/holiday-calendar")
    public ResponseEntity<com.desitech.vyaparsathi.payroll.dto.HolidayCalendarDto> createHolidayCalendar(
            @RequestParam Integer year,
            @RequestParam String weeklyOffDays) {
        return new ResponseEntity<>(holidayCalendarService.createHolidayCalendar(year, weeklyOffDays), HttpStatus.CREATED);
    }

    // --- PDF & ACCOUNTING OPERATIONS ---

    @PreAuthorize("hasAnyRole(\'ADMIN\',\'PAYROLL_ADMIN\',\'MANAGER\',\'EMPLOYEE\')")
    @GetMapping("/slips/{id}/pdf")
    public ResponseEntity<byte[]> downloadPayslipPDF(@PathVariable Long id) {
        byte[] pdf = payrollService.generatePayslipPDF(id);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"payslip-" + id + ".pdf\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PreAuthorize("hasRole(\'EMPLOYEE\')")
    @GetMapping("/employee/form16/pdf")
    public ResponseEntity<byte[]> downloadForm16PDF(@RequestParam String financialYear) {
        Long employeeId = 1L; // Mock - would use current user
        byte[] pdf = payrollService.generateForm16PDF(employeeId, financialYear);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"form16-" + financialYear + ".pdf\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PreAuthorize("hasRole(\'ADMIN\') or hasRole(\'PAYROLL_ADMIN\')")
    @PostMapping("/runs/{id}/post-to-gl")
    public ResponseEntity<Object> postPayrollToGeneralLedger(@PathVariable Long id) {
        Object result = payrollService.postPayrollToGL(id);
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasRole(\'ADMIN\') or hasRole(\'PAYROLL_ADMIN\')")
    @PostMapping("/generate-form16")
    public ResponseEntity<Void> generateForm16ForAllEmployees(@RequestParam String financialYear) {
        payrollService.generateForm16ForAllEmployees(financialYear);
        return ResponseEntity.ok().build();
    }
}

    // --- LEAVE MANAGEMENT ENDPOINTS (P1 ENTERPRISE HARDENING) ---
