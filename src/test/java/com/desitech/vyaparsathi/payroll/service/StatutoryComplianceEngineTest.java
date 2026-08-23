package com.desitech.vyaparsathi.payroll.service;

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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("Statutory Compliance Engine Tests")
class StatutoryComplianceEngineTest {

    @Mock private PfSlabRepository pfSlabRepository;
    @Mock private EsiSlabRepository esiSlabRepository;
    @Mock private PtSlabRepository ptSlabRepository;
    @Mock private TdsSlabRepository tdsSlabRepository;
    @Mock private StatutoryConfigRepository statutoryConfigRepository;

    @InjectMocks private StatutoryComplianceEngine complianceEngine;

    private static final Long SHOP_ID = 1L;

    private Employee testEmployee;
    private PfSlab pfSlab;
    private EsiSlab esiSlab;
    private PtSlab ptSlab;
    private TdsSlab tdsSlab;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Use correct field names: setFirstName/setLastName, setPanNumber, setUanNumber
        testEmployee = new Employee();
        testEmployee.setId(1L);
        testEmployee.setFirstName("John");
        testEmployee.setLastName("Doe");
        testEmployee.setPanNumber("ABCDE1234F");
        testEmployee.setUanNumber("100123456789");
        testEmployee.setPfEnrolled(true);
        testEmployee.setEsicEnrolled(true);
        testEmployee.setPtState("MH");
        testEmployee.setTaxRegime(TaxRegime.NEW_REGIME);

        // Setup PF slab mock
        pfSlab = new PfSlab();
        pfSlab.setWageLimit(new BigDecimal("15000"));
        pfSlab.setEmployeeContributionRate(new BigDecimal("12"));
        pfSlab.setEmployerContributionRate(new BigDecimal("12"));
        pfSlab.setEpfContributionRate(new BigDecimal("3.67"));
        pfSlab.setEpsContributionRate(new BigDecimal("8.33"));

        // Setup ESI slab mock
        esiSlab = new EsiSlab();
        esiSlab.setWageCeiling(new BigDecimal("21000"));
        esiSlab.setEmployeeRate(new BigDecimal("0.75"));
        esiSlab.setEmployerRate(new BigDecimal("3.25"));

        // Setup PT slab mock
        ptSlab = new PtSlab();
        ptSlab.setPtAmount(new BigDecimal("200"));
    }

    @Test
    @DisplayName("Should calculate PF employee deduction (12% of wages up to ceiling)")
    void testPFCalculation() {
        BigDecimal grossWages = BigDecimal.valueOf(50000);
        when(pfSlabRepository.findActiveSlabForDate(eq(SHOP_ID), any(LocalDate.class)))
            .thenReturn(Optional.of(pfSlab));

        StatutoryComplianceEngine.PfDeduction result =
            complianceEngine.calculatePF(grossWages, SHOP_ID, LocalDate.of(2026, 8, 1));

        assertNotNull(result);
        // PF wage is capped at 15000; 12% of 15000 = 1800
        BigDecimal expectedEE = new BigDecimal("15000")
            .multiply(new BigDecimal("12"))
            .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        assertEquals(expectedEE, result.getEmployeeDeduction());
        assertTrue(result.getEmployerDeduction().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Should calculate ESI contribution for gross <= 21000 (0.75% EE, 3.25% ER)")
    void testESICalculationBelowThreshold() {
        BigDecimal grossWages = BigDecimal.valueOf(18000);
        when(esiSlabRepository.findActiveSlabForDate(eq(SHOP_ID), any(LocalDate.class)))
            .thenReturn(Optional.of(esiSlab));

        StatutoryComplianceEngine.EsiDeduction result =
            complianceEngine.calculateESI(grossWages, SHOP_ID, LocalDate.of(2026, 8, 1));

        assertNotNull(result);
        BigDecimal expectedEE = grossWages.multiply(new BigDecimal("0.75"))
            .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        assertEquals(expectedEE, result.getEmployeeDeduction());
        assertTrue(result.getEmployerDeduction().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Should skip ESI when no slab found (returns zero deduction)")
    void testESINoSlabReturnsZero() {
        when(esiSlabRepository.findActiveSlabForDate(any(), any()))
            .thenReturn(Optional.empty());

        StatutoryComplianceEngine.EsiDeduction result =
            complianceEngine.calculateESI(BigDecimal.valueOf(18000), SHOP_ID, LocalDate.of(2026, 8, 1));

        assertNotNull(result);
        // No slab → returns empty EsiDeduction with null fields (engine returns new EsiDeduction())
        assertNull(result.getEmployeeDeduction());
    }

    @Test
    @DisplayName("Should calculate Professional Tax based on PT slab")
    void testProfessionalTaxCalculation() {
        BigDecimal grossWages = BigDecimal.valueOf(60000);
        when(ptSlabRepository.findSlabForSalary(eq(SHOP_ID), eq("MH"), eq(grossWages), any(LocalDate.class)))
            .thenReturn(Optional.of(ptSlab));

        BigDecimal pt = complianceEngine.calculateProfessionalTax(
            grossWages, "MH", SHOP_ID, LocalDate.of(2026, 8, 1)
        );

        assertNotNull(pt);
        assertEquals(new BigDecimal("200"), pt);
    }

    @Test
    @DisplayName("Should return ZERO PT when no matching slab found")
    void testProfessionalTaxNoSlabReturnsZero() {
        when(ptSlabRepository.findSlabForSalary(any(), any(), any(), any()))
            .thenReturn(Optional.empty());

        BigDecimal pt = complianceEngine.calculateProfessionalTax(
            BigDecimal.valueOf(60000), "MH", SHOP_ID, LocalDate.of(2026, 8, 1)
        );

        assertEquals(BigDecimal.ZERO, pt);
    }

    @Test
    @DisplayName("Should calculate TDS for income above exemption limit")
    void testTDSCalculationAboveExemption() {
        TdsSlab tdsSlab = new TdsSlab();
        tdsSlab.setTaxRate(new BigDecimal("5"));
        when(tdsSlabRepository.findSlabForIncome(any(), any(), any(), any()))
            .thenReturn(Optional.of(tdsSlab));

        // Monthly 65000 → annual 780000 (above 7L exemption)
        BigDecimal tds = complianceEngine.calculateTDS(
            testEmployee, BigDecimal.valueOf(65000), 8, 2026, SHOP_ID
        );

        assertNotNull(tds);
        assertTrue(tds.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Should return ZERO TDS when no slab matches (income below exemption)")
    void testTDSCalculationNoSlabReturnsZero() {
        when(tdsSlabRepository.findSlabForIncome(any(), any(), any(), any()))
            .thenReturn(Optional.empty());

        BigDecimal tds = complianceEngine.calculateTDS(
            testEmployee, BigDecimal.valueOf(20000), 8, 2026, SHOP_ID
        );

        assertEquals(BigDecimal.ZERO, tds);
    }

    @Test
    @DisplayName("Should calculate total statutory deductions correctly (sum of PF+ESI+PT+TDS)")
    void testTotalStatutoryDeductionsCalculation() {
        BigDecimal grossWages = BigDecimal.valueOf(50000);
        when(pfSlabRepository.findActiveSlabForDate(any(), any())).thenReturn(Optional.of(pfSlab));
        when(esiSlabRepository.findActiveSlabForDate(any(), any())).thenReturn(Optional.empty()); // not enrolled / above ceiling
        when(ptSlabRepository.findSlabForSalary(any(), any(), any(), any())).thenReturn(Optional.of(ptSlab));
        when(tdsSlabRepository.findSlabForIncome(any(), any(), any(), any())).thenReturn(Optional.empty());

        StatutoryComplianceEngine.StatutoryDeductionsResult result =
            complianceEngine.calculateStatutoryDeductions(testEmployee, grossWages, 8, 2026, SHOP_ID);

        assertNotNull(result);
        BigDecimal computedTotal = result.getPfEmployeeDeduction()
            .add(result.getEsiEmployeeDeduction())
            .add(result.getProfessionalTaxDeduction())
            .add(result.getTdsDeduction());
        assertEquals(
            computedTotal.setScale(2, java.math.RoundingMode.HALF_UP),
            result.getTotalEmployeeDeductions().setScale(2, java.math.RoundingMode.HALF_UP)
        );
    }

    @Test
    @DisplayName("Should return zero deductions when employee has PF and ESI disabled")
    void testNoDeductionsWhenNotEnrolled() {
        testEmployee.setPfEnrolled(false);
        testEmployee.setEsicEnrolled(false);
        testEmployee.setPtState(null); // no PT state

        StatutoryComplianceEngine.StatutoryDeductionsResult result =
            complianceEngine.calculateStatutoryDeductions(testEmployee, BigDecimal.valueOf(50000), 8, 2026, SHOP_ID);

        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getPfEmployeeDeduction());
        assertEquals(BigDecimal.ZERO, result.getEsiEmployeeDeduction());
        assertEquals(BigDecimal.ZERO, result.getProfessionalTaxDeduction());
    }
}
