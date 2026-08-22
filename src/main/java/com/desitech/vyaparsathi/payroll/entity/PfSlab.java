package com.desitech.vyaparsathi.payroll.entity;

import lombok.*;
import jakarta.persistence.*;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "pf_slabs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PfSlab extends ShopAwareEntity {
    @Column(nullable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false)
    private BigDecimal wageLimit;

    @Column(nullable = false)
    private BigDecimal employeeContributionRate;

    @Column(nullable = false)
    private BigDecimal employerContributionRate;

    @Column(nullable = false)
    private BigDecimal epfContributionRate;

    @Column(nullable = false)
    private BigDecimal epsContributionRate;

    @Column(nullable = false)
    private Boolean isActive = true;
}
