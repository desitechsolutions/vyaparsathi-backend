package com.desitech.vyaparsathi.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerTrendDto {
    private Long customerId;
    private String customerName;
    private String buyingPattern; // e.g., "weekly", "monthly", "seasonal"
    private List<String> frequentlyBoughtItems;

    public Long getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public String getBuyingPattern() { return buyingPattern; }
    public List<String> getFrequentlyBoughtItems() { return frequentlyBoughtItems; }
}