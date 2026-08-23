package com.desitech.vyaparsathi.payroll.integration;

import com.desitech.vyaparsathi.payroll.entity.*;
import com.desitech.vyaparsathi.payroll.enums.*;
import com.desitech.vyaparsathi.payroll.repository.*;
import com.desitech.vyaparsathi.payroll.service.*;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Payroll Workflow Integration Tests")
@Transactional
class PayrollWorkflowIntegrationTest {

    @Autowired private PayrollService payrollService;
    @Autowired private PayrollRunRepository payrollRunRepository;
    @Autowired private PayrollSlipRepository payrollSlipRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private SalaryStructureRepository salaryStructureRepository;
    @Autowired private SalaryComponentRepository salaryComponentRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private ShopRepository shopRepository;

    private Shop testShop;
    private Employee testEmployee;
    private SalaryStructure testStructure;

    @BeforeEach
    void setUp() {
        // Create test shop
        testShop = new Shop();
        testShop.setName("Test Shop");
        testShop.setCode("TESTSHOP" + System.currentTimeMillis());
        testShop.setState("Maharashtra");
        testShop.setStateCode("27");
        testShop.setEmail("test@shop.com");
        testShop.setPhone("9999999999");
        testShop = shopRepository.save(testShop);

        // Create test employee with correct field names
        testEmployee = new Employee();
        testEmployee.setShop(testShop);
        testEmployee.setEmployeeCode("EMP001");
        testEmployee.setFirstName("Test");
        testEmployee.setLastName("Employee");
        testEmployee.setPhone("9876543210");
        testEmployee.setJoiningDate(LocalDate.of(2025, 1, 1));
        testEmployee.setEmploymentType(EmploymentType.FULL_TIME);
        testEmployee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        testEmployee.setMonthlyCTC(BigDecimal.valueOf(55000));
        testEmployee.setIsActive(true);
        testEmployee = employeeRepository.save(testEmployee);

        // Create salary structure with correct field name: structureName
        testStructure = new SalaryStructure();
        testStructure.setShop(testShop);
        testStructure.setStructureName("Test Structure");
        testStructure.setStructureCode("STD001");
        testStructure.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        testStructure.setIsActive(true);
        testStructure = salaryStructureRepository.save(testStructure);

        // Link structure to employee
        testEmployee.setSalaryStructureId(testStructure.getId());
        testEmployee = employeeRepository.save(testEmployee);

        // Create salary components with correct field names: componentName, calculationValue
        SalaryComponent basic = new SalaryComponent();
        basic.setShopId(testShop.getId());
        basic.setStructure(testStructure);
        basic.setComponentName("Basic");
        basic.setComponentCode("BASIC");
        basic.setComponentType(ComponentType.EARNING);
        basic.setCalculationType(CalculationType.FLAT_AMOUNT);
        basic.setCalculationValue(BigDecimal.valueOf(50000));
        basic.setIsActive(true);
        basic.setIsTaxable(true);
        basic.setAffectsPF(true);
        basic.setAffectsESI(true);
        basic.setOrderSequence(1);
        salaryComponentRepository.save(basic);

        SalaryComponent hra = new SalaryComponent();
        hra.setShopId(testShop.getId());
        hra.setStructure(testStructure);
        hra.setComponentName("HRA");
        hra.setComponentCode("HRA");
        hra.setComponentType(ComponentType.EARNING);
        hra.setCalculationType(CalculationType.PERCENTAGE_OF_BASIC);
        hra.setCalculationValue(BigDecimal.valueOf(10));
        hra.setIsActive(true);
        hra.setIsTaxable(true);
        hra.setAffectsPF(false);
        hra.setAffectsESI(true);
        hra.setOrderSequence(2);
        salaryComponentRepository.save(hra);
    }

    @Test
    @DisplayName("Full payroll workflow: Create run with correct field values")
    void testPayrollRunCreation() {
        PayrollRun run = new PayrollRun();
        run.setShop(testShop);
        run.setPayrollMonth("08");
        run.setPayrollYear(2026);
        run.setRunNumber("PR-2026-08-001");
        run.setStartDate(LocalDate.of(2026, 8, 1));
        run.setEndDate(LocalDate.of(2026, 8, 31));
        run.setCalendarDays(31);
        run.setStatus(PayrollRunStatus.DRAFT);
        run.setTotalEmployees(1);
        run.setTotalGrossEarnings(BigDecimal.ZERO);
        run.setTotalNetPayable(BigDecimal.ZERO);
        run.setTotalEmployeeDeductions(BigDecimal.ZERO);
        run.setTotalEmployerContributions(BigDecimal.ZERO);
        run.setTotalCompanyCost(BigDecimal.ZERO);
        run = payrollRunRepository.save(run);

        assertNotNull(run.getId());
        assertEquals(PayrollRunStatus.DRAFT, run.getStatus());
        assertEquals("08", run.getPayrollMonth());
        assertEquals(2026, run.getPayrollYear());
        assertEquals("PR-2026-08-001", run.getRunNumber());
    }

    @Test
    @DisplayName("Should record attendance with correct field names")
    void testAttendanceRecording() {
        AttendanceRecord record = new AttendanceRecord();
        record.setShop(testShop);
        record.setEmployee(testEmployee);
        record.setAttendanceDate(LocalDate.of(2026, 8, 1));
        record.setAttendanceType(AttendanceType.PRESENT);
        record = attendanceRecordRepository.save(record);

        assertNotNull(record.getId());
        assertEquals(AttendanceType.PRESENT, record.getAttendanceType());
        assertEquals(LocalDate.of(2026, 8, 1), record.getAttendanceDate());
    }

    @Test
    @DisplayName("Should query attendance by date range using new method name")
    void testAttendanceQueryByDateRange() {
        // Save 5 records
        for (int day = 1; day <= 5; day++) {
            AttendanceRecord record = new AttendanceRecord();
            record.setShop(testShop);
            record.setEmployee(testEmployee);
            record.setAttendanceDate(LocalDate.of(2026, 8, day));
            record.setAttendanceType(AttendanceType.PRESENT);
            attendanceRecordRepository.save(record);
        }

        // Query using new method name
        List<AttendanceRecord> records = attendanceRecordRepository
            .findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
                testEmployee.getId(),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 5)
            );

        assertEquals(5, records.size());
    }

    @Test
    @DisplayName("Should create payroll slip with all required NOT NULL fields")
    void testPayrollSlipCreation() {
        PayrollRun run = buildRun("PR-2026-08-001");

        PayrollSlip slip = new PayrollSlip();
        slip.setShop(testShop);
        slip.setPayrollRun(run);
        slip.setEmployee(testEmployee);
        slip.setSlipNumber("SLIP-" + run.getId() + "-EMP" + testEmployee.getId());
        slip.setTotalDays(31);
        slip.setWorkingDays(26);
        slip.setPresentDays(BigDecimal.valueOf(26));
        slip.setMonthlyBaseSalary(BigDecimal.valueOf(50000));
        slip.setGrossEarnings(BigDecimal.valueOf(55000));
        slip.setTotalDeductions(BigDecimal.valueOf(6600));
        slip.setNetSalary(BigDecimal.valueOf(48400));
        slip.setTotalCTC(BigDecimal.valueOf(59500));
        slip.setPayoutStatus(PayoutStatus.UNPAID);
        slip.setEmployerContributions(BigDecimal.ZERO);
        slip = payrollSlipRepository.save(slip);

        assertNotNull(slip.getId());
        assertEquals(PayoutStatus.UNPAID, slip.getPayoutStatus());
        assertTrue(slip.getGrossEarnings().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(slip.getNetSalary().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("State transitions: DRAFT → PROCESSING → PENDING_APPROVAL → APPROVED → DISBURSED")
    void testPayrollRunStateTransitions() {
        PayrollRun run = buildRun("PR-2026-08-001");

        run.setStatus(PayrollRunStatus.PROCESSING);
        run = payrollRunRepository.save(run);
        assertEquals(PayrollRunStatus.PROCESSING, run.getStatus());

        run.setStatus(PayrollRunStatus.PENDING_APPROVAL);
        run = payrollRunRepository.save(run);
        assertEquals(PayrollRunStatus.PENDING_APPROVAL, run.getStatus());

        run.setStatus(PayrollRunStatus.APPROVED);
        run.setApprovedByUserId(1L);
        run.setApprovedAt(LocalDate.now().atStartOfDay());
        run = payrollRunRepository.save(run);
        assertEquals(PayrollRunStatus.APPROVED, run.getStatus());

        run.setStatus(PayrollRunStatus.DISBURSED);
        run.setDisbursedAt(LocalDate.now().atStartOfDay());
        run = payrollRunRepository.save(run);
        assertEquals(PayrollRunStatus.DISBURSED, run.getStatus());
    }

    @Test
    @DisplayName("Financial aggregates on PayrollRun should be set correctly")
    void testPayrollRunFinancialAggregates() {
        PayrollRun run = buildRun("PR-2026-08-001");
        run.setTotalEmployees(5);
        run.setTotalGrossEarnings(BigDecimal.valueOf(275000)); // 5 x 55000
        run.setTotalEmployeeDeductions(BigDecimal.valueOf(33000));
        run.setTotalNetPayable(BigDecimal.valueOf(242000));
        run.setTotalEmployerContributions(BigDecimal.valueOf(22500));
        run.setTotalCompanyCost(BigDecimal.valueOf(297500));
        run = payrollRunRepository.save(run);

        assertEquals(5, run.getTotalEmployees());
        assertEquals(BigDecimal.valueOf(275000), run.getTotalGrossEarnings());
        assertTrue(run.getTotalCompanyCost().compareTo(run.getTotalGrossEarnings()) > 0);
        assertTrue(run.getTotalNetPayable().compareTo(run.getTotalGrossEarnings()) < 0);
    }

    // ── Helper ───────────────────────────────────────────────────────────────
    private PayrollRun buildRun(String runNumber) {
        PayrollRun run = new PayrollRun();
        run.setShop(testShop);
        run.setPayrollMonth("08");
        run.setPayrollYear(2026);
        run.setRunNumber(runNumber);
        run.setStartDate(LocalDate.of(2026, 8, 1));
        run.setEndDate(LocalDate.of(2026, 8, 31));
        run.setCalendarDays(31);
        run.setStatus(PayrollRunStatus.DRAFT);
        run.setTotalEmployees(1);
        run.setTotalGrossEarnings(BigDecimal.ZERO);
        run.setTotalNetPayable(BigDecimal.ZERO);
        run.setTotalEmployeeDeductions(BigDecimal.ZERO);
        run.setTotalEmployerContributions(BigDecimal.ZERO);
        run.setTotalCompanyCost(BigDecimal.ZERO);
        return payrollRunRepository.save(run);
    }
}
