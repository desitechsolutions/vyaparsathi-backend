package com.desitech.vyaparsathi.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockAdjustmentDto {
    private Long itemVariantId;
    private BigDecimal adjustmentQuantity; // positive for increase, negative for decrease
    private BigDecimal costPerUnit; // optional, if not provided, use average cost
    private String reason; // required field
    private String batch; // optional

    public Long getItemVariantId() { return itemVariantId; }
    public void setItemVariantId(Long itemVariantId) { this.itemVariantId = itemVariantId; }

    public BigDecimal getAdjustmentQuantity() { return adjustmentQuantity; }
    public void setAdjustmentQuantity(BigDecimal adjustmentQuantity) { this.adjustmentQuantity = adjustmentQuantity; }

    public BigDecimal getCostPerUnit() { return costPerUnit; }
    public void setCostPerUnit(BigDecimal costPerUnit) { this.costPerUnit = costPerUnit; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }
}