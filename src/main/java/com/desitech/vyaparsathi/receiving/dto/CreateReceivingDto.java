package com.desitech.vyaparsathi.receiving.dto;

import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateReceivingDto {

    private Long receivingId;
    private Long purchaseOrderId;
    private Long shopId;
    private String notes;
    private String receivedBy;

    @PastOrPresent(message = "Received date cannot be in the future")
    private LocalDateTime receivedDate;

    private List<ReceivingItemDto> receivingItems;

    public Long getReceivingId() { return receivingId; }
    public void setReceivingId(Long receivingId) { this.receivingId = receivingId; }

    public Long getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(Long purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }

    public LocalDateTime getReceivedDate() { return receivedDate; }
    public void setReceivedDate(LocalDateTime receivedDate) { this.receivedDate = receivedDate; }

    public List<ReceivingItemDto> getReceivingItems() { return receivingItems; }
    public void setReceivingItems(List<ReceivingItemDto> receivingItems) { this.receivingItems = receivingItems; }
}
