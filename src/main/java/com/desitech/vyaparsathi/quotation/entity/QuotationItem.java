package com.desitech.vyaparsathi.quotation.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.sales.enums.GSTType;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One line on a {@link Quotation}. Shape mirrors {@code SaleItem} so a
 * quotation converts to a sale by copying rows 1:1. Supports both catalog
 * lines (item_variant_id) and free-text service lines (custom_item_name),
 * matching the constraint added in V57.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "quotation_item")
public class QuotationItem extends ShopAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", nullable = false)
    @JsonBackReference
    private Quotation quotation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_variant_id")
    private ItemVariant itemVariant;

    @Column(name = "custom_item_name", length = 255)
    private String customItemName;

    @Column(name = "custom_description", length = 500)
    private String customDescription;

    @Column(name = "custom_hsn_sac", length = 20)
    private String customHsnSac;

    @Column(name = "custom_unit", length = 30)
    private String customUnit;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Column(name = "hsn_sac", length = 20)
    private String hsnSac;

    @Column(name = "unit", length = 30)
    private String unit;

    @Column(name = "qty", nullable = false, precision = 10, scale = 2)
    private BigDecimal qty;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "gst_type", length = 20)
    private GSTType gstType;

    @Column(name = "gst_rate", nullable = false)
    private Integer gstRate = 0;

    @Column(name = "taxable_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableValue;

    @Column(name = "cgst_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal cgstAmt = BigDecimal.ZERO;

    @Column(name = "sgst_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal sgstAmt = BigDecimal.ZERO;

    @Column(name = "igst_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal igstAmt = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;
}
