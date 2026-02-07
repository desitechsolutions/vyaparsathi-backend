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

    public static PurchaseOrderEventDto fromEntity(PurchaseOrder po) {
        List<PurchaseOrderItemEventDto> itemDtos = po.getItems().stream()
                .map(item -> PurchaseOrderItemEventDto.builder()
                        .id(item.getId())
                        .itemVariantId(item.getItemVariant().getId())
                        .quantity(item.getQuantity())
                        .price(item.getUnitCost())
                        .build())
                .collect(Collectors.toList());

        return PurchaseOrderEventDto.builder()
                .id(po.getId())
                .poNumber(po.getPoNumber())
                .supplierId(po.getSupplier().getId())
                .orderDate(po.getOrderDate())
                .expectedDate(po.getExpectedDeliveryDate())
                .status(po.getStatus())
                .totalAmount(po.getTotalAmount())
                .items(itemDtos)
                .notes(po.getNotes())
                .paymentStatus(po.getPaymentStatus())
                .build();
    }
}