package com.desitech.vyaparsathi.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "platform_feature_flags")
@Getter
@Setter
@NoArgsConstructor
public class PlatformFeatureFlag {

    @Id
    @Column(name = "feature_key", length = 50, nullable = false)
    private String featureKey;

    @Column(name = "feature_name", length = 100, nullable = false)
    private String featureName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "default_enabled", nullable = false)
    private Boolean defaultEnabled = false;

    @Column(length = 30, nullable = false)
    private String category = "GENERAL";
}
