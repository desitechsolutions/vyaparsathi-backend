package com.desitech.vyaparsathi.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderSuggestionDto {
    private Long itemId;
    private String itemName;
    private Double suggestedQuantity;
    private BigDecimal estimatedCost;

    public Long getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public Double getSuggestedQuantity() { return suggestedQuantity; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
}
