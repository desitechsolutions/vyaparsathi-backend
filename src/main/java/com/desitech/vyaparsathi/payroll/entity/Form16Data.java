package com.desitech.vyaparsathi.payroll.entity;

import lombok.*;
import jakarta.persistence.*;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "form16_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Form16Data extends ShopAwareEntity {
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false, length = 10)
    private String financialYear;

    @Column(length = 10)
    private String panNumber;

    @Column(length = 100)
    private String name;

    @Column(length = 255)
    private String address;

    @Column(length = 10)
    private String employerPan;

    @Column(length = 100)
    private String employerName;

    private BigDecimal totalSalary;
    private BigDecimal standardDeduction;
    private BigDecimal grossTotalIncome;
    private BigDecimal pfContribution;
    private BigDecimal esiContribution;
    private BigDecimal professionalTax;
    private BigDecimal tdsPayable;
    private BigDecimal taxPaidThisYear;

    private LocalDate form16PartAGeneratedAt;
    private LocalDate form16PartBGeneratedAt;

    @Column(length = 255)
    private String pdfPath;

    @Column(nullable = false)
    private Boolean isVerified = false;

    public String getEmployerTan() {
        return getShop() != null ? "TAN" + getShop().getId() : "—";
    }

    public String getEmployerAddress() {
        return getShop() != null && getShop().getAddress() != null ? getShop().getAddress() : "—";
    }

    public String getAssessmentYear() {
        if (financialYear != null && financialYear.contains("-")) {
            try {
                String[] parts = financialYear.split("-");
                int start = Integer.parseInt(parts[0]);
                int end = Integer.parseInt(parts[1]);
                return (start + 1) + "-" + (end + 1);
            } catch (Exception e) {
                return financialYear;
            }
        }
        return financialYear;
    }

    public String getPeriodFrom() {
        if (financialYear != null && financialYear.contains("-")) {
            return "01/04/" + financialYear.split("-")[0];
        }
        return "01/04/2024";
    }

    public String getPeriodTo() {
        if (financialYear != null && financialYear.contains("-")) {
            String[] parts = financialYear.split("-");
            int endYear = parts[1].length() == 2 ? Integer.parseInt("20" + parts[1]) : Integer.parseInt(parts[1]);
            return "31/03/" + endYear;
        }
        return "31/03/2025";
    }

    public BigDecimal getTotalDeductions() {
        BigDecimal total = BigDecimal.ZERO;
        if (pfContribution != null) total = total.add(pfContribution);
        if (esiContribution != null) total = total.add(esiContribution);
        return total;
    }

    public BigDecimal getTotalTaxableIncome() {
        BigDecimal gti = grossTotalIncome != null ? grossTotalIncome : BigDecimal.ZERO;
        return gti.subtract(getTotalDeductions()).max(BigDecimal.ZERO);
    }

    public BigDecimal getTaxOnIncome() {
        if (tdsPayable == null || tdsPayable.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        // Education cess is 4%, so base tax = total / 1.04
        return tdsPayable.multiply(new BigDecimal("100")).divide(new BigDecimal("104"), 2, java.math.RoundingMode.HALF_UP);
    }

    public BigDecimal getEducationCess() {
        if (tdsPayable == null) return BigDecimal.ZERO;
        return tdsPayable.subtract(getTaxOnIncome()).max(BigDecimal.ZERO);
    }
}
