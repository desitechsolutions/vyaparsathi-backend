package com.desitech.vyaparsathi.sales.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.common.util.LocalDateTimeAttributeConverter;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.delivery.entity.Delivery;
import com.desitech.vyaparsathi.delivery.enums.DeliveryStatus;
import com.desitech.vyaparsathi.payment.enums.PaymentStatus;
import com.desitech.vyaparsathi.sales.enums.SaleStatus;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@NoArgsConstructor
@Table(name = "sale")
public class Sale extends ShopAwareEntity {
    @Column(name = "invoice_no", nullable = false, unique = true, length = 50)
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
     * Whether GST was intended for this sale at creation time.
     * Stored so that invoices generated after a shop changes its composition-scheme
     * flag still render the correct GST columns for historical sales.
     */
    @Column(name = "is_gst_required", nullable = false)
    private Boolean isGstRequired = false;

    @Column(name = "place_of_supply", length = 100)
    private String placeOfSupply;

    @Column(name = "due_date")
    private java.time.LocalDate dueDate;

    @Column(name = "invoice_discount", precision = 12, scale = 2)
    private BigDecimal invoiceDiscount = BigDecimal.ZERO;

    @Column(name = "shipping_charges", precision = 12, scale = 2)
    private BigDecimal shippingCharges = BigDecimal.ZERO;

    @Column(name = "other_charges", precision = 12, scale = 2)
    private BigDecimal otherCharges = BigDecimal.ZERO;

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

    public List<SaleItem> getSaleItems() { return saleItems; }
    public void setSaleItems(List<SaleItem> saleItems) { this.saleItems = saleItems; }

    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }

    public SaleStatus getStatus() { return status; }
    public void setStatus(SaleStatus status) { this.status = status; }

    public Boolean getIsGstRequired() { return isGstRequired; }
    public void setIsGstRequired(Boolean isGstRequired) { this.isGstRequired = isGstRequired; }

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

    public BigDecimal getGrandTotal() { return totalAmount; }

    public BigDecimal getTaxableAmount() {
        if (saleItems == null || saleItems.isEmpty()) return BigDecimal.ZERO;
        return saleItems.stream()
                .map(item -> item.getTaxableValue() != null ? item.getTaxableValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getCgstAmount() {
        if (saleItems == null || saleItems.isEmpty()) return BigDecimal.ZERO;
        return saleItems.stream()
                .map(item -> item.getCgstAmt() != null ? item.getCgstAmt() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getSgstAmount() {
        if (saleItems == null || saleItems.isEmpty()) return BigDecimal.ZERO;
        return saleItems.stream()
                .map(item -> item.getSgstAmt() != null ? item.getSgstAmt() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getIgstAmount() {
        if (saleItems == null || saleItems.isEmpty()) return BigDecimal.ZERO;
        return saleItems.stream()
                .map(item -> item.getIgstAmt() != null ? item.getIgstAmt() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @PrePersist
    @Override
    public void onCreate() {
        super.onCreate();
        if (this.date == null) {
            this.date = LocalDateTime.now();
        }
    }
}