package com.desitech.vyaparsathi.purchasereturn.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class PurchaseReturnItemDto {
    private Long id;
    private Long itemVariantId;
    private String itemVariantName;
    private String sku;
    private String batchNumber;
    private Integer quantity;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private String reason;
}
