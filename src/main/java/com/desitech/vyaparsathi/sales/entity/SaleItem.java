package com.desitech.vyaparsathi.sales.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.sales.enums.GSTType;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "sale_item")
public class SaleItem extends ShopAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    @JsonBackReference
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_variant_id", nullable = false)
    private ItemVariant itemVariant;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal qty;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "taxable_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "gst_type", nullable = false, length = 20)
    private GSTType gstType;

    @Column(name = "cgst_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal cgstAmt;

    @Column(name = "sgst_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal sgstAmt;

    @Column(name = "igst_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal igstAmt;

    @Column(name = "discount", precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "returned_qty", precision = 10, scale = 2)
    private BigDecimal returnedQty = BigDecimal.ZERO;

    @Column(name = "is_returned", nullable = false)
    private boolean isReturned = false;

    /**
     * The pack size (dispensing units per stock unit) that was active at the time of sale
     * for a loose-medicine line item, e.g. 15 tablets per strip.
     * Null for full-pack sales or medicines that are not dispensed loose.
     * Stored so that returns can reverse exactly the same fractional stock quantity.
     */
    @Column(name = "loose_pack_size", precision = 10, scale = 3)
    private BigDecimal loosePackSize;

    /**
     * Batch/lot number of the specific medicine pack dispensed.
     * Captured at point-of-sale for batch traceability and drug-recall tracking.
     */
    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    /**
     * Expiry date of the specific batch dispensed.
     * Stored per sale-item for invoice printing and narcotics-register compliance.
     */
    @Column(name = "expiry_date")
    private java.time.LocalDate expiryDate;
}