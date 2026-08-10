package com.desitech.vyaparsathi.platform.entity;

import com.desitech.vyaparsathi.common.util.LocalDateTimeAttributeConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_limit_overrides")
@Getter
@Setter
@NoArgsConstructor
public class TenantLimitOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "resource_key", length = 50, nullable = false)
    private String resourceKey; // MAX_USERS | MAX_INVOICES | MAX_ITEMS | MAX_STORAGE_MB

    @Column(name = "override_limit", nullable = false)
    private Integer overrideLimit;

    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate = LocalDateTime.now();

    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_by_admin_id", nullable = false)
    private Long createdByAdminId;

    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
