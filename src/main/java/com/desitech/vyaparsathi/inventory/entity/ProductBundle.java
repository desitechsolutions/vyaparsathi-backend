package com.desitech.vyaparsathi.inventory.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Product bundle / kit. One parent variant composed of N child variants. When
 * the bundle is sold, downstream logic multiplies the sold quantity by each
 * component's per-unit quantity and deducts the underlying variants.
 */
@Entity
@Table(name = "product_bundle")
@Getter
@Setter
@NoArgsConstructor
public class ProductBundle extends ShopAwareEntity {

    @Column(name = "bundle_variant_id", nullable = false)
    private Long bundleVariantId;

    @Column(name = "bundle_name", nullable = false, length = 200)
    private String bundleName;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "productBundle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductBundleComponent> components = new ArrayList<>();

    @PreUpdate
    public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
