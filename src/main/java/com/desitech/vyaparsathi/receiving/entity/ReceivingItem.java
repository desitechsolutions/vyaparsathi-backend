package com.desitech.vyaparsathi.receiving.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import com.desitech.vyaparsathi.receiving.enums.ReceivingItemStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Setter
@Getter
@NoArgsConstructor
@Entity
public class ReceivingItem extends ShopAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiving_id", nullable = false)
    private Receiving receiving;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_item_id", nullable = false)
    private PurchaseOrderItem purchaseOrderItem;

    @Enumerated(EnumType.STRING)
    private ReceivingItemStatus status;

    @Column(nullable = false)
    private Integer expectedQty;

    @Column(nullable = false)
    private Integer receivedQty;

    private Integer damagedQty;
    private String damageReason;
    private Integer rejectedQty;
    private String rejectReason;
    private String notes;

    @Column(name = "unit_cost", precision = 12, scale = 2)
    private BigDecimal unitCost;

    /** V93 landed cost on receipt — freight-adjusted per-unit valuation. */
    @Column(name = "landed_unit_cost", precision = 12, scale = 4)
    private BigDecimal landedUnitCost;

    public BigDecimal getLandedUnitCost() { return landedUnitCost; }
    public void setLandedUnitCost(BigDecimal landedUnitCost) { this.landedUnitCost = landedUnitCost; }

    @Column(name = "putaway_qty")
    private Integer putawayQty;

    @Column(name = "put_away_status")
    private String putAwayStatus;

    @Column(name = "is_overaged")
    private Boolean isOveraged = false;

    @Column(name = "overage_reason")
    private String overageReason;

    @Column(name = "overage_notes", length = 500)
    private String overageNotes;

    @Column(name = "batch_number")
    private String batchNumber;

    @Column(name = "manufacturing_date")
    private LocalDate manufacturingDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "serial_number", length = 1000)
    private String serialNumber;

    @Column(name = "warranty_start_date")
    private LocalDate warrantyStartDate;

    @Column(name = "part_reference", length = 100)
    private String partReference;

    public Receiving getReceiving() { return receiving; }
    public void setReceiving(Receiving receiving) { this.receiving = receiving; }

    public PurchaseOrderItem getPurchaseOrderItem() { return purchaseOrderItem; }
    public void setPurchaseOrderItem(PurchaseOrderItem purchaseOrderItem) { this.purchaseOrderItem = purchaseOrderItem; }

    public ReceivingItemStatus getStatus() { return status; }
    public void setStatus(ReceivingItemStatus status) { this.status = status; }

    public Integer getExpectedQty() { return expectedQty; }
    public void setExpectedQty(Integer expectedQty) { this.expectedQty = expectedQty; }

    public Integer getReceivedQty() { return receivedQty; }
    public void setReceivedQty(Integer receivedQty) { this.receivedQty = receivedQty; }

    public Integer getDamagedQty() { return damagedQty; }
    public void setDamagedQty(Integer damagedQty) { this.damagedQty = damagedQty; }

    public String getDamageReason() { return damageReason; }
    public void setDamageReason(String damageReason) { this.damageReason = damageReason; }

    public Integer getRejectedQty() { return rejectedQty; }
    public void setRejectedQty(Integer rejectedQty) { this.rejectedQty = rejectedQty; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

    public Integer getPutawayQty() { return putawayQty; }
    public void setPutawayQty(Integer putawayQty) { this.putawayQty = putawayQty; }

    public String getPutAwayStatus() { return putAwayStatus; }
    public void setPutAwayStatus(String putAwayStatus) { this.putAwayStatus = putAwayStatus; }

    public Boolean getIsOveraged() { return isOveraged; }
    public void setIsOveraged(Boolean isOveraged) { this.isOveraged = isOveraged; }

    public String getOverageReason() { return overageReason; }
    public void setOverageReason(String overageReason) { this.overageReason = overageReason; }

    public String getOverageNotes() { return overageNotes; }
    public void setOverageNotes(String overageNotes) { this.overageNotes = overageNotes; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public LocalDate getManufacturingDate() { return manufacturingDate; }
    public void setManufacturingDate(LocalDate manufacturingDate) { this.manufacturingDate = manufacturingDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public LocalDate getWarrantyStartDate() { return warrantyStartDate; }
    public void setWarrantyStartDate(LocalDate warrantyStartDate) { this.warrantyStartDate = warrantyStartDate; }

    public String getPartReference() { return partReference; }
    public void setPartReference(String partReference) { this.partReference = partReference; }

    /**
     * Healthy units accepted into sellable stock. In this codebase received /
     * damaged / rejected are three parallel buckets — the operator enters each
     * separately (see {@code validateItemQuantities} which sums them, and
     * {@code updatePOStatus} which treats them as additive). {@code receivedQty}
     * is the healthy count; damaged and rejected are quarantined for RTV /
     * debit-note / write-off flows and never hit sellable stock directly.
     */
    public int getAcceptedQty() {
        return receivedQty != null ? Math.max(0, receivedQty) : 0;
    }

    public int getShortQty() {
        int exp = expectedQty != null ? expectedQty : 0;
        int r = receivedQty != null ? receivedQty : 0;
        return Math.max(0, exp - r);
    }

    public int getExcessQty() {
        int exp = expectedQty != null ? expectedQty : 0;
        int r = receivedQty != null ? receivedQty : 0;
        return Math.max(0, r - exp);
    }
}
