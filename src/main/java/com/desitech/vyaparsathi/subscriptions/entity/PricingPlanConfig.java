package com.desitech.vyaparsathi.subscriptions.entity;

import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pricing_plan_configs")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class PricingPlanConfig {

    @Id
    @Enumerated(EnumType.STRING)
    private Tier tier;

    private String displayName;
    private Double monthlyPrice;
    private Double yearlyPrice;
    private Integer discountPercentage; // e.g., 20 for "20% off"

    private Boolean isPopular; // For the "Most Loved" badge

    @ElementCollection
    @CollectionTable(name = "plan_features", joinColumns = @JoinColumn(name = "tier"))
    @Column(name = "feature")
    private List<String> features; // ["Unlimited Invoices", "GST Reports"]

    private Boolean isActive;
    private Integer sortOrder;

    private Integer maxSalesPerMonth;
    private Integer maxItems;
    private Integer maxStaffUsers;

    @Column(name = "can_process_sale")
    private Boolean canProcessSale = true;

    public Tier getTier() { return tier; }
    public void setTier(Tier tier) { this.tier = tier; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Double getMonthlyPrice() { return monthlyPrice; }
    public void setMonthlyPrice(Double monthlyPrice) { this.monthlyPrice = monthlyPrice; }

    public Double getYearlyPrice() { return yearlyPrice; }
    public void setYearlyPrice(Double yearlyPrice) { this.yearlyPrice = yearlyPrice; }

    public Integer getDiscountPercentage() { return discountPercentage; }
    public void setDiscountPercentage(Integer discountPercentage) { this.discountPercentage = discountPercentage; }

    public Boolean getIsPopular() { return isPopular; }
    public void setIsPopular(Boolean isPopular) { this.isPopular = isPopular; }

    public List<String> getFeatures() { return features; }
    public void setFeatures(List<String> features) { this.features = features; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Integer getMaxSalesPerMonth() { return maxSalesPerMonth; }
    public void setMaxSalesPerMonth(Integer maxSalesPerMonth) { this.maxSalesPerMonth = maxSalesPerMonth; }

    public Integer getMaxItems() { return maxItems; }
    public void setMaxItems(Integer maxItems) { this.maxItems = maxItems; }

    public Integer getMaxStaffUsers() { return maxStaffUsers; }
    public void setMaxStaffUsers(Integer maxStaffUsers) { this.maxStaffUsers = maxStaffUsers; }

    public Boolean getCanProcessSale() { return canProcessSale; }
    public void setCanProcessSale(Boolean canProcessSale) { this.canProcessSale = canProcessSale; }

    // Helper for pro-rata math
    public Double getDailyRate(boolean isYearly) {
        return isYearly ? (yearlyPrice / 365.0) : (monthlyPrice / 30.0);
    }

    public void addFeature(String feature) {
        if (this.features == null) {
            this.features = new ArrayList<>();
        }
        this.features.add(feature);
    }
    public void clearFeatures() {
        if (this.features != null) {
            this.features.clear();
        }
    }
}