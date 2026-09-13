package com.desitech.vyaparsathi.gst.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "gstr2b_entries",
    indexes = {
        @Index(name = "idx_gstr2b_entry_import", columnList = "import_id"),
        @Index(name = "idx_gstr2b_entry_shop", columnList = "shop_id")
    })
@Getter
@Setter
@NoArgsConstructor
public class Gstr2bEntry extends ShopAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_id", nullable = false)
    private Gstr2bImport gstr2bImport;

    @Column(name = "supplier_gstin", length = 15)
    private String supplierGstin;

    @Column(name = "supplier_name", length = 255)
    private String supplierName;

    @Column(name = "invoice_number", length = 100)
    private String invoiceNumber;

    @Column(name = "invoice_type", length = 20)
    private String invoiceType; // B2B, CDNR

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "invoice_value", precision = 14, scale = 2)
    private BigDecimal invoiceValue;

    @Column(name = "taxable_value", precision = 14, scale = 2)
    private BigDecimal taxableValue;

    @Column(name = "igst_amount", precision = 12, scale = 2)
    private BigDecimal igstAmount;

    @Column(name = "cgst_amount", precision = 12, scale = 2)
    private BigDecimal cgstAmount;

    @Column(name = "sgst_amount", precision = 12, scale = 2)
    private BigDecimal sgstAmount;

    @Column(name = "cess_amount", precision = 12, scale = 2)
    private BigDecimal cessAmount;

    @Column(name = "itc_availability", length = 1)
    private String itcAvailability; // Y / N

    @Column(name = "match_status", nullable = false, length = 30)
    private String matchStatus;

    @Column(name = "matched_purchase_id")
    private Long matchedPurchaseId;
}
