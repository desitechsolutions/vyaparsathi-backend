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
}
