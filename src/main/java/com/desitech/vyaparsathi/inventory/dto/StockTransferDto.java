package com.desitech.vyaparsathi.inventory.dto;

import com.desitech.vyaparsathi.inventory.enums.StockTransferStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO that represents the full detail of a StockTransfer record,
 * including the resolved shop names and all line items.
 */
@Data
public class StockTransferDto {

    private Long id;
    private String transferNumber;

    private Long fromShopId;
    private String fromShopName;

    private Long toShopId;
    private String toShopName;

    private LocalDateTime transferDate;
    private StockTransferStatus status;
    private String notes;
    private LocalDateTime createdAt;

    private List<LineItemDto> items;

    // ---------- accessors ----------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTransferNumber() { return transferNumber; }
    public void setTransferNumber(String transferNumber) { this.transferNumber = transferNumber; }

    public Long getFromShopId() { return fromShopId; }
    public void setFromShopId(Long fromShopId) { this.fromShopId = fromShopId; }

    public String getFromShopName() { return fromShopName; }
    public void setFromShopName(String fromShopName) { this.fromShopName = fromShopName; }

    public Long getToShopId() { return toShopId; }
    public void setToShopId(Long toShopId) { this.toShopId = toShopId; }

    public String getToShopName() { return toShopName; }
    public void setToShopName(String toShopName) { this.toShopName = toShopName; }

    public LocalDateTime getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDateTime transferDate) { this.transferDate = transferDate; }

    public StockTransferStatus getStatus() { return status; }
    public void setStatus(StockTransferStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<LineItemDto> getItems() { return items; }
    public void setItems(List<LineItemDto> items) { this.items = items; }

    // ---------- Nested line-item DTO ----------

    @Data
    public static class LineItemDto {
        private Long itemVariantId;
        private String itemName;
        private String sku;
        private BigDecimal quantity;
        private String batchNumber;

        public Long getItemVariantId() { return itemVariantId; }
        public void setItemVariantId(Long itemVariantId) { this.itemVariantId = itemVariantId; }

        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }

        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

        public String getBatchNumber() { return batchNumber; }
        public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }
    }
}
