package com.desitech.vyaparsathi.payroll.entity;

import lombok.*;
import jakarta.persistence.*;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "pt_slabs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PtSlab extends ShopAwareEntity {
    @Column(nullable = false, length = 2)
    private String ptState;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false)
    private BigDecimal salaryFrom;

    @Column(nullable = false)
    private BigDecimal salaryTo;

    @Column(nullable = false)
    private BigDecimal ptAmount;

    @Column(nullable = false)
    private Boolean isActive = true;
}
