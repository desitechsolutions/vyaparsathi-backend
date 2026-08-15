package com.desitech.vyaparsathi.inventory.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Server-side saved filter preset. Replaces {@code localStorage}-only saved
 * views so a shop's presets survive device changes.
 */
@Entity
@Table(name = "saved_view")
@Getter
@Setter
@NoArgsConstructor
public class SavedView extends ShopAwareEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** low_stock / stock_grid / receiving_list / etc. */
    @Column(name = "surface", nullable = false, length = 50)
    private String surface;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
