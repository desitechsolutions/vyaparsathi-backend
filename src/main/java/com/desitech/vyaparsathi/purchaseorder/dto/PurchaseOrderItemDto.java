package com.desitech.vyaparsathi.purchaseorder.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PurchaseOrderItemDto {
    private Long id;
    private Long itemVariantId;
    private Integer quantity;
    private BigDecimal unitCost;
    private String sku;
    private String name;
}
