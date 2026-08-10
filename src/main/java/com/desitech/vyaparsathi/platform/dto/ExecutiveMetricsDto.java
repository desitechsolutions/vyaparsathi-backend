package com.desitech.vyaparsathi.platform.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ExecutiveMetricsDto {
    private long totalShops;
    private long activeShops;
    private long trialShops;
    private long suspendedShops;
    private long payingShops;
    private BigDecimal mrr;
    private BigDecimal arr;
    private double trialConversionRate;
    private String systemStatus = "HEALTHY";
}
