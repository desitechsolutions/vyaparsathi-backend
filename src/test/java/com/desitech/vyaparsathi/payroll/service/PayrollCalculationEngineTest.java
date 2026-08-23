package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.payroll.dto.PayrollCalculationResultDto;
import com.desitech.vyaparsathi.payroll.entity.*;
import com.desitech.vyaparsathi.payroll.enums.*;
import com.desitech.vyaparsathi.payroll.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Payroll Calculation Engine Tests")
class PayrollCalculationEngineTest {

    @InjectMocks private PayrollCalculationEngine calculationEngine;

    private Employee testEmployee;
    private PayrollRun testRun;
    private SalaryStructure testStructure;
    private List<SalaryComponent> testComponents;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup test employee — use correct field setters (firstName, lastName)
        testEmployee = new Employee();
        testEmployee.setId(1L);
        testEmployee.setFirstName("John");
        testEmployee.setLastName("Doe");
        testEmployee.setEmploymentType(EmploymentType.FULL_TIME);
        testEmployee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        testEmployee.setMonthlyCTC(BigDecimal.valueOf(55000));

        // Setup test payroll run — status is PayrollRunStatus enum, not String
        testRun = new PayrollRun();
        testRun.setId(1L);
        testRun.setPayrollMonth("08");
        testRun.setPayrollYear(2026);
        testRun.setStartDate(LocalDate.of(2026, 8, 1));
        testRun.setEndDate(LocalDate.of(2026, 8, 31));
        testRun.setCalendarDays(31);
        testRun.setStatus(PayrollRunStatus.PROCESSING);

        // Setup salary structure — correct field: structureName
        testStructure = new SalaryStructure();
        testStructure.setId(1L);
        testStructure.setStructureName("Standard Structure");
        testStructure.setIsActive(true);

        // Setup components — correct fields: componentName, calculationValue
        testComponents = new ArrayList<>();

        SalaryComponent basicComponent = new SalaryComponent();
        basicComponent.setId(1L);
        basicComponent.setComponentName("Basic");
        basicComponent.setComponentCode("BASIC");
        basicComponent.setComponentType(ComponentType.EARNING);
        basicComponent.setCalculationType(CalculationType.FLAT_AMOUNT);
        basicComponent.setCalculationValue(BigDecimal.valueOf(50000));
        basicComponent.setIsActive(true);
        basicComponent.setOrderSequence(1);
        testComponents.add(basicComponent);

        SalaryComponent hraComponent = new SalaryComponent();
        hraComponent.setId(2L);
        hraComponent.setComponentName("HRA");
        hraComponent.setComponentCode("HRA");
        hraComponent.setComponentType(ComponentType.EARNING);
        hraComponent.setCalculationType(CalculationType.PERCENTAGE_OF_BASIC);
        hraComponent.setCalculationValue(BigDecimal.valueOf(10));
        hraComponent.setIsActive(true);
        hraComponent.setOrderSequence(2);
        testComponents.add(hraComponent);

        SalaryComponent pfComponent = new SalaryComponent();
        pfComponent.setId(3L);
        pfComponent.setComponentName("PF");
        pfComponent.setComponentCode("PF");
        pfComponent.setComponentType(ComponentType.DEDUCTION);
        pfComponent.setCalculationType(CalculationType.PERCENTAGE_OF_BASIC);
        pfComponent.setCalculationValue(BigDecimal.valueOf(12));
        pfComponent.setIsActive(true);
        pfComponent.setOrderSequence(3);
        testComponents.add(pfComponent);
    }

    @Test
    @DisplayName("Should calculate gross earnings correctly for full attendance")
    void testGrossEarningsCalculation() {
        // 26 days PRESENT out of 31 calendar days
        List<AttendanceRecord> attendance = buildAttendance(26, AttendanceType.PRESENT);

        // Assign structure to employee
        testEmployee.setSalaryStructureId(testStructure.getId());
        testStructure.setComponents(testComponents.subList(0, 2)); // Basic + HRA

        PayrollCalculationResultDto result = calculationEngine.calculatePayrollForEmployee(
            testEmployee, testRun, testStructure, attendance
        );

        assertNotNull(result);
        assertTrue(result.getGrossEarnings().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Should apply Loss of Pay (LOP) — 6 absent days reduce salary")
    void testLossOfPayCalculation() {
        // 20 present, 6 absent = LOP
        List<AttendanceRecord> attendance = buildAttendance(20, AttendanceType.PRESENT);
        testStructure.setComponents(testComponents.subList(0, 2));

        PayrollCalculationResultDto resultFull = calculationEngine.calculatePayrollForEmployee(
            testEmployee, testRun, testStructure, buildAttendance(26, AttendanceType.PRESENT)
        );
        PayrollCalculationResultDto resultLOP = calculationEngine.calculatePayrollForEmployee(
            testEmployee, testRun, testStructure, attendance
        );

        assertNotNull(resultLOP);
        // LOP salary must be strictly less than full attendance salary
        assertTrue(resultLOP.getGrossEarnings().compareTo(resultFull.getGrossEarnings()) < 0);
    }

    @Test
    @DisplayName("Should calculate net salary = gross - deductions")
    void testNetSalaryCalculation() {
        testStructure.setComponents(testComponents); // All 3 components
        List<AttendanceRecord> attendance = buildAttendance(26, AttendanceType.PRESENT);

        PayrollCalculationResultDto result = calculationEngine.calculatePayrollForEmployee(
            testEmployee, testRun, testStructure, attendance
        );

        assertNotNull(result);
        BigDecimal expectedNet = result.getGrossEarnings().subtract(result.getTotalDeductions());
        assertEquals(
            expectedNet.setScale(2, java.math.RoundingMode.HALF_UP),
            result.getNetSalary().setScale(2, java.math.RoundingMode.HALF_UP)
        );
    }

    @Test
    @DisplayName("Should handle edge case: zero attendance — zero gross")
    void testZeroAttendanceCalculation() {
        testStructure.setComponents(testComponents.subList(0, 2));

        PayrollCalculationResultDto result = calculationEngine.calculatePayrollForEmployee(
            testEmployee, testRun, testStructure, Collections.emptyList()
        );

        assertNotNull(result);
        assertEquals(0, result.getGrossEarnings().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Should return non-null result DTO with all required fields set")
    void testResultDtoFields() {
        testStructure.setComponents(testComponents.subList(0, 2));

        PayrollCalculationResultDto result = calculationEngine.calculatePayrollForEmployee(
            testEmployee, testRun, testStructure, buildAttendance(26, AttendanceType.PRESENT)
        );

        assertNotNull(result);
        assertNotNull(result.getGrossEarnings());
        assertNotNull(result.getTotalDeductions());
        assertNotNull(result.getNetSalary());
        assertEquals(testEmployee.getId(), result.getEmployeeId());
        assertNotNull(result.getEarnings());
    }

    @Test
    @DisplayName("Should not include inactive components in calculation")
    void testInactiveComponentsExcluded() {
        SalaryComponent inactive = new SalaryComponent();
        inactive.setId(99L);
        inactive.setComponentName("Bonus");
        inactive.setComponentCode("BONUS");
        inactive.setComponentType(ComponentType.EARNING);
        inactive.setCalculationType(CalculationType.FLAT_AMOUNT);
        inactive.setCalculationValue(BigDecimal.valueOf(10000));
        inactive.setIsActive(false); // inactive — must be excluded
        inactive.setOrderSequence(5);

        List<SalaryComponent> mixedComponents = new ArrayList<>(testComponents.subList(0, 2));
        mixedComponents.add(inactive);
        testStructure.setComponents(mixedComponents);

        PayrollCalculationResultDto withInactive = calculationEngine.calculatePayrollForEmployee(
            testEmployee, testRun, testStructure, buildAttendance(26, AttendanceType.PRESENT)
        );

        // Active-only structure
        testStructure.setComponents(testComponents.subList(0, 2));
        PayrollCalculationResultDto withoutInactive = calculationEngine.calculatePayrollForEmployee(
            testEmployee, testRun, testStructure, buildAttendance(26, AttendanceType.PRESENT)
        );

        assertEquals(
            withoutInactive.getGrossEarnings().setScale(2, java.math.RoundingMode.HALF_UP),
            withInactive.getGrossEarnings().setScale(2, java.math.RoundingMode.HALF_UP)
        );
    }

    @Test
    @DisplayName("Should handle PAID_LEAVE — does not reduce gross salary")
    void testPaidLeaveDoesNotReduceSalary() {
        testStructure.setComponents(testComponents.subList(0, 2));

        List<AttendanceRecord> fullPresent = buildAttendance(26, AttendanceType.PRESENT);
        List<AttendanceRecord> withPaidLeave = new ArrayList<>(buildAttendance(24, AttendanceType.PRESENT));
        withPaidLeave.addAll(buildAttendance(2, AttendanceType.PAID_LEAVE));

        PayrollCalculationResultDto resultPresent = calculationEngine.calculatePayrollForEmployee(
            testEmployee, testRun, testStructure, fullPresent
        );
        PayrollCalculationResultDto resultPaidLeave = calculationEngine.calculatePayrollForEmployee(
            testEmployee, testRun, testStructure, withPaidLeave
        );

        // Paid leave days should count same as present — no salary reduction
        assertEquals(
            resultPresent.getGrossEarnings().setScale(2, java.math.RoundingMode.HALF_UP),
            resultPaidLeave.getGrossEarnings().setScale(2, java.math.RoundingMode.HALF_UP)
        );
    }

    // ── Helper ──────────────────────────────────────────────────────────────
    private List<AttendanceRecord> buildAttendance(int count, AttendanceType type) {
        List<AttendanceRecord> records = new ArrayList<>();
        LocalDate date = testRun.getStartDate();
        for (int i = 0; i < count; i++) {
            AttendanceRecord record = new AttendanceRecord();
            record.setEmployee(testEmployee);
            record.setAttendanceDate(date.plusDays(i));
            record.setAttendanceType(type);
            records.add(record);
        }
        return records;
    }
}
