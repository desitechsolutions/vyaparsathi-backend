package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.payroll.entity.Employee;
import com.desitech.vyaparsathi.payroll.repository.EmployeeRepository;
import com.desitech.vyaparsathi.payroll.repository.PayrollSlipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * Gratuity Calculator — Payment of Gratuity Act, 1972.
 *
 * Formula: Gratuity = (Last Drawn Salary × 15/26) × Years of Service
 * - "Last Drawn Salary" = basic + DA (HRA excluded as per statute)
 * - "15/26" = 15 days wages per year of service (26 working days/month)
 * - Eligible after 5 years of continuous employment
 * - Maximum gratuity per Act: ₹20,00,000 (₹20 lakhs) — changed from ₹10L in 2018
 *
 * For non-covered establishments: Gratuity = (Salary × 15/30) × Years
 */
@Service
public class GratuityCalculatorService {

    private static final BigDecimal FIFTEEN_BY_TWENTY_SIX = new BigDecimal("15").divide(new BigDecimal("26"), 8, RoundingMode.HALF_UP);
    private static final BigDecimal MAXIMUM_GRATUITY_ACT = new BigDecimal("2000000"); // ₹20 lakhs
    private static final int MINIMUM_ELIGIBLE_YEARS = 5;

    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private PayrollSlipRepository payrollSlipRepository;

    /**
     * Calculate gratuity for an employee as on a given date.
     *
     * @param employeeId Employee ID
     * @param asOnDate   Date of calculation (resignation/retirement date)
     * @return GratuityCalculation result DTO
     */
    @Transactional(readOnly = true)
    public GratuityCalculation calculateGratuity(Long employeeId, LocalDate asOnDate) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found: " + employeeId));

        LocalDate joiningDate = employee.getJoiningDate();
        if (joiningDate == null) {
            return GratuityCalculation.ineligible(employeeId, "Joining date not recorded");
        }

        // ── Step 1: Compute years of continuous service ──────────────────────
        Period serviceperiod = Period.between(joiningDate, asOnDate);
        int totalYears = serviceperiod.getYears();
        int extraMonths = serviceperiod.getMonths();

        // Per Gratuity Act: 6+ months in the last year counts as a full year
        int gratuityYears = totalYears;
        if (extraMonths >= 6) gratuityYears += 1;

        // ── Step 2: Check eligibility (minimum 5 years) ──────────────────────
        if (totalYears < MINIMUM_ELIGIBLE_YEARS) {
            return GratuityCalculation.ineligible(employeeId,
                    "Minimum 5 years service required. Current: " + totalYears + "y " + extraMonths + "m");
        }

        // ── Step 3: Determine last drawn "salary" (Basic + DA only) ──────────
        // Use monthlyCTC as proxy; ideally sum basic + DA from last payslip
        BigDecimal lastBasicPlusDa = getLastBasicPlusDa(employee);
        if (lastBasicPlusDa == null || lastBasicPlusDa.compareTo(BigDecimal.ZERO) == 0) {
            lastBasicPlusDa = employee.getMonthlyCTC() != null ? employee.getMonthlyCTC() : BigDecimal.ZERO;
        }

        // ── Step 4: Apply Gratuity Act formula ──────────────────────────────
        // Gratuity = (Basic + DA) × 15/26 × Years
        BigDecimal gratuityAmount = lastBasicPlusDa
                .multiply(FIFTEEN_BY_TWENTY_SIX)
                .multiply(new BigDecimal(gratuityYears))
                .setScale(2, RoundingMode.HALF_UP);

        // ── Step 5: Cap at statutory maximum (₹20 lakhs) ────────────────────
        boolean cappedAtMaximum = gratuityAmount.compareTo(MAXIMUM_GRATUITY_ACT) > 0;
        BigDecimal cappedAmount = gratuityAmount.min(MAXIMUM_GRATUITY_ACT);

        return new GratuityCalculation(
                employeeId,
                employee.getFirstName() + " " + (employee.getLastName() != null ? employee.getLastName() : ""),
                joiningDate,
                asOnDate,
                totalYears,
                extraMonths,
                gratuityYears,
                lastBasicPlusDa,
                gratuityAmount,
                cappedAmount,
                cappedAtMaximum,
                true,
                null
        );
    }

    /**
     * Bulk calculate gratuity for all employees in a shop.
     */
    @Transactional(readOnly = true)
    public List<GratuityCalculation> calculateBulkGratuity(Long shopId, LocalDate asOnDate) {
        return employeeRepository.findByShopId(shopId).stream()
                .map(emp -> {
                    try {
                        return calculateGratuity(emp.getId(), asOnDate);
                    } catch (Exception e) {
                        return GratuityCalculation.ineligible(emp.getId(), e.getMessage());
                    }
                })
                .toList();
    }

    /**
     * Estimate gratuity for an employee based on current salary and projected years.
     */
    public BigDecimal estimateGratuity(BigDecimal monthlySalary, int yearsOfService) {
        if (yearsOfService < MINIMUM_ELIGIBLE_YEARS) return BigDecimal.ZERO;
        return monthlySalary
                .multiply(FIFTEEN_BY_TWENTY_SIX)
                .multiply(new BigDecimal(yearsOfService))
                .min(MAXIMUM_GRATUITY_ACT)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getLastBasicPlusDa(Employee employee) {
        // Look for "BASIC" component in last payroll slip items
        try {
            var slips = payrollSlipRepository.findByEmployeeIdAndShopId(
                    employee.getId(),
                    employee.getShop() != null ? employee.getShop().getId() : null,
                    org.springframework.data.domain.PageRequest.of(0, 1,
                            org.springframework.data.domain.Sort.by("id").descending()));
            if (slips.hasContent()) {
                var slip = slips.getContent().get(0);
                if (slip.getItems() != null) {
                    return slip.getItems().stream()
                            .filter(i -> i.getComponentCode() != null &&
                                    (i.getComponentCode().contains("BASIC") || i.getComponentCode().contains("DA")))
                            .map(i -> i.getAmount() != null ? i.getAmount() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ─── Result DTO ────────────────────────────────────────────────────────────

    public record GratuityCalculation(
            Long employeeId,
            String employeeName,
            LocalDate joiningDate,
            LocalDate asOnDate,
            int totalYears,
            int extraMonths,
            int eligibleYears,
            BigDecimal lastSalary,
            BigDecimal gratuityBeforeCap,
            BigDecimal gratuityAmount,
            boolean cappedAtMaximum,
            boolean eligible,
            String ineligibilityReason
    ) {
        static GratuityCalculation ineligible(Long empId, String reason) {
            return new GratuityCalculation(empId, null, null, null, 0, 0, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, false, reason);
        }
    }
}
