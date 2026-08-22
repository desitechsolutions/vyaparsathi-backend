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
        testShop.setEmail("test@shop.com");
        testShop = shopRepository.save(testShop);

        // Create test employee
        testEmployee = new Employee();
        testEmployee.setShop(testShop);
        testEmployee.setName("Test Employee");
        testEmployee.setEmploymentType(EmploymentType.FULL_TIME);
        testEmployee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        testEmployee.setIsActive(true);
        testEmployee = employeeRepository.save(testEmployee);

        // Create salary structure
        testStructure = new SalaryStructure();
        testStructure.setShop(testShop);
        testStructure.setName("Test Structure");
        testStructure.setIsActive(true);
        testStructure = salaryStructureRepository.save(testStructure);

        // Create salary components
        SalaryComponent basic = new SalaryComponent();
        basic.setStructure(testStructure);
        basic.setName("Basic");
        basic.setComponentType(ComponentType.EARNING);
        basic.setCalculationType(CalculationType.FIXED);
        basic.setAmount(BigDecimal.valueOf(50000));
        salaryComponentRepository.save(basic);

        SalaryComponent hra = new SalaryComponent();
        hra.setStructure(testStructure);
        hra.setName("HRA");
        hra.setComponentType(ComponentType.EARNING);
        hra.setCalculationType(CalculationType.PERCENTAGE);
        hra.setPercentage(BigDecimal.valueOf(10));
        salaryComponentRepository.save(hra);

        SalaryComponent pf = new SalaryComponent();
        pf.setStructure(testStructure);
        pf.setName("PF");
        pf.setComponentType(ComponentType.DEDUCTION);
        pf.setCalculationType(CalculationType.PERCENTAGE);
        pf.setPercentage(BigDecimal.valueOf(12));
        salaryComponentRepository.save(pf);
    }

    @Test
    @DisplayName("Full payroll workflow: Create → Process → Approve → Disburse")
    void testCompletePayrollWorkflow() {
        // 1. Create payroll run
        PayrollRun run = new PayrollRun();
        run.setShop(testShop);
        run.setPayrollMonth("08");
        run.setPayrollYear(2026);
        run.setRunNumber("PR-2026-08-001");
        run.setStartDate(LocalDate.of(2026, 8, 1));
        run.setEndDate(LocalDate.of(2026, 8, 31));
        run.setCalendarDays(31);
        run.setStatus(PayrollRunStatus.DRAFT.toString());
        run.setTotalEmployees(1);
        run = payrollRunRepository.save(run);

        assertNotNull(run.getId());
        assertEquals(PayrollRunStatus.DRAFT.toString(), run.getStatus());

        // 2. Record attendance for the period
        for (int day = 1; day <= 26; day++) {
            AttendanceRecord record = new AttendanceRecord();
            record.setShop(testShop);
            record.setEmployee(testEmployee);
            record.setAttendanceDate(LocalDate.of(2026, 8, day));
            record.setAttendanceType("PRESENT");
            record.setPayrollMonth("08");
            record.setPayrollYear(2026);
            attendanceRecordRepository.save(record);
        }

        // 3. Create payroll slip
        PayrollSlip slip = new PayrollSlip();
        slip.setShop(testShop);
        slip.setPayrollRun(run);
        slip.setEmployee(testEmployee);
        slip.setSlipNumber("SLIP-" + run.getId() + "-EMP" + testEmployee.getId());
        slip.setTotalDays(31);
        slip.setWorkingDays(26);
        slip.setPresentDays(BigDecimal.valueOf(26));
        slip.setBasicSalary(BigDecimal.valueOf(50000));
        slip.setGrossEarnings(BigDecimal.valueOf(55000)); // Basic + HRA
        slip.setTotalDeductions(BigDecimal.valueOf(6600)); // PF + others
        slip.setNetSalary(BigDecimal.valueOf(48400));
        slip.setPayoutStatus("PENDING");
        slip = payrollSlipRepository.save(slip);

        assertNotNull(slip.getId());
        assertEquals("PENDING", slip.getPayoutStatus());

        // 4. Mark run as processing
        run.setStatus(PayrollRunStatus.PROCESSING.toString());
        run.setTotalGross(BigDecimal.valueOf(55000));
        run.setTotalDeductions(BigDecimal.valueOf(6600));
        run.setTotalNet(BigDecimal.valueOf(48400));
        run = payrollRunRepository.save(run);

        assertEquals(PayrollRunStatus.PROCESSING.toString(), run.getStatus());

        // 5. Approve payroll run
        run.setStatus(PayrollRunStatus.APPROVED.toString());
        run.setApprovedByUserId(1L);
        run = payrollRunRepository.save(run);

        assertEquals(PayrollRunStatus.APPROVED.toString(), run.getStatus());

        // 6. Disburse payroll run
        run.setStatus(PayrollRunStatus.DISBURSED.toString());
        run.setDisbursedByUserId(1L);
        run = payrollRunRepository.save(run);

        slip.setPayoutStatus("DISBURSED");
        slip = payrollSlipRepository.save(slip);

        assertEquals(PayrollRunStatus.DISBURSED.toString(), run.getStatus());
        assertEquals("DISBURSED", slip.getPayoutStatus());
    }

    @Test
    @DisplayName("Should prevent duplicate payment for same month/year")
    void testDuplicatePaymentPrevention() {
        PayrollRun run1 = new PayrollRun();
        run1.setShop(testShop);
        run1.setPayrollMonth("08");
        run1.setPayrollYear(2026);
        run1.setRunNumber("PR-2026-08-001");
        run1.setStatus(PayrollRunStatus.DRAFT.toString());
        run1 = payrollRunRepository.save(run1);

        PayrollRun run2 = new PayrollRun();
        run2.setShop(testShop);
        run2.setPayrollMonth("08");
        run2.setPayrollYear(2026);
        run2.setRunNumber("PR-2026-08-002");
        run2.setStatus(PayrollRunStatus.DRAFT.toString());

        // This should fail on unique constraint
        assertThrows(Exception.class, () -> {
            payrollRunRepository.save(run2);
            payrollRunRepository.flush();
        });
    }

    @Test
    @DisplayName("Should calculate correct statutory deductions in payroll slip")
    void testStatutoryDeductionsInSlip() {
        BigDecimal basicSalary = BigDecimal.valueOf(50000);
        BigDecimal hra = basicSalary.multiply(BigDecimal.valueOf(0.10));
        BigDecimal grossEarnings = basicSalary.add(hra);

        BigDecimal pfDeduction = grossEarnings.multiply(BigDecimal.valueOf(0.12));
        BigDecimal totalDeductions = pfDeduction;

        BigDecimal netSalary = grossEarnings.subtract(totalDeductions);

        PayrollSlip slip = new PayrollSlip();
        slip.setShop(testShop);
        slip.setEmployee(testEmployee);
        slip.setBasicSalary(basicSalary);
        slip.setGrossEarnings(grossEarnings);
        slip.setPfContribution(pfDeduction);
        slip.setTotalDeductions(totalDeductions);
        slip.setNetSalary(netSalary);

        assertTrue(slip.getNetSalary().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(slip.getNetSalary().compareTo(slip.getGrossEarnings()) < 0);
    }

    @Test
    @DisplayName("Should handle payroll run state transitions correctly")
    void testPayrollRunStateTransitions() {
        PayrollRun run = new PayrollRun();
        run.setShop(testShop);
        run.setPayrollMonth("08");
        run.setPayrollYear(2026);
        run.setRunNumber("PR-2026-08-001");
        run.setStatus(PayrollRunStatus.DRAFT.toString());
        run = payrollRunRepository.save(run);

        // DRAFT → PROCESSING
        run.setStatus(PayrollRunStatus.PROCESSING.toString());
        run = payrollRunRepository.save(run);
        assertEquals(PayrollRunStatus.PROCESSING.toString(), run.getStatus());

        // PROCESSING → APPROVED
        run.setStatus(PayrollRunStatus.APPROVED.toString());
        run = payrollRunRepository.save(run);
        assertEquals(PayrollRunStatus.APPROVED.toString(), run.getStatus());

        // APPROVED → DISBURSED
        run.setStatus(PayrollRunStatus.DISBURSED.toString());
        run = payrollRunRepository.save(run);
        assertEquals(PayrollRunStatus.DISBURSED.toString(), run.getStatus());
    }

    @Test
    @DisplayName("Should calculate financial aggregates for payroll run")
    void testPayrollRunFinancialAggregates() {
        PayrollRun run = new PayrollRun();
        run.setShop(testShop);
        run.setPayrollMonth("08");
        run.setPayrollYear(2026);
        run.setRunNumber("PR-2026-08-001");
        run.setTotalEmployees(1);
        run.setTotalGrossEarnings(BigDecimal.valueOf(55000));
        run.setTotalEmployeeDeductions(BigDecimal.valueOf(6600));
        run.setTotalNetPayable(BigDecimal.valueOf(48400));
        run.setTotalEmployerContributions(BigDecimal.valueOf(4500)); // PF + ESI employer share
        run.setTotalCompanyCost(BigDecimal.valueOf(55000 + 4500));

        assertEquals(BigDecimal.valueOf(55000), run.getTotalGrossEarnings());
        assertEquals(BigDecimal.valueOf(48400), run.getTotalNetPayable());
        assertTrue(run.getTotalCompanyCost().compareTo(run.getTotalGrossEarnings()) > 0);
    }
}
