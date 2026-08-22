package com.desitech.vyaparsathi.payroll.entity;

import lombok.*;
import jakarta.persistence.*;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "tax_declarations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaxDeclaration extends ShopAwareEntity {
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false, length = 10)
    private String financialYear;

    @Column(nullable = false, length = 50)
    private String taxRegime;

    private BigDecimal lifeInsurancePremium;
    private BigDecimal medicalInsurancePremium;
    private BigDecimal educationExpenses;
    private BigDecimal homeLoanPrincipal;
    private BigDecimal homeLoanInterest;
    private BigDecimal npsContribution;
    private BigDecimal other80cDeductions;

    private BigDecimal houseRentAllowanceClaimed;
    private BigDecimal leaveEncashmentClaimed;
    private BigDecimal medicalReimbursementClaimed;

    private BigDecimal totalTaxableIncome;
    private LocalDate submittedAt;
}
