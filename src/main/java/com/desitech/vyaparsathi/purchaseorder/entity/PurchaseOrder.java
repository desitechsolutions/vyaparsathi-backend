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

    // ─── V85 approval workflow (Phase 3) ─────────────────────────────
    // Populated by the corresponding service transitions:
    //   * submittedBy  — user id who moved DRAFT → SUBMITTED (or → PENDING_APPROVAL)
    //   * approvedBy / approvedAt — set when OWNER/ADMIN approves PENDING_APPROVAL
    //   * rejectedBy / rejectedAt / rejectionReason — set on reject
    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejected_by")
    private Long rejectedBy;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

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

    public Long getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(Long submittedBy) { this.submittedBy = submittedBy; }

    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public Long getRejectedBy() { return rejectedBy; }
    public void setRejectedBy(Long rejectedBy) { this.rejectedBy = rejectedBy; }

    public LocalDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(LocalDateTime rejectedAt) { this.rejectedAt = rejectedAt; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    // V91: PO short-close — force-close a PO with an open remainder when the
    // supplier can't deliver the balance and both sides agree to walk away.
    @Column(name = "is_short_closed", nullable = false)
    private boolean shortClosed = false;

    @Column(name = "close_reason", length = 500)
    private String closeReason;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    public boolean isShortClosed() { return shortClosed; }
    public void setShortClosed(boolean shortClosed) { this.shortClosed = shortClosed; }

    /** V92 — opt-in flag; when true, freight is distributed to line unit cost. */
    @Column(name = "landed_cost_enabled", nullable = false)
    private boolean landedCostEnabled = false;

    public boolean isLandedCostEnabled() { return landedCostEnabled; }
    public void setLandedCostEnabled(boolean landedCostEnabled) { this.landedCostEnabled = landedCostEnabled; }

    // V95 multi-currency + recurrence.
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "exchange_rate", nullable = false, precision = 14, scale = 6)
    private java.math.BigDecimal exchangeRate = java.math.BigDecimal.ONE;

    @Column(name = "base_total_amount", precision = 14, scale = 2)
    private java.math.BigDecimal baseTotalAmount;

    @Column(name = "recurring_enabled", nullable = false)
    private boolean recurringEnabled = false;

    @Column(name = "recurring_frequency", length = 20)
    private String recurringFrequency;

    @Column(name = "recurring_next_at")
    private LocalDateTime recurringNextAt;

    @Column(name = "recurring_parent_id")
    private Long recurringParentId;

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public java.math.BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(java.math.BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }

    public java.math.BigDecimal getBaseTotalAmount() { return baseTotalAmount; }
    public void setBaseTotalAmount(java.math.BigDecimal baseTotalAmount) { this.baseTotalAmount = baseTotalAmount; }

    public boolean isRecurringEnabled() { return recurringEnabled; }
    public void setRecurringEnabled(boolean recurringEnabled) { this.recurringEnabled = recurringEnabled; }

    public String getRecurringFrequency() { return recurringFrequency; }
    public void setRecurringFrequency(String recurringFrequency) { this.recurringFrequency = recurringFrequency; }

    public LocalDateTime getRecurringNextAt() { return recurringNextAt; }
    public void setRecurringNextAt(LocalDateTime recurringNextAt) { this.recurringNextAt = recurringNextAt; }

    public Long getRecurringParentId() { return recurringParentId; }
    public void setRecurringParentId(Long recurringParentId) { this.recurringParentId = recurringParentId; }

    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String closeReason) { this.closeReason = closeReason; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }

    public Long getClosedBy() { return closedBy; }
    public void setClosedBy(Long closedBy) { this.closedBy = closedBy; }
}
