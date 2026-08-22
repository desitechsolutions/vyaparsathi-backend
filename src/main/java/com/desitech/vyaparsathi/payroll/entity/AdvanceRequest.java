package com.desitech.vyaparsathi.payroll.entity;

import lombok.*;
import jakarta.persistence.*;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "advance_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdvanceRequest extends ShopAwareEntity {
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(length = 255)
    private String reason;

    @Column(nullable = false, length = 50)
    private String status;

    private LocalDate requestedAt;
    private Long approvedByUserId;
    private LocalDate approvedAt;

    @Column(columnDefinition = "TEXT")
    private String approvalRemarks;

    private LocalDate disbursedAt;
    private BigDecimal recoveryDeductionAmount;
    private Integer recoveryStartMonth;
    private Integer recoveryMonths;

    @Column(nullable = false)
    private Boolean isActive = true;
}
