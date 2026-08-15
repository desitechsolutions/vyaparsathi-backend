package com.desitech.vyaparsathi.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "product_bundle_component")
@Getter
@Setter
@NoArgsConstructor
public class ProductBundleComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_bundle_id", nullable = false)
    private ProductBundle productBundle;

    @Column(name = "component_variant_id", nullable = false)
    private Long componentVariantId;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "optional_flag", nullable = false)
    private boolean optionalFlag = false;
}
