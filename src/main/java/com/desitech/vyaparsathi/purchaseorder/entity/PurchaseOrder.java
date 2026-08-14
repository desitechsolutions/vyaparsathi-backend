package com.desitech.vyaparsathi.purchaseorder.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.payment.enums.PaymentStatus;
import com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "purchase_order")
public class PurchaseOrder extends ShopAwareEntity {

    @Column(name = "po_number", nullable = false, unique = true)
    private String poNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @Column(name = "expected_delivery_date")
    private LocalDateTime expectedDeliveryDate;

    // Default to ZERO so the first Hibernate insert is legal — recompute
    // fills the real total in the same transaction. Previously null on
    // insert, causing MySQL 'Column total_amount cannot be null' on the
    // duplicate flow which saves the header before recomputeTotals runs.
    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PurchaseOrderStatus status;

    @Column
    private String notes;

    @Column(name = "payment_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    // ─── State-machine audit stamps (V81) ─────────────────────────────
    // Populated by the corresponding service transitions:
    //  * sentAt        — sendToSupplier() records when the PO was emailed.
    //  * receivedAt    — markAsReceived() records final fulfilment.
    //  * cancelledAt / cancelledBy / cancellationReason — cancelPurchaseOrder().

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    // ─── V83 header totals ────────────────────────────────────────────
    // totalAmount stays authoritative (existing column). These are the
    // derived summands so the FE can render a Zoho-style breakdown
    // without recomputing on the client. Recompute happens in
    // PurchaseOrderService whenever lines change.
    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "total_discount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDiscount = BigDecimal.ZERO;

    @Column(name = "total_tax", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalTax = BigDecimal.ZERO;

    @Column(name = "freight_charges", nullable = false, precision = 12, scale = 2)
    private BigDecimal freightCharges = BigDecimal.ZERO;

    @Column(name = "round_off", nullable = false, precision = 6, scale = 2)
    private BigDecimal roundOff = BigDecimal.ZERO;

    @JsonManagedReference
    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderItem> items;

    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }

    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }

    public LocalDateTime getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDateTime orderDate) { this.orderDate = orderDate; }

    public LocalDateTime getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public void setExpectedDeliveryDate(LocalDateTime expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public PurchaseOrderStatus getStatus() { return status; }
    public void setStatus(PurchaseOrderStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }

    public List<PurchaseOrderItem> getItems() { return items; }
    public void setItems(List<PurchaseOrderItem> items) { this.items = items; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public Long getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(Long cancelledBy) { this.cancelledBy = cancelledBy; }

    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal == null ? BigDecimal.ZERO : subtotal; }

    public BigDecimal getTotalDiscount() { return totalDiscount; }
    public void setTotalDiscount(BigDecimal totalDiscount) { this.totalDiscount = totalDiscount == null ? BigDecimal.ZERO : totalDiscount; }

    public BigDecimal getTotalTax() { return totalTax; }
    public void setTotalTax(BigDecimal totalTax) { this.totalTax = totalTax == null ? BigDecimal.ZERO : totalTax; }

    public BigDecimal getFreightCharges() { return freightCharges; }
    public void setFreightCharges(BigDecimal freightCharges) { this.freightCharges = freightCharges == null ? BigDecimal.ZERO : freightCharges; }

    public BigDecimal getRoundOff() { return roundOff; }
    public void setRoundOff(BigDecimal roundOff) { this.roundOff = roundOff == null ? BigDecimal.ZERO : roundOff; }
}
