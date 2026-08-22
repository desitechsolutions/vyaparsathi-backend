package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.payroll.dto.StatutoryDeductionsDto;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Statutory Compliance Engine Tests")
class StatutoryComplianceEngineTest {

    @Mock private PfSlabRepository pfSlabRepository;
    @Mock private EsiSlabRepository esiSlabRepository;
    @Mock private PtSlabRepository ptSlabRepository;
    @Mock private TdsSlabRepository tdsSlabRepository;
    @Mock private StatutoryConfigRepository statutoryConfigRepository;

    @InjectMocks private StatutoryComplianceEngine complianceEngine;

    private Employee testEmployee;
    private PayrollRun testRun;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        testEmployee = new Employee();
        testEmployee.setId(1L);
        testEmployee.setName("John Doe");
        testEmployee.setPan("ABCDE1234F");
        testEmployee.setUan("100123456789");

        testRun = new PayrollRun();
        testRun.setId(1L);
        testRun.setPayrollMonth("08");
        testRun.setPayrollYear(2026);
    }

    @Test
    @DisplayName("Should calculate PF contribution (12% of basic + DA)")
    void testPFCalculation() {
        BigDecimal basicSalary = BigDecimal.valueOf(50000);
        BigDecimal da = BigDecimal.valueOf(5000);
        BigDecimal pfBase = basicSalary.add(da); // 55000

        StatutoryDeductionsDto result = complianceEngine.calculatePF(pfBase, testEmployee, testRun);

        assertNotNull(result);
        BigDecimal expectedPF = pfBase.multiply(BigDecimal.valueOf(0.12));
        assertEquals(expectedPF.setScale(2, java.math.RoundingMode.HALF_UP),
                     result.getEmployeePFContribution().setScale(2, java.math.RoundingMode.HALF_UP));
        assertTrue(result.getEmployerPFContribution().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Should calculate ESI contribution (0.75% EE, 3.25% ER for gross <= 21000)")
    void testESICalculationBelowThreshold() {
        BigDecimal grossWages = BigDecimal.valueOf(18000);

        StatutoryDeductionsDto result = complianceEngine.calculateESI(grossWages, testEmployee);

        assertNotNull(result);
        BigDecimal expectedEE = grossWages.multiply(BigDecimal.valueOf(0.0075));
        assertEquals(expectedEE.setScale(2, java.math.RoundingMode.HALF_UP),
                     result.getEmployeeESIContribution().setScale(2, java.math.RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("Should NOT calculate ESI for gross > 21000")
    void testESICalculationAboveThreshold() {
        BigDecimal grossWages = BigDecimal.valueOf(25000);

        StatutoryDeductionsDto result = complianceEngine.calculateESI(grossWages, testEmployee);

        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getEmployeeESIContribution());
        assertEquals(BigDecimal.ZERO, result.getEmployerESIContribution());
    }

    @Test
    @DisplayName("Should calculate Professional Tax based on state and slab")
    void testProfessionalTaxCalculation() {
        BigDecimal grossWages = BigDecimal.valueOf(60000);
        testEmployee.setState("MH"); // Maharashtra

        StatutoryDeductionsDto result = complianceEngine.calculateProfessionalTax(grossWages, testEmployee);

        assertNotNull(result);
        assertTrue(result.getProfessionalTax().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    @DisplayName("Should calculate TDS (2% for standard, progressive for high income)")
    void testTDSCalculation() {
        BigDecimal annualIncome = BigDecimal.valueOf(750000);

        StatutoryDeductionsDto result = complianceEngine.calculateTDS(annualIncome, testEmployee, testRun);

        assertNotNull(result);
        assertTrue(result.getTds().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Should NOT calculate TDS for income below exemption limit")
    void testTDSNullCalculation() {
        BigDecimal annualIncome = BigDecimal.valueOf(250000);

        StatutoryDeductionsDto result = complianceEngine.calculateTDS(annualIncome, testEmployee, testRun);

        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getTds());
    }

    @Test
    @DisplayName("Should validate PF UAN number")
    void testPFUANValidation() {
        testEmployee.setUan("100123456789");

        boolean isValid = complianceEngine.validatePFUAN(testEmployee.getUan());

        assertTrue(isValid);
    }

    @Test
    @DisplayName("Should validate PAN format")
    void testPANValidation() {
        testEmployee.setPan("ABCDE1234F");

        boolean isValid = complianceEngine.validatePAN(testEmployee.getPan());

        assertTrue(isValid);
    }

    @Test
    @DisplayName("Should generate ECR file for EPFO submission")
    void testECRGeneration() {
        PayrollRun run = new PayrollRun();
        run.setId(1L);
        run.setPayrollMonth("08");
        run.setPayrollYear(2026);

        String ecrContent = complianceEngine.generateECRFile(run);

        assertNotNull(ecrContent);
        assertTrue(ecrContent.contains("ECECR"));
        assertTrue(ecrContent.length() > 0);
    }

    @Test
    @DisplayName("Should generate ESIC return for submission")
    void testESICReturnGeneration() {
        PayrollRun run = new PayrollRun();
        run.setId(1L);
        run.setPayrollMonth("08");
        run.setPayrollYear(2026);

        byte[] esicReturn = complianceEngine.generateESICReturn(run);

        assertNotNull(esicReturn);
        assertTrue(esicReturn.length > 0);
    }

    @Test
    @DisplayName("Should calculate total statutory deductions")
    void testTotalStatutoryDeductionsCalculation() {
        BigDecimal grossWages = BigDecimal.valueOf(65000);

        StatutoryDeductionsDto result = complianceEngine.calculateTotalStatutoryDeductions(
            grossWages, testEmployee, testRun
        );

        assertNotNull(result);
        BigDecimal total = result.getEmployeePFContribution()
            .add(result.getEmployeeESIContribution())
            .add(result.getProfessionalTax())
            .add(result.getTds());

        assertEquals(total.setScale(2, java.math.RoundingMode.HALF_UP),
                     result.getTotalDeductions().setScale(2, java.math.RoundingMode.HALF_UP));
    }
}
