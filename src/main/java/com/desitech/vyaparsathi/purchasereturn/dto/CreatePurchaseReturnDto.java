package com.desitech.vyaparsathi.purchasereturn.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CreatePurchaseReturnDto {

    @NotNull(message = "Supplier ID is required")
    private Long supplierId;

    private Long purchaseOrderId;
    private Long receivingId;
    private LocalDateTime returnDate;
    private String notes;

    @NotEmpty(message = "Return items cannot be empty")
    @Valid
    private List<CreatePurchaseReturnItemDto> items;

    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }

    public Long getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(Long purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }

    public Long getReceivingId() { return receivingId; }
    public void setReceivingId(Long receivingId) { this.receivingId = receivingId; }

    public LocalDateTime getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDateTime returnDate) { this.returnDate = returnDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<CreatePurchaseReturnItemDto> getItems() { return items; }
    public void setItems(List<CreatePurchaseReturnItemDto> items) { this.items = items; }
}
