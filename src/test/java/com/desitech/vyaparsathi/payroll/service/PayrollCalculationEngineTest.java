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

    @Mock private PayrollSlipRepository payrollSlipRepository;
    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private SalaryComponentRepository salaryComponentRepository;
    @Mock private StaffLoanRepository staffLoanRepository;

    @InjectMocks private PayrollCalculationEngine calculationEngine;

    private Employee testEmployee;
    private PayrollRun testRun;
    private SalaryStructure testStructure;
    private List<SalaryComponent> testComponents;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup test employee
        testEmployee = new Employee();
        testEmployee.setId(1L);
        testEmployee.setName("John Doe");
        testEmployee.setEmploymentType(EmploymentType.FULL_TIME);
        testEmployee.setEmploymentStatus(EmploymentStatus.ACTIVE);

        // Setup test payroll run
        testRun = new PayrollRun();
        testRun.setId(1L);
        testRun.setPayrollMonth("08");
        testRun.setPayrollYear(2026);
        testRun.setStartDate(LocalDate.of(2026, 8, 1));
        testRun.setEndDate(LocalDate.of(2026, 8, 31));
        testRun.setCalendarDays(31);
        testRun.setStatus("PROCESSING");

        // Setup salary structure
        testStructure = new SalaryStructure();
        testStructure.setId(1L);
        testStructure.setName("Standard Structure");
        testStructure.setIsActive(true);

        // Setup components
        testComponents = new ArrayList<>();

        SalaryComponent basicComponent = new SalaryComponent();
        basicComponent.setId(1L);
        basicComponent.setName("Basic");
        basicComponent.setComponentType(ComponentType.EARNING);
        basicComponent.setCalculationType(CalculationType.FIXED);
        basicComponent.setAmount(BigDecimal.valueOf(50000));
        testComponents.add(basicComponent);

        SalaryComponent hraComponent = new SalaryComponent();
        hraComponent.setId(2L);
        hraComponent.setName("HRA");
        hraComponent.setComponentType(ComponentType.EARNING);
        hraComponent.setCalculationType(CalculationType.PERCENTAGE);
        hraComponent.setPercentage(BigDecimal.valueOf(10));
        testComponents.add(hraComponent);

        SalaryComponent pfComponent = new SalaryComponent();
        pfComponent.setId(3L);
        pfComponent.setName("PF");
        pfComponent.setComponentType(ComponentType.DEDUCTION);
        pfComponent.setCalculationType(CalculationType.PERCENTAGE);
        pfComponent.setPercentage(BigDecimal.valueOf(12));
        testComponents.add(pfComponent);
    }

    @Test
    @DisplayName("Should calculate gross earnings correctly")
    void testGrossEarningsCalculation() {
        // Setup attendance: 22 present days out of 26 working days
        List<AttendanceRecord> attendance = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            AttendanceRecord record = new AttendanceRecord();
            record.setAttendanceType("PRESENT");
            attendance.add(record);
        }

        when(salaryComponentRepository.findByStructureId(testStructure.getId()))
            .thenReturn(testComponents.subList(0, 2)); // Basic + HRA

        PayrollCalculationResultDto result = calculationEngine.calculatePayrollForEmployee(
            testEmployee, testRun, testStructure, attendance
        );

        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(55000), result.getGrossEarnings());
        assertTrue(result.getGrossEarnings().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Should apply Loss of Pay (LOP) correctly")
    void testLossOfPayCalculation() {
        // Setup: 20 present days out of 26 working days = 6 LOP days
        List<AttendanceRecord> attendance = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            AttendanceRecord record = new AttendanceRecord();
            record.setAttendanceType("PRESENT");
            attendance.add(record);
        }

        when(salaryComponentRepository.findByStructureId(testStructure.getId()))
            .thenReturn(testComponents.subList(0, 2));

        PayrollCalculationResultDto result = calculationEngine.calculatePayrollForEmployee(
            testEmployee, testRun, testStructure, attendance
        );

        assertNotNull(result);
        BigDecimal lopDays = BigDecimal.valueOf(6); // 26 - 20
        BigDecimal expectedLopDeduction = BigDecimal.valueOf(50000) // Basic
            .multiply(lopDays)
            .divide(BigDecimal.valueOf(26), 2, java.math.RoundingMode.HALF_UP);

        assertTrue(result.getGrossEarnings().compareTo(BigDecimal.valueOf(55000)) < 0);
    }

    @Test
    @DisplayName("Should calculate statutory deductions correctly")
    void testStatutoryDeductionsCalculation() {
        List<AttendanceRecord> attendance = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            AttendanceRecord record = new AttendanceRecord();
            record.setAttendanceType("PRESENT");
            attendance.add(record);
        }

        when(salaryComponentRepository.findByStructureId(testStructure.getId()))
            .thenReturn(testComponents);

        PayrollCalculationResultDto result = calculationEngine.calculatePayrollForEmployee(
            testEmployee, testRun, testStructure, attendance
        );

        assertNotNull(result);
        BigDecimal expectedPF = BigDecimal.valueOf(55000).multiply(BigDecimal.valueOf(0.12));
        assertEquals(expectedPF.setScale(2, java.math.RoundingMode.HALF_UP),
                     result.getPfContribution().setScale(2, java.math.RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("Should calculate net salary correctly")
    void testNetSalaryCalculation() {
        List<AttendanceRecord> attendance = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            AttendanceRecord record = new AttendanceRecord();
            record.setAttendanceType("PRESENT");
            attendance.add(record);
        }

        when(salaryComponentRepository.findByStructureId(testStructure.getId()))
            .thenReturn(testComponents);

        PayrollCalculationResultDto result = calculationEngine.calculatePayrollForEmployee(
            testEmployee, testRun, testStructure, attendance
        );

        assertNotNull(result);
        BigDecimal gross = result.getGrossEarnings();
        BigDecimal deductions = result.getTotalDeductions();
        BigDecimal expectedNet = gross.subtract(deductions);

        assertEquals(expectedNet.setScale(2, java.math.RoundingMode.HALF_UP),
                     result.getNetSalary().setScale(2, java.math.RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("Should handle edge case: zero attendance")
    void testZeroAttendanceCalculation() {
        List<AttendanceRecord> attendance = new ArrayList<>();

        when(salaryComponentRepository.findByStructureId(testStructure.getId()))
            .thenReturn(testComponents.subList(0, 2));

        PayrollCalculationResultDto result = calculationEngine.calculatePayrollForEmployee(
            testEmployee, testRun, testStructure, attendance
        );

        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getGrossEarnings());
    }

    @Test
    @DisplayName("Should handle edge case: full LOP month")
    void testFullLOPMonthCalculation() {
        // All days are absent or holiday
        List<AttendanceRecord> attendance = new ArrayList<>();

        when(salaryComponentRepository.findByStructureId(testStructure.getId()))
            .thenReturn(testComponents.subList(0, 2));

        PayrollCalculationResultDto result = calculationEngine.calculatePayrollForEmployee(
            testEmployee, testRun, testStructure, attendance
        );

        assertNotNull(result);
        assertTrue(result.getGrossEarnings().compareTo(BigDecimal.ZERO) <= 0);
    }

    @Test
    @DisplayName("Should calculate with loan deductions")
    void testLoanDeductionCalculation() {
        List<AttendanceRecord> attendance = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            AttendanceRecord record = new AttendanceRecord();
            record.setAttendanceType("PRESENT");
            attendance.add(record);
        }

        StaffLoan testLoan = new StaffLoan();
        testLoan.setId(1L);
        testLoan.setEmiAmount(BigDecimal.valueOf(5000));
        testLoan.setStatus(LoanStatus.ACTIVE);

        when(staffLoanRepository.findByEmployeeIdAndStatus(testEmployee.getId(), LoanStatus.ACTIVE))
            .thenReturn(List.of(testLoan));
        when(salaryComponentRepository.findByStructureId(testStructure.getId()))
            .thenReturn(testComponents);

        PayrollCalculationResultDto result = calculationEngine.calculatePayrollForEmployee(
            testEmployee, testRun, testStructure, attendance
        );

        assertNotNull(result);
        assertTrue(result.getLoanRecovery().compareTo(BigDecimal.ZERO) > 0);
    }
}
