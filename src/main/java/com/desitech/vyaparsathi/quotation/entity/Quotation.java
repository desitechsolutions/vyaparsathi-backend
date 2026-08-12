package com.desitech.vyaparsathi.quotation.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.quotation.enums.QuotationStatus;
import com.desitech.vyaparsathi.sales.entity.Sale;
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
 * A non-binding price offer sent to a customer. Convertible to a {@link Sale}
 * via {@code QuotationService.convertToSale(...)}. Once converted, the
 * {@link #convertedToSale} FK is populated and status transitions to
 * {@link QuotationStatus#CONVERTED} — preventing double conversion.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "quotation",
        uniqueConstraints = @UniqueConstraint(name = "uk_quotation_shop_number",
                columnNames = {"shop_id", "quotation_no"}))
public class Quotation extends ShopAwareEntity {

    @Column(name = "quotation_no", nullable = false, length = 50)
    private String quotationNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "quotation_date", nullable = false)
    private LocalDateTime quotationDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    // Header totals — mirror Sale's shape so conversion is a straight copy
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
    @Column(name = "status", nullable = false, length = 20)
    private QuotationStatus status = QuotationStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_to_sale_id")
    private Sale convertedToSale;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<QuotationItem> items = new ArrayList<>();

    public void addItem(QuotationItem item) {
        item.setQuotation(this);
        this.items.add(item);
    }
}
