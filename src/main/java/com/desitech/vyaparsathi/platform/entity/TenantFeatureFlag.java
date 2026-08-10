package com.desitech.vyaparsathi.platform.entity;

import com.desitech.vyaparsathi.common.util.LocalDateTimeAttributeConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_feature_flags", uniqueConstraints = {
    @UniqueConstraint(name = "uq_shop_feature", columnNames = {"shop_id", "feature_key"})
})
@Getter
@Setter
@NoArgsConstructor
public class TenantFeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "feature_key", length = 50, nullable = false)
    private String featureKey;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "updated_by_admin_id", nullable = false)
    private Long updatedByAdminId;

    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
