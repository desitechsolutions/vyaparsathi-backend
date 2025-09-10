package com.desitech.vyaparsathi.purchaseorder.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public  class PurchaseOrderItemEventDto {
    private Long id;
    private Long itemVariantId;
    private Integer quantity;
    private BigDecimal price;
}