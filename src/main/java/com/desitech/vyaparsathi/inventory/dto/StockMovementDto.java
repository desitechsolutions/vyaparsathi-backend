package com.desitech.vyaparsathi.inventory.dto;

import com.desitech.vyaparsathi.inventory.enums.StockMovementType;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.desitech.vyaparsathi.common.util.CustomLocalDateTimeDeserializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class StockMovementDto {
    private Long id;
    private Long itemVariantId;
    private String itemName;
    private String sku;
    private StockMovementType movementType; // ADD, DEDUCT, ADJUST
    private BigDecimal quantity;
    private BigDecimal costPerUnit;
    private String batch;
    private String reason;
    private String reference; // e.g., "Sale #INV-001", "Purchase Order #PO-001"
    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime timestamp;
    private LocalDate expiryDate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getItemVariantId() { return itemVariantId; }
    public void setItemVariantId(Long itemVariantId) { this.itemVariantId = itemVariantId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public StockMovementType getMovementType() { return movementType; }
    public void setMovementType(StockMovementType movementType) { this.movementType = movementType; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getCostPerUnit() { return costPerUnit; }
    public void setCostPerUnit(BigDecimal costPerUnit) { this.costPerUnit = costPerUnit; }

    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
}