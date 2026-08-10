package com.desitech.vyaparsathi.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopItemDto {
    private Long itemId;
    private String itemName;
    private Double changePercent;
    private boolean rising;

    public Long getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public Double getChangePercent() { return changePercent; }
    public boolean isRising() { return rising; }
}
