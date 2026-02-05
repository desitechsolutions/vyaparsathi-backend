package com.desitech.vyaparsathi.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderSuggestionDto {
    private Long itemId;
    private String itemName;
    private Double suggestedQuantity;
    private java.math.BigDecimal estimatedCost;
}
