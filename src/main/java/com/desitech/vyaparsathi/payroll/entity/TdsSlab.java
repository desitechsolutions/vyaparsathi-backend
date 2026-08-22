package com.desitech.vyaparsathi.payroll.entity;

import lombok.*;
import jakarta.persistence.*;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.payroll.enums.TaxRegime;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "tds_slabs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TdsSlab extends ShopAwareEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaxRegime taxRegime;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false)
    private BigDecimal incomeFrom;

    @Column(nullable = false)
    private BigDecimal incomeTo;

    @Column(nullable = false)
    private BigDecimal taxRate;

    @Column(nullable = false)
    private Boolean isActive = true;
}
