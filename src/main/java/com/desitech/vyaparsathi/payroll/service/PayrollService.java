package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.audit.helper.AuditHelper;
import com.desitech.vyaparsathi.changelog.model.ChangeLogOperation;
import com.desitech.vyaparsathi.changelog.service.ChangeLogService;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.event.NotificationEvent;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.payroll.dto.*;
import com.desitech.vyaparsathi.payroll.entity.*;
import com.desitech.vyaparsathi.payroll.enums.*;
import com.desitech.vyaparsathi.payroll.mapper.*;
import com.desitech.vyaparsathi.payroll.repository.*;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PayrollService {

    @Autowired private StaffRepository staffRepository;
    @Autowired private PayrollRecordRepository payrollRecordRepository;
    @Autowired private PayrollMapper payrollMapper;
    @Autowired private StaffMapper staffMapper;
    @Autowired private ChangeLogService changeLogService;
    @Autowired private ShopRepository shopRepository;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private AuditHelper auditHelper;

    // Phase 1: New autowires for Employee, Structures, Loans
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private SalaryStructureRepository salaryStructureRepository;
    @Autowired private SalaryComponentRepository salaryComponentRepository;
    @Autowired private StaffLoanRepository staffLoanRepository;
    @Autowired private StaffLoanRepaymentRepository staffLoanRepaymentRepository;
    @Autowired private EmployeeMapper employeeMapper;
    @Autowired private SalaryStructureMapper salaryStructureMapper;
    @Autowired private SalaryComponentMapper salaryComponentMapper;
    @Autowired private StaffLoanMapper staffLoanMapper;

    // Phase 2: Payroll Run & Slip repositories
    @Autowired private PayrollRunRepository payrollRunRepository;
    @Autowired private PayrollSlipRepository payrollSlipRepository;
    @Autowired private PayrollSlipItemRepository payrollSlipItemRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private PayrollCalculationEngine calculationEngine;
    @Autowired private PayrollRunMapper payrollRunMapper;
    @Autowired private PayrollSlipMapper payrollSlipMapper;

    // Phase 3: Statutory & Banking
    @Autowired private StatutoryComplianceEngine complianceEngine;
    @Autowired private BankingIntegrationService bankingService;
    @Autowired private StatutoryConfigRepository statutoryConfigRepository;
    @Autowired private BankTransactionRepository bankTransactionRepository;

    // Phase 4: ESS
    @Autowired private EmployeeSelfServiceEngine essEngine;
    @Autowired private PayslipDispatchService dispatchService;

    // PDF, Accounting, Form16
    @Autowired private PayslipPDFGenerator pdfGenerator;
    @Autowired private PayrollAccountingBridge accountingBridge;
    @Autowired private PayrollEventPublisher payrollEventPublisher;
    @Autowired(required = false) private TaxDeclarationRepository taxDeclarationRepository;
    @Autowired(required = false) private AdvanceRequestRepository advanceRequestRepository;
    @Autowired(required = false) private Form16DataRepository form16DataRepository;

    // --- HELPER METHODS ---

    private String generatePayrollRunNumber(Long shopId, int year, int month) {
        String prefix = String.format("PR-%04d-%02d", year, month);
        long count = payrollRunRepository.countByShopIdAndPayrollMonthAndPayrollYear(shopId, String.format("%02d", month), year);
        return String.format("%s-%03d", prefix, count + 1);
    }

    private String generatePayslipNumber(Long runId, Long employeeId) {
        return String.format("SLIP-%d-EMP%d", runId, employeeId);
    }

    private int calculateWorkingDays(List<AttendanceRecord> attendance, PayrollRun run) {
        // Working days = calendar days - weekends (approx 26 for most months)
        // This is a simplified calculation; real implementation would use holiday calendar
        int weekends = (run.getCalendarDays() / 7) * 2; // Rough estimate
        return Math.max(run.getCalendarDays() - weekends, 20);
    }

    private int calculatePresentDays(List<AttendanceRecord> attendance) {
        return (int) attendance.stream()
            .filter(r -> r.getAttendanceType() != null &&
                    (r.getAttendanceType().toString().equals("PRESENT") ||
                     r.getAttendanceType().toString().equals("HALF_DAY")))
            .count();
    }

    // --- STAFF MANAGEMENT ---

    @Transactional
    public StaffDto addStaff(@Valid StaffDto dto) {
        Shop shop = getCurrentShop();
        Staff staff = staffMapper.toEntity(dto);
        staff.setShop(shop);
        staff.setAdvanceBalance(BigDecimal.ZERO);
        staff.setActive(true);

        staffRepository.save(staff);
        changeLogService.append("STAFF", staff.getId(), ChangeLogOperation.CREATE, staff, "LOCAL_DEVICE");
        return staffMapper.toDto(staff);
    }

    @Transactional
    public StaffDto updateStaff(Long id, @Valid StaffDto dto) {
        Staff staff = findStaffById(id);
        staffMapper.updateEntityFromDto(dto, staff);

        staffRepository.save(staff);
        changeLogService.append("STAFF", id, ChangeLogOperation.UPDATE, staff, "LOCAL_DEVICE");
        return staffMapper.toDto(staff);
    }

    @Transactional
    public void deleteStaff(Long id) {
        Staff staff = findStaffById(id);
        staff.setActive(false); // Soft delete
        staffRepository.save(staff);
        changeLogService.append("STAFF", id, ChangeLogOperation.DELETE, null, "LOCAL_DEVICE");
    }

    // --- ADVANCE MANAGEMENT ---

    @Transactional
    public void issueAdvance(Long staffId, BigDecimal amount, String remarks) {
        Staff staff = findStaffById(staffId);
        staff.setAdvanceBalance(staff.getAdvanceBalance().add(amount));
        staffRepository.save(staff);

        changeLogService.append("STAFF_ADVANCE", staffId, ChangeLogOperation.UPDATE, "Issued: " + amount, "LOCAL_DEVICE");

        eventPublisher.publishEvent(new NotificationEvent(
                this,
                "payment",
                "Salary Paid",
                "₹" + amount + " paid to " + staff.getName(),
                "admin@shop.com",
                "/payroll/history",
                "low"
        ));
    }

    // --- PAYROLL PROCESSING ---

    @Transactional
    public PayrollResponseDto processSalary(@Valid PayrollRequestDto dto) {
        Staff staff = findStaffById(dto.getStaffId());
        Long shopId = TenantContext.getCurrentShopId();

        // 1. Check for Duplicate Payment (Prevention)
        if (payrollRecordRepository.existsByStaffIdAndSalaryMonthAndSalaryYearAndShopId(
                staff.getId(), dto.getSalaryMonth(), dto.getSalaryYear(), shopId)) {
            throw new IllegalStateException("Salary already processed for " + dto.getSalaryMonth() + " " + dto.getSalaryYear());
        }

        // 2. Perform Financial Calculation
        BigDecimal base = staff.getBaseSalary();
        BigDecimal netAmount = base.add(dto.getBonus())
                .subtract(dto.getDeductions())
                .subtract(dto.getAdvanceDeduction());

        // 3. Create Entity
        PayrollRecord record = new PayrollRecord();
        record.setStaff(staff);
        record.setShop(getCurrentShop());
        record.setSalaryMonth(dto.getSalaryMonth());
        record.setSalaryYear(dto.getSalaryYear());
        record.setBaseSalaryAtTime(base);
        record.setBonus(dto.getBonus());
        record.setDeductions(dto.getDeductions());
        record.setAdvanceDeduction(dto.getAdvanceDeduction());
        record.setNetAmount(netAmount);
        record.setPaymentDate(LocalDate.now());
        record.setStatus(PayrollStatus.PAID);
        record.setPaymentMode(dto.getPaymentMode());
        record.setRemarks(dto.getRemarks());

        // 4. Recovery: Deduct from Staff Advance Balance
        if (dto.getAdvanceDeduction().compareTo(BigDecimal.ZERO) > 0) {
            staff.setAdvanceBalance(staff.getAdvanceBalance().subtract(dto.getAdvanceDeduction()));
            staffRepository.save(staff);
        }

        payrollRecordRepository.save(record);

        // 5. Audit and Notify
        auditHelper.log("PAYROLL", "Payroll Processing", record.getId().toString(), record.getStaff().getName()+" - "+ record.getSalaryMonth() +" SALARY");
        changeLogService.append("PAYROLL", record.getId(), ChangeLogOperation.CREATE, record, "LOCAL_DEVICE");

        eventPublisher.publishEvent(new NotificationEvent(
                this,
                "payment",
                "Salary Paid",
                "₹" + netAmount + " paid to " + staff.getName(),
                "admin@shop.com",
                "/payroll/history",
                "low"
        ));

        return payrollMapper.toDto(record);
    }

    // --- QUERIES ---

    // Update the method signature to include month and year
    public Page<StaffResponseDto> listStaff(String month, Integer year, Pageable pageable) {
        Long shopId = TenantContext.getCurrentShopId();

        // 1. Get the page of staff as you did before
        Page<Staff> staffPage = staffRepository.findByShopIdAndActiveTrue(shopId, pageable);

        // 2. Map and Check Status in one go
        return staffPage.map(staff -> {
            // Map base fields
            StaffResponseDto dto = staffMapper.toResponseDto(staff);

            // 3. Check database if a payment record exists for THIS specific month/year
            boolean isPaid = payrollRecordRepository.existsByStaffIdAndSalaryMonthAndSalaryYearAndShopId(
                    staff.getId(), month, year, shopId
            );

            dto.setPaidInCurrentPeriod(isPaid);
            return dto;
        });
    }

    public Page<PayrollResponseDto> listPayments(Long staffId, Pageable pageable) {
        return payrollRecordRepository.findByShopIdAndStaffId(TenantContext.getCurrentShopId(), staffId, pageable)
                .map(payrollMapper::toDto);
    }

    // --- HELPERS ---

    private Staff findStaffById(Long id) {
        return staffRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Staff", id));
    }

    private Shop getCurrentShop() {
        return shopRepository.findById(TenantContext.getCurrentShopId())
                .orElseThrow(() -> new EntityNotFoundAppException("Shop", TenantContext.getCurrentShopId()));
    }

    // --- READ OPERATIONS ---

    public StaffDto getStaff(Long id) {
        // We use findByIdAndActiveTrue to ensure we don't return soft-deleted staff
        Staff staff = staffRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Staff", id));
        return staffMapper.toDto(staff);
    }

    public List<StaffDto> getAllActiveStaffForShop() {
        // Useful for population of dropdowns in the "Pay Salary" modal
        return staffRepository.findByShopIdAndActiveTrue(TenantContext.getCurrentShopId())
                .stream()
                .map(staffMapper::toDto)
                .toList();
    }

    // --- PHASE 1: EMPLOYEE MASTER ---

    @Transactional
    public EmployeeDto addEmployee(@Valid EmployeeDto dto) {
        Shop shop = getCurrentShop();
        Employee employee = employeeMapper.toEntity(dto);
        employee.setShop(shop);
        if (employee.getEmploymentStatus() == null) {
            employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        }
        if (employee.getIsActive() == null) {
            employee.setIsActive(true);
        }

        employeeRepository.save(employee);
        changeLogService.append("EMPLOYEE", employee.getId(), ChangeLogOperation.CREATE, employee, "LOCAL_DEVICE");
        return employeeMapper.toDto(employee);
    }

    @Transactional
    public EmployeeDto updateEmployee(Long id, @Valid EmployeeDto dto) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Employee", id));
        employeeMapper.updateEntityFromDto(dto, employee);
        employeeRepository.save(employee);
        changeLogService.append("EMPLOYEE", id, ChangeLogOperation.UPDATE, employee, "LOCAL_DEVICE");
        return employeeMapper.toDto(employee);
    }

    @Transactional
    public void deleteEmployee(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Employee", id));
        employee.setIsActive(false);
        employeeRepository.save(employee);
        changeLogService.append("EMPLOYEE", id, ChangeLogOperation.DELETE, null, "LOCAL_DEVICE");
    }

    public EmployeeDto getEmployee(Long id) {
        Employee employee = employeeRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Employee", id));
        return employeeMapper.toDto(employee);
    }

    public Page<EmployeeDto> listEmployees(EmploymentStatus status, Pageable pageable) {
        Long shopId = TenantContext.getCurrentShopId();
        Page<Employee> employees = (status != null)
                ? employeeRepository.findByShopIdAndEmploymentStatus(shopId, status, pageable)
                : employeeRepository.findByShopIdAndIsActiveTrue(shopId, pageable);
        return employees.map(employeeMapper::toDto);
    }

    // --- PHASE 1: SALARY STRUCTURES ---

    @Transactional
    public SalaryStructureDto createSalaryStructure(@Valid SalaryStructureDto dto) {
        Shop shop = getCurrentShop();
        SalaryStructure structure = salaryStructureMapper.toEntity(dto);
        structure.setShop(shop);
        if (structure.getIsActive() == null) {
            structure.setIsActive(true);
        }

        salaryStructureRepository.save(structure);
        changeLogService.append("SALARY_STRUCTURE", structure.getId(), ChangeLogOperation.CREATE, structure, "LOCAL_DEVICE");
        return salaryStructureMapper.toDto(structure);
    }

    @Transactional
    public SalaryStructureDto updateSalaryStructure(Long id, @Valid SalaryStructureDto dto) {
        SalaryStructure structure = salaryStructureRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("SalaryStructure", id));
        salaryStructureMapper.updateEntityFromDto(dto, structure);
        salaryStructureRepository.save(structure);
        changeLogService.append("SALARY_STRUCTURE", id, ChangeLogOperation.UPDATE, structure, "LOCAL_DEVICE");
        return salaryStructureMapper.toDto(structure);
    }

    public Page<SalaryStructureDto> listSalaryStructures(Pageable pageable) {
        Long shopId = TenantContext.getCurrentShopId();
        return salaryStructureRepository.findByShopIdAndIsActiveTrue(shopId, pageable)
                .map(salaryStructureMapper::toDto);
    }

    public SalaryStructureDto getSalaryStructure(Long id) {
        SalaryStructure structure = salaryStructureRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("SalaryStructure", id));
        return salaryStructureMapper.toDto(structure);
    }

    @Transactional
    public SalaryComponentDto addComponentToStructure(Long structureId, @Valid SalaryComponentDto dto) {
        SalaryStructure structure = salaryStructureRepository.findById(structureId)
                .orElseThrow(() -> new EntityNotFoundAppException("SalaryStructure", structureId));
        SalaryComponent component = salaryComponentMapper.toEntity(dto);
        component.setStructure(structure);
        component.setShopId(TenantContext.getCurrentShopId());

        salaryComponentRepository.save(component);
        return salaryComponentMapper.toDto(component);
    }

    // --- PHASE 1: STAFF LOANS ---

    @Transactional
    public StaffLoanDto createLoan(Long employeeId, @Valid StaffLoanDto dto) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundAppException("Employee", employeeId));

        Shop shop = getCurrentShop();
        StaffLoan loan = staffLoanMapper.toEntity(dto);
        loan.setShop(shop);
        loan.setEmployee(employee);
        loan.setLoanNumber("LOAN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        if (loan.getStatus() == null) {
            loan.setStatus(LoanStatus.ACTIVE);
        }

        staffLoanRepository.save(loan);
        auditHelper.log("STAFF_LOAN", "Loan Creation", loan.getId().toString(), employee.getFirstName() + " - " + dto.getLoanType());

        // Auto-generate repayment schedule immediately after loan creation
        generateLoanRepaymentSchedule(loan);

        return staffLoanMapper.toDto(loan);
    }

    /**
     * Generates and persists the full EMI repayment schedule for a loan.
     * Uses the standard EMI formula for interest-bearing loans.
     * For zero-interest loans, divides principal equally across tenure.
     */
    private void generateLoanRepaymentSchedule(StaffLoan loan) {
        BigDecimal principal = loan.getPrincipalAmount();
        BigDecimal annualRate = loan.getInterestRateAnnual() != null ? loan.getInterestRateAnnual() : BigDecimal.ZERO;
        int months = loan.getTenureMonths() != null && loan.getTenureMonths() > 0 ? loan.getTenureMonths() : 1;

        // Parse recovery start month (format: "MM/YYYY" or "YYYY-MM")
        java.time.LocalDate firstDueDate;
        try {
            String[] parts = loan.getRecoveryStartMonth().contains("/")
                    ? loan.getRecoveryStartMonth().split("/")
                    : loan.getRecoveryStartMonth().split("-");
            int recoveryMonth = Integer.parseInt(parts[0].length() == 2 ? parts[0] : parts[1]);
            int recoveryYear  = Integer.parseInt(parts[0].length() == 4 ? parts[0] : parts[1]);
            firstDueDate = java.time.LocalDate.of(recoveryYear, recoveryMonth, 1).withDayOfMonth(1);
        } catch (Exception e) {
            firstDueDate = loan.getDisbursementDate().plusMonths(1).withDayOfMonth(1);
        }

        BigDecimal emiAmount;
        BigDecimal monthlyRate;

        if (annualRate.compareTo(BigDecimal.ZERO) == 0) {
            // Zero-interest loan — equal principal installments
            emiAmount = principal.divide(new BigDecimal(months), 2, java.math.RoundingMode.HALF_UP);
            monthlyRate = BigDecimal.ZERO;
        } else {
            monthlyRate = annualRate.divide(new BigDecimal(1200), 8, java.math.RoundingMode.HALF_UP);
            // EMI formula: P * r * (1+r)^n / ((1+r)^n - 1)
            BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
            BigDecimal onePlusRpowN = onePlusR.pow(months);
            emiAmount = principal.multiply(monthlyRate).multiply(onePlusRpowN)
                    .divide(onePlusRpowN.subtract(BigDecimal.ONE), 2, java.math.RoundingMode.HALF_UP);
        }

        BigDecimal remainingPrincipal = principal;

        for (int i = 1; i <= months; i++) {
            BigDecimal interestAmount = remainingPrincipal.multiply(monthlyRate)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal principalAmount = emiAmount.subtract(interestAmount);

            // Last installment: clear any rounding residual
            if (i == months) {
                principalAmount = remainingPrincipal;
                emiAmount = principalAmount.add(interestAmount);
            }

            StaffLoanRepayment repayment = new StaffLoanRepayment();
            repayment.setLoan(loan);
            repayment.setInstallmentNumber(i);
            repayment.setDueDate(firstDueDate.plusMonths(i - 1));
            repayment.setPrincipalAmount(principalAmount.max(BigDecimal.ZERO));
            repayment.setInterestAmount(interestAmount);
            repayment.setTotalEMI(emiAmount);
            repayment.setPaymentStatus(com.desitech.vyaparsathi.payroll.enums.RepaymentStatus.PENDING);
            staffLoanRepaymentRepository.save(repayment);

            remainingPrincipal = remainingPrincipal.subtract(principalAmount).max(BigDecimal.ZERO);
        }
    }

    public BigDecimal calculateLoanEMI(BigDecimal principal, BigDecimal annualRate, Integer months) {
        if (months == 0) return principal;

        BigDecimal monthlyRate = annualRate.divide(new BigDecimal(12 * 100), 6, java.math.RoundingMode.HALF_UP);

        // EMI = P * r * (1 + r)^n / ((1 + r)^n - 1)
        BigDecimal numerator = monthlyRate.multiply(monthlyRate.add(BigDecimal.ONE).pow(months));
        BigDecimal denominator = numerator.subtract(monthlyRate.add(BigDecimal.ONE).pow(months).subtract(BigDecimal.ONE));

        return principal.multiply(numerator).divide(denominator, 2, java.math.RoundingMode.HALF_UP);
    }

    public List<StaffLoanRepaymentDto> getLoanSchedule(Long loanId) {
        StaffLoan loan = staffLoanRepository.findById(loanId)
                .orElseThrow(() -> new EntityNotFoundAppException("StaffLoan", loanId));

        return staffLoanRepaymentRepository.findByLoanIdOrderByInstallmentNumber(loanId).stream()
                .map(r -> new StaffLoanRepaymentDto(
                        r.getId(),
                        r.getLoan().getId(),
                        r.getInstallmentNumber(),
                        r.getDueDate(),
                        r.getPaidDate(),
                        r.getPrincipalAmount(),
                        r.getInterestAmount(),
                        r.getTotalEMI(),
                        r.getPaymentStatus(),
                        r.getPayrollSlipId(),
                        r.getRemarks()
                ))
                .toList();
    }

    // --- PHASE 2: PAYROLL RUN MANAGEMENT ---

    @Transactional
    public PayrollRunDto createPayrollRun(int month, int year) {
        Shop shop = getCurrentShop();
        Long shopId = TenantContext.getCurrentShopId();
        String payrollMonth = String.format("%02d", month); // Convert int to "01", "02", etc.

        if (payrollRunRepository.existsByShopIdAndPayrollMonthAndPayrollYear(shopId, payrollMonth, year)) {
            throw new IllegalStateException("Payroll run already exists for " + month + "/" + year);
        }

        PayrollRun run = new PayrollRun();
        run.setShop(shop);
        run.setPayrollMonth(String.format("%02d", month));
        run.setPayrollYear(year);
        run.setStatus(PayrollRunStatus.DRAFT);
        run.setTotalGrossEarnings(BigDecimal.ZERO);
        run.setTotalNetPayable(BigDecimal.ZERO);
        run.setTotalEmployerContributions(BigDecimal.ZERO);

        // Set required fields
        run.setRunNumber(generatePayrollRunNumber(shopId, year, month)); // Auto-generate format: PR-2026-08-001
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth()); // Last day of month
        run.setStartDate(startDate);
        run.setEndDate(endDate);
        run.setCalendarDays(endDate.getDayOfMonth()); // Days in month

        payrollRunRepository.save(run);
        changeLogService.append("PAYROLL_RUN", run.getId(), ChangeLogOperation.CREATE, run, "LOCAL_DEVICE");
        return payrollRunMapper.toDto(run);
    }

    @Transactional
    public PayrollRunDto markPayrollRunAsProcessing(Long runId) {
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> new EntityNotFoundAppException("PayrollRun", runId));

        if (!run.getStatus().equals(PayrollRunStatus.DRAFT)) {
            throw new IllegalStateException("Only DRAFT payroll runs can move to PROCESSING");
        }

        run.setStatus(PayrollRunStatus.PROCESSING);
        // processingStartedAt field removed from PayrollRun — no-op
        payrollRunRepository.save(run);
        changeLogService.append("PAYROLL_RUN", runId, ChangeLogOperation.UPDATE, run, "LOCAL_DEVICE");
        return payrollRunMapper.toDto(run);
    }

    @Transactional
    public PayrollRunDto approvePayrollRun(Long runId, Long approverId) {
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> new EntityNotFoundAppException("PayrollRun", runId));

        if (!run.getStatus().equals(PayrollRunStatus.PENDING_APPROVAL)) {
            throw new IllegalStateException("Only PENDING_APPROVAL payroll runs can be approved");
        }

        // ── Maker-Checker Validation ────────────────────────────────────────
        // The person who prepared the run cannot approve it (four-eyes principle)
        if (run.getPreparedByUserId() != null && run.getPreparedByUserId().equals(approverId)) {
            throw new IllegalStateException(
                    "Maker-Checker violation: The preparer cannot approve their own payroll run. " +
                    "A different authorized user must approve.");
        }

        run.setStatus(PayrollRunStatus.APPROVED);
        run.setApprovedByUserId(approverId);
        run.setApprovedAt(LocalDate.now().atStartOfDay());
        payrollRunRepository.save(run);
        changeLogService.append("PAYROLL_RUN", runId, ChangeLogOperation.UPDATE, run, "LOCAL_DEVICE");
        return payrollRunMapper.toDto(run);
    }

    @Transactional
    public PayrollRunDto disbursePayrollRun(Long runId, Long disburserId) {
        Long shopId = TenantContext.getCurrentShopId();
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> new EntityNotFoundAppException("PayrollRun", runId));

        if (!run.getStatus().equals(PayrollRunStatus.APPROVED)) {
            throw new IllegalStateException("Only APPROVED payroll runs can be disbursed");
        }

        run.setStatus(PayrollRunStatus.DISBURSED);
        run.setDisbursedAt(LocalDate.now().atStartOfDay());
        payrollRunRepository.save(run);
        changeLogService.append("PAYROLL_RUN", runId, ChangeLogOperation.UPDATE, run, "LOCAL_DEVICE");

        // ── Post to General Ledger (FIXED: was never wired before) ──────────
        try {
            accountingBridge.postPayrollToGeneralLedger(run, shopId, disburserId);
        } catch (Exception e) {
            // GL posting failure should NOT roll back the disbursal — log and alert
            org.slf4j.LoggerFactory.getLogger(PayrollService.class)
                    .error("GL posting failed for payroll run {} — requires manual reconciliation: {}",
                            runId, e.getMessage());
        }

        // ── Publish event — triggers async payslip dispatch ─────────────────
        // (FIXED: was missing — payslips were never emailed after disbursal)
        payrollEventPublisher.onPayrollDisbursed(run, shopId);

        eventPublisher.publishEvent(new NotificationEvent(
                this, "payment", "Payroll Disbursed",
                "Payroll run " + run.getPayrollMonth() + "/" + run.getPayrollYear() + " disbursed successfully",
                "admin@shop.com", "/payroll/runs", "high"
        ));

        return payrollRunMapper.toDto(run);
    }

    public PayrollRunDto getPayrollRun(Long runId) {
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> new EntityNotFoundAppException("PayrollRun", runId));
        return payrollRunMapper.toDto(run);
    }

    public Page<PayrollRunDto> listPayrollRuns(Pageable pageable) {
        Long shopId = TenantContext.getCurrentShopId();
        return payrollRunRepository.findByShopIdOrderByPayrollYearDescPayrollMonthDesc(shopId, pageable)
                .map(payrollRunMapper::toDto);
    }

    // --- PHASE 2: PAYROLL SLIP MANAGEMENT ---

    @Transactional
    public PayrollSlipDto calculateAndCreateSlip(Long runId, Long employeeId, List<AttendanceRecord> attendance) {
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> new EntityNotFoundAppException("PayrollRun", runId));
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundAppException("Employee", employeeId));

        // getActiveSalaryStructure() removed — look up by salaryStructureId
        SalaryStructure structure = employee.getSalaryStructureId() != null
                ? salaryStructureRepository.findById(employee.getSalaryStructureId()).orElse(null)
                : null;
        if (structure == null) {
            throw new IllegalStateException("Employee has no active salary structure");
        }

        // PayrollCalculationEngine now returns PayrollCalculationResultDto
        PayrollCalculationResultDto result = calculationEngine
                .calculatePayrollForEmployee(employee, run, structure, attendance);

        PayrollSlip slip = new PayrollSlip();
        slip.setPayrollRun(run);
        slip.setEmployee(employee);
        slip.setShop(getCurrentShop());
        // attendanceSummary field removed from PayrollSlip entity — build it in mapper/PDF instead
        slip.setGrossEarnings(result.getGrossEarnings());
        slip.setNetSalary(result.getNetSalary());
        slip.setPayoutStatus(PayoutStatus.UNPAID);
        slip.setCreatedAt(LocalDate.now().atStartOfDay());

        // Set required fields
        slip.setSlipNumber(generatePayslipNumber(run.getId(), employeeId));
        slip.setTotalDays(run.getCalendarDays());
        slip.setWorkingDays(calculateWorkingDays(attendance, run));
        slip.setPresentDays(BigDecimal.valueOf(calculatePresentDays(attendance))); // Convert int to BigDecimal

        // Calculate monthly basic salary from salary structure components
        BigDecimal monthlyBaseSalary = structure.getComponents().stream()
                .filter(c -> c.getComponentType() == ComponentType.EARNING && c.getName().equalsIgnoreCase("BASIC"))
                .map(SalaryComponent::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        slip.setMonthlyBaseSalary(monthlyBaseSalary);
        slip.setTotalCTC(slip.getMonthlyBaseSalary().add(slip.getGrossEarnings())); // CTC = Base + Gross

        payrollSlipRepository.save(slip);

        for (PayrollCalculationEngine.ComponentAmount component : result.getEarnings()) {
            PayrollSlipItem item = new PayrollSlipItem();
            item.setPayrollSlip(slip);
            item.setComponentCode(component.componentCode);
            item.setComponentName(component.componentName);
            item.setAmount(component.amount);
            payrollSlipItemRepository.save(item);
        }
        for (PayrollCalculationEngine.ComponentAmount component : result.getDeductions()) {
            PayrollSlipItem item = new PayrollSlipItem();
            item.setPayrollSlip(slip);
            item.setComponentCode(component.componentCode);
            item.setComponentName(component.componentName);
            item.setAmount(component.amount);
            payrollSlipItemRepository.save(item);
        }

        changeLogService.append("PAYROLL_SLIP", slip.getId(), ChangeLogOperation.CREATE, slip, "LOCAL_DEVICE");
        return payrollSlipMapper.toDto(slip);
    }

    public Page<PayrollSlipDto> listPayrollSlipsForRun(Long runId, Pageable pageable) {
        return payrollSlipRepository.findByPayrollRunId(runId, pageable)
                .map(payrollSlipMapper::toDto);
    }

    public PayrollSlipDto getPayrollSlip(Long slipId) {
        PayrollSlip slip = payrollSlipRepository.findById(slipId)
                .orElseThrow(() -> new EntityNotFoundAppException("PayrollSlip", slipId));
        return payrollSlipMapper.toDto(slip);
    }

    @Transactional
    public PayrollSlipDto updatePayrollSlip(Long slipId, PayrollSlipDto dto) {
        PayrollSlip slip = payrollSlipRepository.findById(slipId)
                .orElseThrow(() -> new EntityNotFoundAppException("PayrollSlip", slipId));

        // Verify shop ownership - prevent cross-shop access
        Long currentShopId = TenantContext.getCurrentShopId();
        if (!slip.getShop().getId().equals(currentShopId)) {
            throw new IllegalArgumentException("Unauthorized: Payroll slip does not belong to current shop");
        }

        slip.setGrossEarnings(dto.getGrossEarnings());
        slip.setNetSalary(dto.getNetSalary());
        payrollSlipRepository.save(slip);
        changeLogService.append("PAYROLL_SLIP", slipId, ChangeLogOperation.UPDATE, slip, "LOCAL_DEVICE");
        return payrollSlipMapper.toDto(slip);
    }

    // --- PHASE 2: ATTENDANCE MANAGEMENT ---

    @Transactional
    public AttendanceRecordDto recordAttendance(Long employeeId, LocalDate date, AttendanceType type) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundAppException("Employee", employeeId));

        AttendanceRecord record = new AttendanceRecord();
        record.setEmployee(employee);
        record.setShop(getCurrentShop());
        record.setAttendanceDate(date);
        record.setAttendanceType(type);

        attendanceRecordRepository.save(record);
        changeLogService.append("ATTENDANCE", record.getId(), ChangeLogOperation.CREATE, record, "LOCAL_DEVICE");
        return new AttendanceRecordDto(record.getId(), employeeId, date, type.name());
    }

    public List<AttendanceRecord> getAttendanceForPeriod(Long employeeId, LocalDate startDate, LocalDate endDate) {
        return attendanceRecordRepository.findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(employeeId, startDate, endDate);
    }

    // --- PHASE 3: STATUTORY COMPLIANCE ---

    @Transactional
    public void applyStatutoryDeductions(Long runId) {
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> new EntityNotFoundAppException("PayrollRun", runId));

        List<PayrollSlip> slips = payrollSlipRepository.findByPayrollRunId(runId, Pageable.unpaged()).getContent();
        Long shopId = TenantContext.getCurrentShopId();

        for (PayrollSlip slip : slips) {
            StatutoryComplianceEngine.StatutoryDeductionsResult statutory =
                complianceEngine.calculateStatutoryDeductions(
                    slip.getEmployee(),
                    slip.getGrossEarnings(),
                    run.getPayrollMonth() != null ? Integer.parseInt(run.getPayrollMonth()) : 1,
                    run.getPayrollYear() != null ? run.getPayrollYear() : LocalDate.now().getYear(),
                    shopId
                );

            slip.setTotalDeductions(statutory.getTotalEmployeeDeductions());
            slip.setNetSalary(slip.getGrossEarnings().subtract(slip.getTotalDeductions()));
            payrollSlipRepository.save(slip);

            // Store statutory breakdown in items
            storeStatutoryItems(slip, statutory);
        }
    }

    private void storeStatutoryItems(PayrollSlip slip, StatutoryComplianceEngine.StatutoryDeductionsResult statutory) {
        if (statutory.getPfEmployeeDeduction().compareTo(BigDecimal.ZERO) > 0) {
            createSlipItem(slip, "PF_EE", "PF (Employee)", statutory.getPfEmployeeDeduction());
            createSlipItem(slip, "PF_ER", "PF (Employer)", statutory.getPfEmployerDeduction());
        }
        if (statutory.getEsiEmployeeDeduction().compareTo(BigDecimal.ZERO) > 0) {
            createSlipItem(slip, "ESI_EE", "ESI (Employee)", statutory.getEsiEmployeeDeduction());
            createSlipItem(slip, "ESI_ER", "ESI (Employer)", statutory.getEsiEmployerDeduction());
        }
        if (statutory.getProfessionalTaxDeduction().compareTo(BigDecimal.ZERO) > 0) {
            createSlipItem(slip, "PT", "Professional Tax", statutory.getProfessionalTaxDeduction());
        }
        if (statutory.getTdsDeduction().compareTo(BigDecimal.ZERO) > 0) {
            createSlipItem(slip, "TDS", "TDS", statutory.getTdsDeduction());
        }
    }

    private void createSlipItem(PayrollSlip slip, String code, String name, BigDecimal amount) {
        PayrollSlipItem item = new PayrollSlipItem();
        item.setPayrollSlip(slip);
        item.setComponentCode(code);
        item.setComponentName(name);
        item.setAmount(amount);
        payrollSlipItemRepository.save(item);
    }

    // --- PHASE 3: BANKING INTEGRATION ---

    @Transactional
    public BankingIntegrationService.BankingResult disburseViaRazorpayX(Long runId) {
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> new EntityNotFoundAppException("PayrollRun", runId));
        Long shopId = TenantContext.getCurrentShopId();

        return bankingService.disburseViaRazorpayX(run, shopId);
    }

    @Transactional
    public String generateNEFTBatchFile(Long runId) {
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> new EntityNotFoundAppException("PayrollRun", runId));
        return bankingService.generateNEFTBatchFile(run, TenantContext.getCurrentShopId());
    }

    @Transactional
    public String generateNACHBatchFile(Long runId) {
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> new EntityNotFoundAppException("PayrollRun", runId));
        return bankingService.generateNACHBatchFile(run, TenantContext.getCurrentShopId());
    }

    @Transactional
    public String generateECRFile(Long runId) {
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> new EntityNotFoundAppException("PayrollRun", runId));
        return bankingService.generateECRFile(run, TenantContext.getCurrentShopId());
    }

    public Page<BankTransaction> listBankTransactions(String status, Pageable pageable) {
        Long shopId = TenantContext.getCurrentShopId();
        return bankTransactionRepository.findByShopIdAndStatus(shopId, status, pageable);
    }

    public BankingIntegrationService.BankAccountValidation validateBankDetails(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundAppException("Employee", employeeId));
        return bankingService.validateBankDetails(employee);
    }

    // --- PHASE 4: EMPLOYEE SELF-SERVICE ---

    public Page<PayrollSlip> getEmployeePayslips(Long employeeId, Pageable pageable) {
        return essEngine.getEmployeePayslips(employeeId, pageable);
    }

    public PayrollSlip getPayslipDetails(Long slipId, Long employeeId) {
        return essEngine.getPayslipDetails(slipId, employeeId);
    }

    public Object getAttendanceSummary(Long employeeId, int month, int year) {
        return essEngine.getAttendanceSummary(employeeId, month, year);
    }

    public Object getTaxDeclaration(Long employeeId, String financialYear) {
        return essEngine.getTaxDeclaration(employeeId, financialYear);
    }

    @Transactional
    public Object submitTaxDeclaration(Long employeeId, com.desitech.vyaparsathi.payroll.dto.TaxDeclarationDto declarationDto) {
        if (declarationDto == null) {
            throw new IllegalArgumentException("Tax declaration data is required");
        }
        TaxDeclaration taxDecl = new TaxDeclaration();
        taxDecl.setEmployee(employeeRepository.findById(employeeId).orElseThrow(() -> new EntityNotFoundAppException("Employee", employeeId)));
        taxDecl.setLifeInsurancePremium(declarationDto.getSection80C());
        taxDecl.setMedicalInsurancePremium(declarationDto.getSection80D());
        taxDecl.setNpsContribution(declarationDto.getNPS());
        taxDecl.setTaxRegime(declarationDto.getTaxRegime());
        taxDecl.setFinancialYear(declarationDto.getFinancialYear());
        return essEngine.submitTaxDeclaration(employeeId, taxDecl);
    }

    @Transactional
    public Object requestAdvance(Long employeeId, BigDecimal amount, String reason) {
        return essEngine.requestAdvance(employeeId, amount, reason);
    }

    public Page<Object> getAdvanceRequests(Long employeeId, Pageable pageable) {
        return essEngine.getEmployeeAdvanceRequests(employeeId, pageable).map(Object.class::cast);
    }

    public Page<Object> getEmployeeLoans(Long employeeId, Pageable pageable) {
        return essEngine.getEmployeeLoans(employeeId, pageable).map(Object.class::cast);
    }

    public Page<Object> getDispatchHistory(Long employeeId, Pageable pageable) {
        return essEngine.getDispatchHistory(employeeId, pageable).map(Object.class::cast);
    }

    public Object getForm16Data(Long employeeId, String financialYear) {
        return essEngine.getForm16(employeeId, financialYear);
    }

    public Object getEssPreferences(Long employeeId) {
        return essEngine.getEssPreferences(employeeId);
    }

    @Transactional
    public Object updateEssPreferences(Long employeeId, java.util.Map<String, Object> preferencesDto) {
        if (preferencesDto == null || preferencesDto.isEmpty()) {
            throw new IllegalArgumentException("Preferences data is required");
        }
        EssPreferences prefs = new EssPreferences();
        prefs.setEmployee(employeeRepository.findById(employeeId).orElseThrow(() -> new EntityNotFoundAppException("Employee", employeeId)));
        if (preferencesDto.containsKey("emailNotifications")) {
            prefs.setEmailNotifications(Boolean.parseBoolean(preferencesDto.get("emailNotifications").toString()));
        }
        if (preferencesDto.containsKey("smsNotifications")) {
            prefs.setSmsNotifications(Boolean.parseBoolean(preferencesDto.get("smsNotifications").toString()));
        }
        if (preferencesDto.containsKey("whatsappNotifications")) {
            prefs.setWhatsappNotifications(Boolean.parseBoolean(preferencesDto.get("whatsappNotifications").toString()));
        }
        if (preferencesDto.containsKey("autoTaxCalculation")) {
            prefs.setAutoTaxCalculation(Boolean.parseBoolean(preferencesDto.get("autoTaxCalculation").toString()));
        }
        return essEngine.updateEssPreferences(employeeId, prefs);
    }

    @Transactional
    public void dispatchPayslipsForRun(Long runId) {
        dispatchService.dispatchPayslipsForRun(runId);
    }

    public Map<String, Long> getDispatchStats(Long runId) {
        return dispatchService.getDispatchStats(runId);
    }

    // --- PDF & ACCOUNTING OPERATIONS ---

    @Transactional
    public byte[] generatePayslipPDF(Long slipId) {
        PayrollSlip slip = payrollSlipRepository.findById(slipId)
                .orElseThrow(() -> new EntityNotFoundAppException("PayrollSlip", slipId));
        return pdfGenerator.generatePayslipPDF(slip);
    }

    @Transactional
    public byte[] generateForm16PDF(Long employeeId, String financialYear) {
        if (form16DataRepository == null) {
            throw new RuntimeException("Form16DataRepository not available");
        }
        Form16Data form16 = form16DataRepository.findByEmployeeIdAndFinancialYear(employeeId, financialYear)
                .orElseThrow(() -> new EntityNotFoundAppException("Form16", employeeId));
        return pdfGenerator.generateForm16PDF(form16);
    }

    @Transactional
    public com.desitech.vyaparsathi.payroll.entity.JournalEntryEntity postPayrollToGL(Long runId) {
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> new EntityNotFoundAppException("PayrollRun", runId));
        Long shopId = TenantContext.getCurrentShopId();
        return accountingBridge.postPayrollToGeneralLedger(run, shopId);
    }

    // --- FORM 16 GENERATION ---

    @Transactional
    public void generateForm16ForEmployee(Long employeeId, String financialYear) {
        if (form16DataRepository == null) return;

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundAppException("Employee", employeeId));

        Form16Data form16 = new Form16Data();
        form16.setEmployee(employee);
        form16.setFinancialYear(financialYear);
        form16.setPanNumber(employee.getPanNumber());
        form16.setName(employee.getFirstName() + (employee.getLastName() != null ? " " + employee.getLastName() : ""));
        form16.setAddress(null); // address field removed from Employee
        form16.setEmployerName("Your Company");
        form16.setIsVerified(false);

        form16DataRepository.save(form16);
        changeLogService.append("FORM16", form16.getId(), ChangeLogOperation.CREATE, form16, "LOCAL_DEVICE");
    }

    @Transactional
    public void generateForm16ForAllEmployees(String financialYear) {
        Long shopId = TenantContext.getCurrentShopId();
        List<Employee> employees = employeeRepository.findByShopIdAndIsActiveTrueAndEmploymentStatus(
                shopId, com.desitech.vyaparsathi.payroll.enums.EmploymentStatus.ACTIVE);

        for (Employee emp : employees) {
            generateForm16ForEmployee(emp.getId(), financialYear);
        }
    }

    // Slip ownership check for EMPLOYEE role — prevents cross-employee slip access
    public PayrollSlipDto getPayrollSlipForEmployee(Long slipId, Long employeeId) {
        PayrollSlip slip = payrollSlipRepository.findById(slipId)
                .orElseThrow(() -> new EntityNotFoundAppException("PayrollSlip", slipId));
        if (!slip.getEmployee().getId().equals(employeeId)) {
            throw new SecurityException("Access denied: slip " + slipId + " does not belong to employee " + employeeId);
        }
        return payrollSlipMapper.toDto(slip);
    }
}

