package com.desitech.vyaparsathi.salesorder.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.quotation.entity.Quotation;
import com.desitech.vyaparsathi.salesorder.enums.SalesOrderStatus;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A confirmed customer order. Sits between the (optional) Quotation and the
 * final Sale. On APPROVE the system creates {@link StockReservation} rows so
 * the ordered stock cannot be walked in by another customer.
 * On CONVERT-TO-SALE (full or partial), reservations are consumed and a
 * DRAFT Sale is produced. Multiple partial conversions are supported.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "sales_order",
        uniqueConstraints = @UniqueConstraint(name = "uk_sales_order_shop_number",
                columnNames = {"shop_id", "order_no"}))
public class SalesOrder extends ShopAwareEntity {

    @Column(name = "order_no", nullable = false, length = 50)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Column(name = "total_taxable_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalTaxableAmount = BigDecimal.ZERO;

    @Column(name = "total_cgst", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCgst = BigDecimal.ZERO;

    @Column(name = "total_sgst", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalSgst = BigDecimal.ZERO;

    @Column(name = "total_igst", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalIgst = BigDecimal.ZERO;

    @Column(name = "invoice_discount", nullable = false, precision = 15, scale = 2)
    private BigDecimal invoiceDiscount = BigDecimal.ZERO;

    @Column(name = "shipping_charges", nullable = false, precision = 15, scale = 2)
    private BigDecimal shippingCharges = BigDecimal.ZERO;

    @Column(name = "other_charges", nullable = false, precision = 15, scale = 2)
    private BigDecimal otherCharges = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "is_gst_required", nullable = false)
    private Boolean isGstRequired = Boolean.TRUE;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "terms", columnDefinition = "TEXT")
    private String terms;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private SalesOrderStatus status = SalesOrderStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id")
    private Quotation quotation;

    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<SalesOrderItem> items = new ArrayList<>();

    public void addItem(SalesOrderItem item) {
        item.setSalesOrder(this);
        this.items.add(item);
    }
}
