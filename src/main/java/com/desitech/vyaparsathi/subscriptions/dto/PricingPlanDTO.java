package com.desitech.vyaparsathi.subscriptions.dto;

import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import lombok.Data;
import java.util.List;

@Data
public class PricingPlanDTO {
    private Tier tier;
    private String displayName;
    private Double monthlyPrice;
    private Double yearlyPrice;
    private Integer discountPercentage;
    private Boolean isPopular;
    private List<String> features;
    private Boolean isActive;
    private Integer maxSalesPerMonth;
    private Integer maxItems;
    private Integer maxStaffUsers;
    private Boolean canProcessSale;
}