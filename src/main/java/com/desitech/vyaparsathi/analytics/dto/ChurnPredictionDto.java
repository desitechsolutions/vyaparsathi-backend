package com.desitech.vyaparsathi.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChurnPredictionDto {
    private Long customerId;
    private String customerName;
    private Double churnProbability;
    private java.math.BigDecimal revenueAtRisk;
}
