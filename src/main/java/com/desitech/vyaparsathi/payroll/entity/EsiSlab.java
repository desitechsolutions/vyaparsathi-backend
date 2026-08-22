package com.desitech.vyaparsathi.payroll.entity;

import lombok.*;
import jakarta.persistence.*;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "esi_slabs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EsiSlab extends ShopAwareEntity {
    @Column(nullable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false)
    private BigDecimal wageCeiling;

    @Column(nullable = false)
    private BigDecimal employeeRate;

    @Column(nullable = false)
    private BigDecimal employerRate;

    @Column(nullable = false)
    private Boolean isActive = true;
}
