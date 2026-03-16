package com.desitech.vyaparsathi.inventory.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "item_variant")
@Getter
@Setter
@NoArgsConstructor
public class ItemVariant extends ShopAwareEntity {

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

    // --- Pharmacy-specific fields ---

    /**
     * Batch/lot number assigned by manufacturer. Critical for pharmacy traceability.
     */
    @Column(name = "batch_number")
    private String batchNumber;

    /**
     * Date of manufacture (printed on medicine packaging).
     */
    @Column(name = "manufacturing_date")
    private LocalDate manufacturingDate;

    /**
     * Expiry date of this batch. Used to generate expiry alerts and prevent sale of expired items.
     */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /**
     * Maximum Retail Price – the legally printed maximum price on medicine packaging.
     * Selling price must not exceed MRP.
     */
    @Column(name = "mrp", precision = 12, scale = 2)
    private BigDecimal mrp;

    /**
     * Whether this medicine can be dispensed in quantities smaller than the stock unit.
     * For example: strip of 15 tablets can be sold as individual tablets (loose).
     * When true, {@link #packSize} must be set to enable correct stock deduction.
     */
    @Column(name = "is_loose_medicine")
    private Boolean isLooseMedicine = false;

    /**
     * Number of dispensing units (e.g. tablets) per stock unit (e.g. strip).
     * Used only when {@link #isLooseMedicine} is true.
     * Example: if packSize = 15, selling 4 tablets deducts 4/15 = 0.267 strips from stock.
     */
    @Column(name = "pack_size", precision = 10, scale = 3)
    private BigDecimal packSize;
}
