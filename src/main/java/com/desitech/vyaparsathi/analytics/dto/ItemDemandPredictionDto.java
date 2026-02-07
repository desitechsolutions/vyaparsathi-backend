package com.desitech.vyaparsathi.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemDemandPredictionDto {
    private Long itemId;
    private String itemName;
    private Integer predictedDemandNextMonth;
    private String trend; // e.g., "increasing", "decreasing", "stable"
}
