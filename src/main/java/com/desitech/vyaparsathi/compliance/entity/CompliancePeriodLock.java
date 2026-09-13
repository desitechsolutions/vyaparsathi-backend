package com.desitech.vyaparsathi.compliance.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "compliance_period_locks",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_period_lock",
        columnNames = {"shop_id", "period_year", "period_month", "form_type"}))
@Getter
@Setter
@NoArgsConstructor
public class CompliancePeriodLock extends BaseEntity {

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "form_type", nullable = false, length = 20)
    private String formType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by_user_id")
    private Long lockedByUserId;

    @Column(name = "locked_by", length = 255)
    private String lockedBy;
}
