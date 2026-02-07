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
}
