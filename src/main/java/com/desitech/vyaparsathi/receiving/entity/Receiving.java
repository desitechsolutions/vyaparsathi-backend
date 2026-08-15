package com.desitech.vyaparsathi.receiving.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.receiving.enums.ReceivingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
public class Receiving extends ShopAwareEntity {

    @Column(name = "gr_number", length = 50)
    private String grNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReceivingStatus status;

    @Column(name = "supplier_invoice_no", length = 100)
    private String supplierInvoiceNo;

    @Column(name = "supplier_invoice_date")
    private java.time.LocalDate supplierInvoiceDate;

    @Column(name = "vehicle_no", length = 50)
    private String vehicleNo;

    @Column(name = "delivery_challan_no", length = 100)
    private String deliveryChallanNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private com.desitech.vyaparsathi.auth.entity.User approvedByUser;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** Free-form note captured at approval time (V89). */
    @Column(name = "approval_note", length = 500)
    private String approvalNote;

    // V90 cancellation audit — populated when a GRN is voided.
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by_user_id")
    private Long cancelledByUserId;

    // V91 enterprise fields — pre-receipt planning + variance flags.
    @Column(name = "expected_delivery_date")
    private java.time.LocalDate expectedDeliveryDate;

    @Column(name = "dock_bay", length = 50)
    private String dockBay;

    @Column(name = "checklist_json", columnDefinition = "TEXT")
    private String checklistJson;

    @Column(name = "digital_signature_url", length = 500)
    private String digitalSignatureUrl;

    @Column(name = "cost_variance_flag", nullable = false)
    private boolean costVarianceFlag = false;

    @Column(name = "auto_ticket_raised", nullable = false)
    private boolean autoTicketRaised = false;

    /** V93 — freight paid at delivery (may differ from PO estimate). */
    @Column(name = "freight_actual", precision = 12, scale = 2)
    private java.math.BigDecimal freightActual;

    /** V93 — when on, {@code freightActual} distributes into line landed cost. */
    @Column(name = "landed_cost_enabled", nullable = false)
    private boolean landedCostEnabled = false;

    @CreatedDate
    private LocalDateTime receivedAt;

    @CreatedBy
    @Column(nullable = false, updatable = false)
    private String receivedBy;

    private String notes;

    @OneToMany(mappedBy = "receiving", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReceivingItem> items;

    @LastModifiedDate
    private LocalDateTime lastUpdatedAt;

    public String getGrNumber() { return grNumber; }
    public void setGrNumber(String grNumber) { this.grNumber = grNumber; }

    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public void setPurchaseOrder(PurchaseOrder purchaseOrder) { this.purchaseOrder = purchaseOrder; }

    public ReceivingStatus getStatus() { return status; }
    public void setStatus(ReceivingStatus status) { this.status = status; }

    public String getSupplierInvoiceNo() { return supplierInvoiceNo; }
    public void setSupplierInvoiceNo(String supplierInvoiceNo) { this.supplierInvoiceNo = supplierInvoiceNo; }

    public java.time.LocalDate getSupplierInvoiceDate() { return supplierInvoiceDate; }
    public void setSupplierInvoiceDate(java.time.LocalDate supplierInvoiceDate) { this.supplierInvoiceDate = supplierInvoiceDate; }

    public String getVehicleNo() { return vehicleNo; }
    public void setVehicleNo(String vehicleNo) { this.vehicleNo = vehicleNo; }

    public String getDeliveryChallanNo() { return deliveryChallanNo; }
    public void setDeliveryChallanNo(String deliveryChallanNo) { this.deliveryChallanNo = deliveryChallanNo; }

    public com.desitech.vyaparsathi.auth.entity.User getApprovedByUser() { return approvedByUser; }
    public void setApprovedByUser(com.desitech.vyaparsathi.auth.entity.User approvedByUser) { this.approvedByUser = approvedByUser; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public String getApprovalNote() { return approvalNote; }
    public void setApprovalNote(String approvalNote) { this.approvalNote = approvalNote; }

    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public Long getCancelledByUserId() { return cancelledByUserId; }
    public void setCancelledByUserId(Long cancelledByUserId) { this.cancelledByUserId = cancelledByUserId; }

    public java.time.LocalDate getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public void setExpectedDeliveryDate(java.time.LocalDate expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; }

    public String getDockBay() { return dockBay; }
    public void setDockBay(String dockBay) { this.dockBay = dockBay; }

    public String getChecklistJson() { return checklistJson; }
    public void setChecklistJson(String checklistJson) { this.checklistJson = checklistJson; }

    public String getDigitalSignatureUrl() { return digitalSignatureUrl; }
    public void setDigitalSignatureUrl(String digitalSignatureUrl) { this.digitalSignatureUrl = digitalSignatureUrl; }

    public boolean isCostVarianceFlag() { return costVarianceFlag; }
    public void setCostVarianceFlag(boolean costVarianceFlag) { this.costVarianceFlag = costVarianceFlag; }

    public boolean isAutoTicketRaised() { return autoTicketRaised; }
    public void setAutoTicketRaised(boolean autoTicketRaised) { this.autoTicketRaised = autoTicketRaised; }

    public java.math.BigDecimal getFreightActual() { return freightActual; }
    public void setFreightActual(java.math.BigDecimal freightActual) { this.freightActual = freightActual; }

    public boolean isLandedCostEnabled() { return landedCostEnabled; }
    public void setLandedCostEnabled(boolean landedCostEnabled) { this.landedCostEnabled = landedCostEnabled; }

    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }

    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<ReceivingItem> getItems() { return items; }
    public void setItems(List<ReceivingItem> items) { this.items = items; }

    public LocalDateTime getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }
}