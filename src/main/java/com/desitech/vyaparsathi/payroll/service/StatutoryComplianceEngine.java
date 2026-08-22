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
        // Simple: annual income estimate * TDS rate
        BigDecimal annualEstimate = monthlyGross.multiply(new BigDecimal(12));

        TdsSlab slab = tdsSlabRepository.findSlabForIncome(shopId, employee.getTaxRegime(), annualEstimate, LocalDate.of(year, month, 1)).orElse(null);
        if (slab == null) return BigDecimal.ZERO;

        return annualEstimate.multiply(slab.getTaxRate())
                .divide(new BigDecimal(100 * 12), 2, RoundingMode.HALF_UP);
    }

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
