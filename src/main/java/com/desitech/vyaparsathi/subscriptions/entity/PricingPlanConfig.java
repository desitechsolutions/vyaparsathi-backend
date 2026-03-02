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