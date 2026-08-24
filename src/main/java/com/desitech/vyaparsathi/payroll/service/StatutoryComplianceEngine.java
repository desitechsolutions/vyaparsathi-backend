package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.payroll.entity.*;
import com.desitech.vyaparsathi.payroll.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
public class StatutoryComplianceEngine {

    @Autowired private PfSlabRepository pfSlabRepository;
    @Autowired private EsiSlabRepository esiSlabRepository;
    @Autowired private PtSlabRepository ptSlabRepository;
    @Autowired private TdsSlabRepository tdsSlabRepository;
    @Autowired private StatutoryConfigRepository configRepository;
    @Autowired private TaxDeclarationRepository taxDeclarationRepository;

    public StatutoryDeductionsResult calculateStatutoryDeductions(
            Employee employee,
            BigDecimal grossEarnings,
            int month,
            int year,
            Long shopId) {

        StatutoryDeductionsResult result = new StatutoryDeductionsResult();
        LocalDate asOnDate = LocalDate.of(year, month, 1);

        // 1. PF Calculation
        if (Boolean.TRUE.equals(employee.getPfEnrolled())) {
            PfDeduction pf = calculatePF(grossEarnings, shopId, asOnDate);
            result.setPfEmployeeDeduction(pf.getEmployeeDeduction());
            result.setPfEmployerDeduction(pf.getEmployerDeduction());
            result.setEpfAmount(pf.getEpfAmount());
            result.setEpsAmount(pf.getEpsAmount());
        }

        // 2. ESI Calculation
        if (Boolean.TRUE.equals(employee.getEsicEnrolled()) && grossEarnings.compareTo(new BigDecimal(21000)) <= 0) {
            EsiDeduction esi = calculateESI(grossEarnings, shopId, asOnDate);
            result.setEsiEmployeeDeduction(esi.getEmployeeDeduction());
            result.setEsiEmployerDeduction(esi.getEmployerDeduction());
        }

        // 3. Professional Tax
        if (employee.getPtState() != null) {
            BigDecimal pt = calculateProfessionalTax(grossEarnings, employee.getPtState(), shopId, asOnDate);
            result.setProfessionalTaxDeduction(pt);
        }

        // 4. TDS (based on annual income)
        BigDecimal tds = calculateTDS(employee, grossEarnings, month, year, shopId);
        result.setTdsDeduction(tds);

        return result;
    }

    public PfDeduction calculatePF(BigDecimal grossEarnings, Long shopId, LocalDate asOnDate) {
        PfSlab slab = pfSlabRepository.findActiveSlabForDate(shopId, asOnDate).orElse(null);
        if (slab == null) return new PfDeduction();

        // PF on basic + DA (not on allowances)
        BigDecimal pfWage = grossEarnings.min(slab.getWageLimit());
        BigDecimal eeContribution = pfWage.multiply(slab.getEmployeeContributionRate())
                .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
        BigDecimal erContribution = pfWage.multiply(slab.getEmployerContributionRate())
                .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
        BigDecimal epf = erContribution.multiply(slab.getEpfContributionRate())
                .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
        BigDecimal eps = erContribution.multiply(slab.getEpsContributionRate())
                .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);

        return new PfDeduction(eeContribution, erContribution, epf, eps);
    }

    public EsiDeduction calculateESI(BigDecimal grossEarnings, Long shopId, LocalDate asOnDate) {
        EsiSlab slab = esiSlabRepository.findActiveSlabForDate(shopId, asOnDate).orElse(null);
        if (slab == null) return new EsiDeduction();

        BigDecimal esiWage = grossEarnings.min(slab.getWageCeiling());
        BigDecimal eeDeduction = esiWage.multiply(slab.getEmployeeRate())
                .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
        BigDecimal erDeduction = esiWage.multiply(slab.getEmployerRate())
                .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);

        return new EsiDeduction(eeDeduction, erDeduction);
    }

    public BigDecimal calculateProfessionalTax(String ptState, BigDecimal grossEarnings, Long shopId, LocalDate asOnDate) {
        return calculateProfessionalTax(grossEarnings, ptState, shopId, asOnDate);
    }

    public BigDecimal calculateProfessionalTax(BigDecimal grossEarnings, String ptState, Long shopId, LocalDate asOnDate) {
        PtSlab slab = ptSlabRepository.findSlabForSalary(shopId, ptState, grossEarnings, asOnDate).orElse(null);
        if (slab == null) return BigDecimal.ZERO;
        return slab.getPtAmount();
    }

    public BigDecimal calculateTDS(Employee employee, BigDecimal monthlyGross, int month, int year, Long shopId) {
        // ── Step 1: Annual income estimate ──────────────────────────────────
        BigDecimal annualEstimate = monthlyGross.multiply(new BigDecimal(12));
        BigDecimal taxableIncome = annualEstimate;

        String regime = employee.getTaxRegime() != null ? employee.getTaxRegime().name() : "NEW_REGIME";

        // ── Step 2: Standard Deduction (Sec 16) ─────────────────────────────
        // New Regime: ₹75,000 | Old Regime: ₹50,000
        BigDecimal standardDeduction = "NEW_REGIME".equals(regime)
                ? new BigDecimal("75000")
                : new BigDecimal("50000");
        taxableIncome = taxableIncome.subtract(standardDeduction);

        // ── Step 3: Chapter VI-A deductions (Old Regime only) ───────────────
        // Under New Regime, most deductions are not available
        if ("OLD_REGIME".equals(regime)) {
            try {
                String fy = year + "-" + (year + 1 - 2000);
                var declaration = taxDeclarationRepository
                        .findByEmployeeIdAndFinancialYear(employee.getId(), fy).orElse(null);
                if (declaration != null) {
                    // 80C — capped at ₹1,50,000
                    BigDecimal section80c = BigDecimal.ZERO
                            .add(nvlBD(declaration.getLifeInsurancePremium()))
                            .add(nvlBD(declaration.getEducationExpenses()))
                            .add(nvlBD(declaration.getHomeLoanPrincipal()))
                            .add(nvlBD(declaration.getOther80cDeductions()))
                            .min(new BigDecimal("150000"));
                    taxableIncome = taxableIncome.subtract(section80c);

                    // 80D — Medical Insurance (capped ₹25,000 individual, ₹50,000 senior)
                    BigDecimal section80d = nvlBD(declaration.getMedicalInsurancePremium())
                            .min(new BigDecimal("50000"));
                    taxableIncome = taxableIncome.subtract(section80d);

                    // 80CCD(1B) — NPS contribution (additional ₹50,000 deduction)
                    BigDecimal nps = nvlBD(declaration.getNpsContribution()).min(new BigDecimal("50000"));
                    taxableIncome = taxableIncome.subtract(nps);

                    // 24(b) — Home Loan Interest (capped ₹2,00,000)
                    BigDecimal homeLoanInterest = nvlBD(declaration.getHomeLoanInterest())
                            .min(new BigDecimal("200000"));
                    taxableIncome = taxableIncome.subtract(homeLoanInterest);
                }
            } catch (Exception e) {
                // Declaration lookup failed — proceed without deductions
            }
        } else {
            // New Regime: only NPS employer contribution (Sec 80CCD(2)) and a few exemptions
            // Standard deduction already applied above
        }

        // Ensure taxable income is not negative
        if (taxableIncome.compareTo(BigDecimal.ZERO) < 0) taxableIncome = BigDecimal.ZERO;

        // ── Step 4: Section 87A Rebate (New Regime: ≤ ₹7L → zero tax) ──────
        if ("NEW_REGIME".equals(regime) && taxableIncome.compareTo(new BigDecimal("700000")) <= 0) {
            return BigDecimal.ZERO; // Full rebate — no TDS
        }

        // ── Step 5: Apply TDS slab to taxable income ────────────────────────
        TdsSlab slab = tdsSlabRepository.findSlabForIncome(shopId, employee.getTaxRegime(),
                taxableIncome, LocalDate.of(year, month, 1)).orElse(null);
        if (slab == null) return BigDecimal.ZERO;

        BigDecimal annualTax = taxableIncome.multiply(slab.getTaxRate())
                .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);

        // Add 4% Health & Education Cess
        annualTax = annualTax.multiply(new BigDecimal("1.04")).setScale(2, RoundingMode.HALF_UP);

        // Monthly TDS = annual tax / 12
        return annualTax.divide(new BigDecimal(12), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvlBD(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    // Result DTOs
    public static class StatutoryDeductionsResult {
        private BigDecimal pfEmployeeDeduction = BigDecimal.ZERO;
        private BigDecimal pfEmployerDeduction = BigDecimal.ZERO;
        private BigDecimal epfAmount = BigDecimal.ZERO;
        private BigDecimal epsAmount = BigDecimal.ZERO;
        private BigDecimal esiEmployeeDeduction = BigDecimal.ZERO;
        private BigDecimal esiEmployerDeduction = BigDecimal.ZERO;
        private BigDecimal professionalTaxDeduction = BigDecimal.ZERO;
        private BigDecimal tdsDeduction = BigDecimal.ZERO;

        public BigDecimal getTotalEmployeeDeductions() {
            return pfEmployeeDeduction.add(esiEmployeeDeduction).add(professionalTaxDeduction).add(tdsDeduction);
        }

        public BigDecimal getTotalEmployerContributions() {
            return pfEmployerDeduction.add(esiEmployerDeduction);
        }

        public BigDecimal getTotalStatutoryAmount() {
            return getTotalEmployeeDeductions().add(getTotalEmployerContributions());
        }

        // Getters
        public BigDecimal getPfEmployeeDeduction() { return pfEmployeeDeduction; }
        public void setPfEmployeeDeduction(BigDecimal v) { this.pfEmployeeDeduction = v; }
        public BigDecimal getPfEmployerDeduction() { return pfEmployerDeduction; }
        public void setPfEmployerDeduction(BigDecimal v) { this.pfEmployerDeduction = v; }
        public BigDecimal getEpfAmount() { return epfAmount; }
        public void setEpfAmount(BigDecimal v) { this.epfAmount = v; }
        public BigDecimal getEpsAmount() { return epsAmount; }
        public void setEpsAmount(BigDecimal v) { this.epsAmount = v; }
        public BigDecimal getEsiEmployeeDeduction() { return esiEmployeeDeduction; }
        public void setEsiEmployeeDeduction(BigDecimal v) { this.esiEmployeeDeduction = v; }
        public BigDecimal getEsiEmployerDeduction() { return esiEmployerDeduction; }
        public void setEsiEmployerDeduction(BigDecimal v) { this.esiEmployerDeduction = v; }
        public BigDecimal getProfessionalTaxDeduction() { return professionalTaxDeduction; }
        public void setProfessionalTaxDeduction(BigDecimal v) { this.professionalTaxDeduction = v; }
        public BigDecimal getTdsDeduction() { return tdsDeduction; }
        public void setTdsDeduction(BigDecimal v) { this.tdsDeduction = v; }
    }

    public static class PfDeduction {
        private BigDecimal employeeDeduction;
        private BigDecimal employerDeduction;
        private BigDecimal epfAmount;
        private BigDecimal epsAmount;

        public PfDeduction() {}
        public PfDeduction(BigDecimal ee, BigDecimal er, BigDecimal epf, BigDecimal eps) {
            this.employeeDeduction = ee;
            this.employerDeduction = er;
            this.epfAmount = epf;
            this.epsAmount = eps;
        }

        public BigDecimal getEmployeeDeduction() { return employeeDeduction; }
        public BigDecimal getEmployerDeduction() { return employerDeduction; }
        public BigDecimal getEpfAmount() { return epfAmount; }
        public BigDecimal getEpsAmount() { return epsAmount; }
    }

    public static class EsiDeduction {
        private BigDecimal employeeDeduction;
        private BigDecimal employerDeduction;

        public EsiDeduction() {}
        public EsiDeduction(BigDecimal ee, BigDecimal er) {
            this.employeeDeduction = ee;
            this.employerDeduction = er;
        }

        public BigDecimal getEmployeeDeduction() { return employeeDeduction; }
        public BigDecimal getEmployerDeduction() { return employerDeduction; }
    }
}
