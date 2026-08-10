package com.desitech.vyaparsathi.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request DTO for creating a stock transfer between two shop locations.
 */
@Data
public class StockTransferCreateDto {

    /** ID of the shop that will lose the stock. */
    private Long fromShopId;

    /** ID of the shop that will receive the stock. */
    private Long toShopId;

    /** When the physical transfer is scheduled / occurred. Defaults to now if null. */
    private LocalDateTime transferDate;

    /** Optional free-text note (e.g. reason, vehicle number). */
    private String notes;

    /** Line items describing which variants and quantities to transfer. */
    private List<StockTransferLineDto> items;

    // ---------- accessors ----------

    public Long getFromShopId() { return fromShopId; }
    public void setFromShopId(Long fromShopId) { this.fromShopId = fromShopId; }

    public Long getToShopId() { return toShopId; }
    public void setToShopId(Long toShopId) { this.toShopId = toShopId; }

    public LocalDateTime getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDateTime transferDate) { this.transferDate = transferDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<StockTransferLineDto> getItems() { return items; }
    public void setItems(List<StockTransferLineDto> items) { this.items = items; }

    // ---------- Inner line-item DTO ----------

    @Data
    public static class StockTransferLineDto {

        /** ID of the ItemVariant to transfer. */
        private Long itemVariantId;

        /** Quantity to move (must be > 0). */
        private BigDecimal quantity;

        /** Optional batch reference. */
        private String batchNumber;

        public Long getItemVariantId() { return itemVariantId; }
        public void setItemVariantId(Long itemVariantId) { this.itemVariantId = itemVariantId; }

        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

        public String getBatchNumber() { return batchNumber; }
        public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }
    }
}
