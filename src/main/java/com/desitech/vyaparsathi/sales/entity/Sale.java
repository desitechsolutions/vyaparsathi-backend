package com.desitech.vyaparsathi.sales.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.AuditableFinancialEntity;
import com.desitech.vyaparsathi.common.enums.SupplyType;
import com.desitech.vyaparsathi.common.util.LocalDateTimeAttributeConverter;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.delivery.entity.Delivery;
import com.desitech.vyaparsathi.delivery.enums.DeliveryStatus;
import com.desitech.vyaparsathi.payment.enums.PaymentStatus;
import com.desitech.vyaparsathi.sales.enums.SaleStatus;
import com.desitech.vyaparsathi.sales.enums.SaleType;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@NoArgsConstructor
@Table(
    name = "sale",
    uniqueConstraints = {
        // invoice_no must be unique within a shop, not globally.
        // A global unique constraint blocks Shop B from using INV/26-27/00001
        // when Shop A already has it. Fixed by V144 migration.
        @UniqueConstraint(name = "uq_sale_shop_invoice_no", columnNames = {"shop_id", "invoice_no"})
    }
)
public class Sale extends AuditableFinancialEntity {
    @Column(name = "invoice_no", nullable = false, length = 50)
    private String invoiceNo;

    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column(nullable = false)
    private LocalDateTime date;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    private List<Delivery> deliveries = new ArrayList<>();

    // optional helper
    public Delivery getLatestDelivery() {
        return deliveries.isEmpty() ? null : deliveries.get(0);
    }

    public boolean hasDelivery() {
        return deliveries != null && !deliveries.isEmpty();
    }

    /**
     * Optional: Get current status without fetching full list
     * (useful when you only need status, not the whole object)
     */
    public DeliveryStatus getCurrentDeliveryStatus() {
        Delivery latest = getLatestDelivery();
        return latest != null ? latest.getDeliveryStatus() : null;
    }

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "round_off", precision = 10, scale = 2)
    private BigDecimal roundOff;

    @Column(name = "synced_flag", nullable = false)
    private boolean syncedFlag = false;

    /**
     * Client-supplied idempotency key for {@code POST /api/sales}. When present,
     * a duplicate request with the same (shop_id, idempotency_key) returns the
     * originally-created sale instead of a new one — prevents double-charge on
     * network retry or POS "Charge" double-tap.
     */
    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    /** Optional user id of the salesperson responsible for this sale. */
    @Column(name = "salesperson_id")
    private Long salespersonId;

    /** Free-text notes on the sale (delivery instructions, private memo). */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<SaleItem> saleItems = new ArrayList<>();

    @Column(name = "payment_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private SaleStatus status = SaleStatus.COMPLETED;

    /**
     * Whether this row is a real tax invoice or a proforma. Defaults to
     * {@link SaleType#INVOICE}; PROFORMA rows skip stock deduction and ledger
     * posting. See {@link SaleType}.
     */
    @Column(name = "sale_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SaleType saleType = SaleType.INVOICE;

    /**
     * When this row is a real invoice created from a proforma, points to the
     * source proforma sale. Used to prevent double-conversion of a proforma
     * and to keep the audit trail intact.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proforma_source_sale_id")
    private Sale proformaSourceSale;

    /**
     * Whether GST was intended for this sale at creation time.
     * Stored so that invoices generated after a shop changes its composition-scheme
     * flag still render the correct GST columns for historical sales.
     */
    @Column(name = "is_gst_required", nullable = false)
    private Boolean isGstRequired = false;

    /**
     * Tax payable under reverse charge — recipient is liable to pay GST
     * to the government instead of the supplier. Drives PDF rendering
     * (a "Tax payable under reverse charge" note appears near the tax
     * summary) and GSTR-1 rchrg = "Y" for B2B invoices. Applies to
     * §9(3) mandatory reverse-charge supplies (GTA, legal, etc.) and
     * §9(4) supplies from unregistered persons.
     */
    @Column(name = "reverse_charge", nullable = false)
    private Boolean reverseCharge = false;

    @Column(name = "place_of_supply", length = 100)
    private String placeOfSupply;

    /**
     * Nature of the supply — intra-state, interstate, export, SEZ, etc.
     * Stored as the {@link SupplyType} enum name ({@code EnumType.STRING}) so the
     * column remains human-readable and the migration from the previous free-text
     * {@code VARCHAR(30)} is lossless for valid enum values.
     *
     * <p>This field describes the <em>supply nature</em> and drives which GST
     * components (CGST+SGST vs. IGST) are applicable. GSTR-1 table routing
     * (B2B / B2CL / B2CS) is determined separately by {@code GstTaxService}
     * based on the customer's GSTIN and invoice value.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "supply_type", length = 30)
    private SupplyType supplyType;

    @Column(name = "bill_to_party_snapshot", columnDefinition = "TEXT")
    private String billToPartySnapshot;

    @Column(name = "ship_to_party_snapshot", columnDefinition = "TEXT")
    private String shipToPartySnapshot;

    @Column(name = "consignee_party_snapshot", columnDefinition = "TEXT")
    private String consigneePartySnapshot;

    @Column(name = "due_date")
    private java.time.LocalDate dueDate;

    @Column(name = "invoice_discount", precision = 12, scale = 2)
    private BigDecimal invoiceDiscount = BigDecimal.ZERO;

    @Column(name = "shipping_charges", precision = 12, scale = 2)
    private BigDecimal shippingCharges = BigDecimal.ZERO;

    @Column(name = "other_charges", precision = 12, scale = 2)
    private BigDecimal otherCharges = BigDecimal.ZERO;

    // ── Immutable financial snapshot (MT-2 fix) ───────────────────────────────
    // Populated once when the invoice transitions to COMPLETED status.
    // SaleService.cancelSale() MUST read these before zeroing totalAmount.
    // These columns are never updated after first write — treat them as append-only.
    // Pre-existing rows have NULL here; they must be read from SaleItem aggregates
    // or recovered from AuditLog.previousValue.

    /** Grand total at the moment of invoice completion. Never zeroed on cancellation. */
    @Column(name = "original_total_amount", precision = 12, scale = 2)
    private BigDecimal originalTotalAmount;

    /** Sum of all SaleItem.taxableValue at completion. Used for GSTR-1 {@code val} field. */
    @Column(name = "original_taxable_value", precision = 12, scale = 2)
    private BigDecimal originalTaxableValue;

    /** Sum of all SaleItem.cgstAmt at completion. */
    @Column(name = "original_cgst", precision = 12, scale = 2)
    private BigDecimal originalCgst;

    /** Sum of all SaleItem.sgstAmt at completion. */
    @Column(name = "original_sgst", precision = 12, scale = 2)
    private BigDecimal originalSgst;

    /** Sum of all SaleItem.igstAmt at completion. */
    @Column(name = "original_igst", precision = 12, scale = 2)
    private BigDecimal originalIgst;

    /** Sum of all SaleItem.utgstAmt at completion (UT supplies only). */
    @Column(name = "original_utgst", precision = 12, scale = 2)
    private BigDecimal originalUtgst;

    /** Sum of all SaleItem.cessAmt at completion. Zero for non-cess supplies. */
    @Column(name = "original_cess", precision = 12, scale = 2)
    private BigDecimal originalCess;

    // ── Composite supply charge GST (CA-7) ───────────────────────────────────
    // GST computed on shipping + other charges at the invoice's highest GST rate
    // (Section 8(a) CGST Act: composite supply is taxed at the principal supply rate).
    // Null / zero for pre-Phase-2 rows or when no charges are billed.

    @Column(name = "composite_charge_cgst", precision = 12, scale = 2)
    private BigDecimal compositeChargeCgst = BigDecimal.ZERO;

    @Column(name = "composite_charge_sgst", precision = 12, scale = 2)
    private BigDecimal compositeChargeSgst = BigDecimal.ZERO;

    @Column(name = "composite_charge_igst", precision = 12, scale = 2)
    private BigDecimal compositeChargeIgst = BigDecimal.ZERO;

    @Column(name = "composite_charge_utgst", precision = 12, scale = 2)
    private BigDecimal compositeChargeUtgst = BigDecimal.ZERO;

    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }

    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public List<Delivery> getDeliveries() { return deliveries; }
    public void setDeliveries(List<Delivery> deliveries) { this.deliveries = deliveries; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getRoundOff() { return roundOff; }
    public void setRoundOff(BigDecimal roundOff) { this.roundOff = roundOff; }

    public boolean isSyncedFlag() { return syncedFlag; }
    public void setSyncedFlag(boolean syncedFlag) { this.syncedFlag = syncedFlag; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Long getSalespersonId() { return salespersonId; }
    public void setSalespersonId(Long salespersonId) { this.salespersonId = salespersonId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<SaleItem> getSaleItems() { return saleItems; }
    public void setSaleItems(List<SaleItem> saleItems) { this.saleItems = saleItems; }

    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }

    public SaleStatus getStatus() { return status; }
    public void setStatus(SaleStatus status) { this.status = status; }

    public SaleType getSaleType() { return saleType; }
    public void setSaleType(SaleType saleType) { this.saleType = saleType; }

    public Sale getProformaSourceSale() { return proformaSourceSale; }
    public void setProformaSourceSale(Sale proformaSourceSale) { this.proformaSourceSale = proformaSourceSale; }

    public boolean isProforma() { return saleType == SaleType.PROFORMA; }

    public Boolean getIsGstRequired() { return isGstRequired; }
    public void setIsGstRequired(Boolean isGstRequired) { this.isGstRequired = isGstRequired; }

    public Boolean getReverseCharge() { return reverseCharge; }
    public void setReverseCharge(Boolean reverseCharge) { this.reverseCharge = reverseCharge != null && reverseCharge; }
    public boolean isReverseCharge() { return Boolean.TRUE.equals(reverseCharge); }

    public java.time.LocalDate getDueDate() { return dueDate; }
    public void setDueDate(java.time.LocalDate dueDate) { this.dueDate = dueDate; }

    public BigDecimal getInvoiceDiscount() { return invoiceDiscount; }
    public void setInvoiceDiscount(BigDecimal invoiceDiscount) { this.invoiceDiscount = invoiceDiscount; }

    public BigDecimal getShippingCharges() { return shippingCharges; }
    public void setShippingCharges(BigDecimal shippingCharges) { this.shippingCharges = shippingCharges; }

    public BigDecimal getOtherCharges() { return otherCharges; }
    public void setOtherCharges(BigDecimal otherCharges) { this.otherCharges = otherCharges; }

    public String getPlaceOfSupply() { return placeOfSupply; }
    public void setPlaceOfSupply(String placeOfSupply) { this.placeOfSupply = placeOfSupply; }

    public SupplyType getSupplyType() { return supplyType; }
    public void setSupplyType(SupplyType supplyType) { this.supplyType = supplyType; }

    public String getBillToPartySnapshot() { return billToPartySnapshot; }
    public void setBillToPartySnapshot(String billToPartySnapshot) { this.billToPartySnapshot = billToPartySnapshot; }

    public String getShipToPartySnapshot() { return shipToPartySnapshot; }
    public void setShipToPartySnapshot(String shipToPartySnapshot) { this.shipToPartySnapshot = shipToPartySnapshot; }

    public String getConsigneePartySnapshot() { return consigneePartySnapshot; }
    public void setConsigneePartySnapshot(String consigneePartySnapshot) { this.consigneePartySnapshot = consigneePartySnapshot; }

    // --- Phase 5 E-Invoice & E-Way Bill Fields ---
    @Column(name = "irn", length = 100)
    private String irn;

    @Column(name = "ack_no", length = 50)
    private String ackNo;

    @Column(name = "ack_date")
    private LocalDateTime ackDate;

    @Column(name = "qr_code_path")
    private String qrCodePath;

    @Column(name = "einvoice_status", length = 30)
    private String einvoiceStatus = "NOT_GENERATED";

    @Column(name = "eway_bill_no", length = 50)
    private String ewayBillNo;

    @Column(name = "eway_bill_date")
    private LocalDateTime ewayBillDate;

    @Column(name = "eway_bill_valid_until")
    private LocalDateTime ewayBillValidUntil;

    @Column(name = "vehicle_number", length = 30)
    private String vehicleNumber;

    @Column(name = "transporter_id", length = 30)
    private String transporterId;

    @Column(name = "transporter_name", length = 100)
    private String transporterName;

    public Long getId() { return super.getId(); }
    public String getIrn() { return irn; }
    public void setIrn(String irn) { this.irn = irn; }

    public String getAckNo() { return ackNo; }
    public void setAckNo(String ackNo) { this.ackNo = ackNo; }

    public LocalDateTime getAckDate() { return ackDate; }
    public void setAckDate(LocalDateTime ackDate) { this.ackDate = ackDate; }

    public String getQrCodePath() { return qrCodePath; }
    public void setQrCodePath(String qrCodePath) { this.qrCodePath = qrCodePath; }

    public String getEinvoiceStatus() { return einvoiceStatus; }
    public void setEinvoiceStatus(String einvoiceStatus) { this.einvoiceStatus = einvoiceStatus; }

    public String getEwayBillNo() { return ewayBillNo; }
    public void setEwayBillNo(String ewayBillNo) { this.ewayBillNo = ewayBillNo; }

    public LocalDateTime getEwayBillDate() { return ewayBillDate; }
    public void setEwayBillDate(LocalDateTime ewayBillDate) { this.ewayBillDate = ewayBillDate; }

    public LocalDateTime getEwayBillValidUntil() { return ewayBillValidUntil; }
    public void setEwayBillValidUntil(LocalDateTime ewayBillValidUntil) { this.ewayBillValidUntil = ewayBillValidUntil; }

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public String getTransporterId() { return transporterId; }
    public void setTransporterId(String transporterId) { this.transporterId = transporterId; }

    public String getTransporterName() { return transporterName; }
    public void setTransporterName(String transporterName) { this.transporterName = transporterName; }

    public BigDecimal getOriginalTotalAmount() { return originalTotalAmount; }
    /** Called once at invoice completion. Throws if snapshot is already set (append-only guard). */
    public void setOriginalTotalAmount(BigDecimal v) {
        if (this.originalTotalAmount != null) {
            throw new IllegalStateException(
                "originalTotalAmount is immutable once set. Sale id=" + getId());
        }
        this.originalTotalAmount = v;
    }

    public BigDecimal getOriginalTaxableValue() { return originalTaxableValue; }
    public void setOriginalTaxableValue(BigDecimal v) {
        if (this.originalTaxableValue != null) {
            throw new IllegalStateException(
                "originalTaxableValue is immutable once set. Sale id=" + getId());
        }
        this.originalTaxableValue = v;
    }

    public BigDecimal getOriginalCgst() { return originalCgst; }
    public void setOriginalCgst(BigDecimal v) {
        if (this.originalCgst != null) {
            throw new IllegalStateException(
                "originalCgst is immutable once set. Sale id=" + getId());
        }
        this.originalCgst = v;
    }

    public BigDecimal getOriginalSgst() { return originalSgst; }
    public void setOriginalSgst(BigDecimal v) {
        if (this.originalSgst != null) {
            throw new IllegalStateException(
                "originalSgst is immutable once set. Sale id=" + getId());
        }
        this.originalSgst = v;
    }

    public BigDecimal getOriginalIgst() { return originalIgst; }
    public void setOriginalIgst(BigDecimal v) {
        if (this.originalIgst != null) {
            throw new IllegalStateException(
                "originalIgst is immutable once set. Sale id=" + getId());
        }
        this.originalIgst = v;
    }

    public BigDecimal getOriginalUtgst() { return originalUtgst; }
    public void setOriginalUtgst(BigDecimal v) {
        if (this.originalUtgst != null) {
            throw new IllegalStateException(
                "originalUtgst is immutable once set. Sale id=" + getId());
        }
        this.originalUtgst = v;
    }

    public BigDecimal getOriginalCess() { return originalCess; }
    public void setOriginalCess(BigDecimal v) {
        if (this.originalCess != null) {
            throw new IllegalStateException(
                "originalCess is immutable once set. Sale id=" + getId());
        }
        this.originalCess = v;
    }

    /**
     * Returns the taxable value to use for GSTR-1 reporting.
     * Prefers the immutable snapshot; falls back to the live SaleItem aggregate
     * for pre-V132 rows where the snapshot was not taken.
     */
    public BigDecimal getReportingTaxableValue() {
        return originalTaxableValue != null ? originalTaxableValue : getTaxableAmount();
    }

    /**
     * Returns the grand total to use for GSTR-1 reporting.
     * Prefers the immutable snapshot so that cancelled invoices still report correctly.
     */
    public BigDecimal getReportingTotalAmount() {
        return originalTotalAmount != null ? originalTotalAmount : totalAmount;
    }

    public BigDecimal getGrandTotal() { return totalAmount; }

    /**
     * Sum of all line-item taxable values plus composite supply charges (shipping +
     * other charges when GST is applicable). The composite principal is already stored
     * in {@code shippingCharges} / {@code otherCharges}; including them here ensures
     * GSTR-1 Table 4/9/10 taxable-value columns are correctly populated.
     */
    public BigDecimal getTaxableAmount() {
        BigDecimal items = (saleItems == null || saleItems.isEmpty()) ? BigDecimal.ZERO :
            saleItems.stream()
                .map(item -> item.getTaxableValue() != null ? item.getTaxableValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Composite supply charges are taxable when GST was applied
        BigDecimal chargeTaxable = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(isGstRequired)) {
            chargeTaxable = (shippingCharges != null ? shippingCharges : BigDecimal.ZERO)
                           .add(otherCharges != null ? otherCharges : BigDecimal.ZERO);
        }
        return items.add(chargeTaxable);
    }

    public BigDecimal getCgstAmount() {
        BigDecimal items = (saleItems == null || saleItems.isEmpty()) ? BigDecimal.ZERO :
            saleItems.stream()
                .map(item -> item.getCgstAmt() != null ? item.getCgstAmt() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return items.add(compositeChargeCgst != null ? compositeChargeCgst : BigDecimal.ZERO);
    }

    public BigDecimal getSgstAmount() {
        BigDecimal items = (saleItems == null || saleItems.isEmpty()) ? BigDecimal.ZERO :
            saleItems.stream()
                .map(item -> item.getSgstAmt() != null ? item.getSgstAmt() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return items.add(compositeChargeSgst != null ? compositeChargeSgst : BigDecimal.ZERO);
    }

    public BigDecimal getIgstAmount() {
        BigDecimal items = (saleItems == null || saleItems.isEmpty()) ? BigDecimal.ZERO :
            saleItems.stream()
                .map(item -> item.getIgstAmt() != null ? item.getIgstAmt() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return items.add(compositeChargeIgst != null ? compositeChargeIgst : BigDecimal.ZERO);
    }

    public BigDecimal getUtgstAmount() {
        BigDecimal items = (saleItems == null || saleItems.isEmpty()) ? BigDecimal.ZERO :
            saleItems.stream()
                .map(item -> item.getUtgstAmt() != null ? item.getUtgstAmt() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return items.add(compositeChargeUtgst != null ? compositeChargeUtgst : BigDecimal.ZERO);
    }

    public BigDecimal getCompositeChargeCgst() { return compositeChargeCgst != null ? compositeChargeCgst : BigDecimal.ZERO; }
    public void setCompositeChargeCgst(BigDecimal v) { this.compositeChargeCgst = v; }

    public BigDecimal getCompositeChargeSgst() { return compositeChargeSgst != null ? compositeChargeSgst : BigDecimal.ZERO; }
    public void setCompositeChargeSgst(BigDecimal v) { this.compositeChargeSgst = v; }

    public BigDecimal getCompositeChargeIgst() { return compositeChargeIgst != null ? compositeChargeIgst : BigDecimal.ZERO; }
    public void setCompositeChargeIgst(BigDecimal v) { this.compositeChargeIgst = v; }

    public BigDecimal getCompositeChargeUtgst() { return compositeChargeUtgst != null ? compositeChargeUtgst : BigDecimal.ZERO; }
    public void setCompositeChargeUtgst(BigDecimal v) { this.compositeChargeUtgst = v; }

    @PrePersist
    @Override
    public void onCreate() {
        super.onCreate();
        if (this.date == null) {
            this.date = LocalDateTime.now();
        }
    }
}