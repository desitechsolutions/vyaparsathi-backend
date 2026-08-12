package com.desitech.vyaparsathi.salesorder.entity;

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
 * One line on a {@link SalesOrder}. {@link #fulfilledQty} tracks how much has
 * been converted into a Sale so far, enabling partial fulfillment: an SO for
 * 10 units can be fulfilled by two Sales of 5 units each.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "sales_order_item")
public class SalesOrderItem extends ShopAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id", nullable = false)
    @JsonBackReference
    private SalesOrder salesOrder;

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

    /** How much of {@link #qty} has already been converted into Sale line(s). */
    @Column(name = "fulfilled_qty", nullable = false, precision = 10, scale = 2)
    private BigDecimal fulfilledQty = BigDecimal.ZERO;

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

    /** Convenience: remaining qty available for conversion. */
    public BigDecimal getRemainingQty() {
        BigDecimal q = qty != null ? qty : BigDecimal.ZERO;
        BigDecimal f = fulfilledQty != null ? fulfilledQty : BigDecimal.ZERO;
        return q.subtract(f).max(BigDecimal.ZERO);
    }
}
