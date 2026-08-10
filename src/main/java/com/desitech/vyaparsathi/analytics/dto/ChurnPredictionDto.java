package com.desitech.vyaparsathi.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChurnPredictionDto {
    private Long customerId;
    private String customerName;
    private Double churnProbability;
    private BigDecimal revenueAtRisk;

    public Long getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public Double getChurnProbability() { return churnProbability; }
    public BigDecimal getRevenueAtRisk() { return revenueAtRisk; }
}
