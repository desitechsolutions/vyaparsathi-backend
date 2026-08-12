package com.desitech.vyaparsathi.delivery.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.delivery.enums.DeliveryPaidBy;
import com.desitech.vyaparsathi.delivery.enums.DeliveryStatus;
import com.desitech.vyaparsathi.sales.entity.Sale;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "deliveries")
public class Delivery extends ShopAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    /**
     * Delivery challan number in the format {@code DC/YY-YY/NNNNN} — issued by
     * {@code DeliveryChallanNumberService} on first PDF request. Historical
     * delivery rows (predating V66) may have this null until the challan PDF
     * is generated for them, at which point it is lazy-assigned.
     */
    @Column(name = "challan_no", length = 50)
    private String challanNo;

    private String invoiceNumber;
    private String customerName;

    @Column(columnDefinition = "TEXT")
    private String deliveryAddress;

    private Double deliveryCharge;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryPaidBy deliveryPaidBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @ManyToOne
    @JoinColumn(name = "delivery_person_id")
    private DeliveryPerson deliveryPerson;

    private String deliveryNotes;

    private LocalDateTime deliveredAt;

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeliveryStatusHistory> statusHistory;

    /**
     * Per-line dispatch record. When populated, the challan PDF renders a
     * proper item table (matching Rule 55 requirements). When empty, the
     * challan falls back to listing the parent Sale's items in full.
     */
    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<DeliveryItem> items = new ArrayList<>();

    // ── Planning ─────────────────────────────────────────────────────
    @Column(name = "estimated_delivery_date")
    private LocalDate estimatedDeliveryDate;

    /**
     * Address as it existed when the delivery was created. Independent of
     * subsequent changes to the customer record — the challan PDF and audit
     * trail must reflect the address on the dispatch date, not the address
     * the customer moved to later.
     */
    @Column(name = "delivery_address_snapshot", columnDefinition = "TEXT")
    private String deliveryAddressSnapshot;

    // ── Proof-of-delivery ─────────────────────────────────────────────
    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "pod_signature_url", length = 1024)
    private String podSignatureUrl;

    @Column(name = "pod_photo_url", length = 1024)
    private String podPhotoUrl;

    @Column(name = "pod_otp", length = 20)
    private String podOtp;

    @Column(name = "pod_collected_at")
    private LocalDateTime podCollectedAt;

    // ── Cash-on-delivery ─────────────────────────────────────────────
    @Column(name = "cod_amount", precision = 12, scale = 2)
    private BigDecimal codAmount;

    @Column(name = "cod_collected", nullable = false)
    private boolean codCollected = false;

    @Column(name = "cod_collected_at")
    private LocalDateTime codCollectedAt;

    // ── Attempts / failure log ───────────────────────────────────────
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    // ── Logistics ────────────────────────────────────────────────────
    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "eway_bill_no", length = 50)
    private String ewayBillNo;

    @Column(name = "courier_partner", length = 100)
    private String courierPartner;

    public Sale getSale() { return sale; }
    public void setSale(Sale sale) { this.sale = sale; }

    public String getChallanNo() { return challanNo; }
    public void setChallanNo(String challanNo) { this.challanNo = challanNo; }

    public List<DeliveryItem> getItems() { return items; }
    public void setItems(List<DeliveryItem> items) { this.items = items; }

    public void addItem(DeliveryItem item) {
        item.setDelivery(this);
        this.items.add(item);
    }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }

    public Double getDeliveryCharge() { return deliveryCharge; }
    public void setDeliveryCharge(Double deliveryCharge) { this.deliveryCharge = deliveryCharge; }

    public DeliveryPaidBy getDeliveryPaidBy() { return deliveryPaidBy; }
    public void setDeliveryPaidBy(DeliveryPaidBy deliveryPaidBy) { this.deliveryPaidBy = deliveryPaidBy; }

    public DeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(DeliveryStatus deliveryStatus) { this.deliveryStatus = deliveryStatus; }

    public DeliveryPerson getDeliveryPerson() { return deliveryPerson; }
    public void setDeliveryPerson(DeliveryPerson deliveryPerson) { this.deliveryPerson = deliveryPerson; }

    public String getDeliveryNotes() { return deliveryNotes; }
    public void setDeliveryNotes(String deliveryNotes) { this.deliveryNotes = deliveryNotes; }

    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }

    public List<DeliveryStatusHistory> getStatusHistory() { return statusHistory; }
    public void setStatusHistory(List<DeliveryStatusHistory> statusHistory) { this.statusHistory = statusHistory; }
}