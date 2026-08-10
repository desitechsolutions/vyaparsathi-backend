package com.desitech.vyaparsathi.purchaseorder.events.dto;

import com.desitech.vyaparsathi.payment.enums.PaymentStatus;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderEventDto {
    private Long id;
    private String poNumber;
    private Long supplierId;
    private LocalDateTime orderDate;
    private LocalDateTime expectedDate;
    private PurchaseOrderStatus status;
    private BigDecimal totalAmount;
    private List<PurchaseOrderItemEventDto> items;
    private String notes;
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }

    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }

    public LocalDateTime getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDateTime orderDate) { this.orderDate = orderDate; }

    public LocalDateTime getExpectedDate() { return expectedDate; }
    public void setExpectedDate(LocalDateTime expectedDate) { this.expectedDate = expectedDate; }

    public PurchaseOrderStatus getStatus() { return status; }
    public void setStatus(PurchaseOrderStatus status) { this.status = status; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public List<PurchaseOrderItemEventDto> getItems() { return items; }
    public void setItems(List<PurchaseOrderItemEventDto> items) { this.items = items; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }

    public static PurchaseOrderEventDto fromEntity(PurchaseOrder po) {
        List<PurchaseOrderItemEventDto> itemDtos = new ArrayList<>();
        if (po.getItems() != null) {
            itemDtos = po.getItems().stream()
                    .map(item -> {
                        PurchaseOrderItemEventDto dto = new PurchaseOrderItemEventDto();
                        dto.setId(item.getId());
                        dto.setItemVariantId(item.getItemVariant() != null ? item.getItemVariant().getId() : null);
                        dto.setQuantity(item.getQuantity());
                        dto.setPrice(item.getUnitCost());
                        return dto;
                    })
                    .collect(Collectors.toList());
        }

        PurchaseOrderEventDto dto = new PurchaseOrderEventDto();
        dto.setId(po.getId());
        dto.setPoNumber(po.getPoNumber());
        dto.setSupplierId(po.getSupplier() != null ? po.getSupplier().getId() : null);
        dto.setOrderDate(po.getOrderDate());
        dto.setExpectedDate(po.getExpectedDeliveryDate());
        dto.setStatus(po.getStatus());
        dto.setTotalAmount(po.getTotalAmount());
        dto.setItems(itemDtos);
        dto.setNotes(po.getNotes());
        dto.setPaymentStatus(po.getPaymentStatus());
        return dto;
    }
}