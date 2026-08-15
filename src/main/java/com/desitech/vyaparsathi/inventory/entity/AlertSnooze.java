package com.desitech.vyaparsathi.inventory.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "alert_snooze")
@Getter
@Setter
@NoArgsConstructor
public class AlertSnooze extends ShopAwareEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;

    @Column(name = "alert_key", nullable = false, length = 200)
    private String alertKey;

    @Column(name = "snoozed_until", nullable = false)
    private LocalDateTime snoozedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
