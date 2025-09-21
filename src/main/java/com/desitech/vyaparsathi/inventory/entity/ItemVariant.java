package com.desitech.vyaparsathi.inventory.entity;

import com.desitech.vyaparsathi.shop.entity.Shop;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;

@Entity
@Table(name = "item_variant")
@Data
public class ItemVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "price_per_unit", nullable = false)
    private BigDecimal pricePerUnit;

    @Column
    private String hsn;

    @Column(name = "gst_rate", nullable = true)
    private Integer gstRate;

    @Column(name = "photo_path")
    private String photoPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    @JsonBackReference
    @ToString.Exclude
    private Item item;

    @Column
    private String color;

    @Column
    private String size;

    @Column
    private String design;

    @Column
    private String fit;

    @Column(name = "low_stock_threshold")
    private BigDecimal lowStockThreshold; // Threshold for low stock alerts
}
