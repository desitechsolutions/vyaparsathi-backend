package com.desitech.vyaparsathi.receiving.dto;

import com.desitech.vyaparsathi.receiving.enums.ReceivingItemStatus;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ReceivingItemDto {
    private Long id;
    private Long purchaseOrderItemId;
    private Long itemVariantId;
    private String sku;
    private String name;
    private BigDecimal unitCost;
    private ReceivingItemStatus status;
    @Min(value = 0, message = "Expected quantity cannot be negative")
    private Integer expectedQty;
    @Min(value = 0, message = "Received quantity cannot be negative")
    private Integer receivedQty;
    @Min(value = 0, message = "Damaged quantity cannot be negative")
    private Integer damagedQty;
    private String damageReason;
    private String notes;
    private String putAwayStatus;
    @Min(value = 0, message = "Rejected quantity cannot be negative")
    private Integer rejectedQty;
    private String rejectReason;
    @Min(value = 0, message = "Putaway quantity cannot be negative")
    private Integer putawayQty;
    private String overageReason;
    private String overageNotes;
    private Boolean isOveraged = false;

    private String batchNumber;
    private LocalDate manufacturingDate;
    private LocalDate expiryDate;
    private String serialNumber;
    private LocalDate warrantyStartDate;
    private String partReference;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPurchaseOrderItemId() { return purchaseOrderItemId; }
    public void setPurchaseOrderItemId(Long purchaseOrderItemId) { this.purchaseOrderItemId = purchaseOrderItemId; }

    public Long getItemVariantId() { return itemVariantId; }
    public void setItemVariantId(Long itemVariantId) { this.itemVariantId = itemVariantId; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

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

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getPutAwayStatus() { return putAwayStatus; }
    public void setPutAwayStatus(String putAwayStatus) { this.putAwayStatus = putAwayStatus; }

    public Integer getRejectedQty() { return rejectedQty; }
    public void setRejectedQty(Integer rejectedQty) { this.rejectedQty = rejectedQty; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public Integer getPutawayQty() { return putawayQty; }
    public void setPutawayQty(Integer putawayQty) { this.putawayQty = putawayQty; }

    public String getOverageReason() { return overageReason; }
    public void setOverageReason(String overageReason) { this.overageReason = overageReason; }

    public String getOverageNotes() { return overageNotes; }
    public void setOverageNotes(String overageNotes) { this.overageNotes = overageNotes; }

    public Boolean getIsOveraged() { return isOveraged; }
    public void setIsOveraged(Boolean isOveraged) { this.isOveraged = isOveraged; }

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

    public Integer getAcceptedQty() {
        int r = receivedQty != null ? receivedQty : 0;
        int d = damagedQty != null ? damagedQty : 0;
        int rej = rejectedQty != null ? rejectedQty : 0;
        return Math.max(0, r - d - rej);
    }

    public Integer getShortQty() {
        int exp = expectedQty != null ? expectedQty : 0;
        int r = receivedQty != null ? receivedQty : 0;
        return Math.max(0, exp - r);
    }

    public Integer getExcessQty() {
        int exp = expectedQty != null ? expectedQty : 0;
        int r = receivedQty != null ? receivedQty : 0;
        return Math.max(0, r - exp);
    }

    public BigDecimal getLineTotal() {
        if (unitCost == null) return BigDecimal.ZERO;
        return unitCost.multiply(BigDecimal.valueOf(getAcceptedQty()));
    }
}
