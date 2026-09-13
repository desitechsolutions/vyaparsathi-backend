package com.desitech.vyaparsathi.subscriptions.entity;

import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
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

    @Version
    private Long version;

    private String displayName;
    private Double monthlyPrice;
    private Double yearlyPrice;
    private Integer discountPercentage; // e.g., 20 for "20% off"

    // ── Time-bound promotional pricing (super admin configurable) ───────────
    private Double promoPriceMonthly;
    private Double promoPriceYearly;
    private String promoLabel;
    private LocalDateTime promoStartsAt;
    private LocalDateTime promoEndsAt;

    // ── Durable Razorpay Plan-ID cache — replaces the old in-memory cache so
    //    a restart no longer creates duplicate Razorpay Plan objects for an
    //    unchanged price point. Populated/read only by RazorpaySubscriptionService.
    private String razorpayPlanIdMonthly;
    private String razorpayPlanIdYearly;
    private Double razorpayPlanPriceMonthly;
    private Double razorpayPlanPriceYearly;

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

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Double getPromoPriceMonthly() { return promoPriceMonthly; }
    public void setPromoPriceMonthly(Double promoPriceMonthly) { this.promoPriceMonthly = promoPriceMonthly; }

    public Double getPromoPriceYearly() { return promoPriceYearly; }
    public void setPromoPriceYearly(Double promoPriceYearly) { this.promoPriceYearly = promoPriceYearly; }

    public String getPromoLabel() { return promoLabel; }
    public void setPromoLabel(String promoLabel) { this.promoLabel = promoLabel; }

    public LocalDateTime getPromoStartsAt() { return promoStartsAt; }
    public void setPromoStartsAt(LocalDateTime promoStartsAt) { this.promoStartsAt = promoStartsAt; }

    public LocalDateTime getPromoEndsAt() { return promoEndsAt; }
    public void setPromoEndsAt(LocalDateTime promoEndsAt) { this.promoEndsAt = promoEndsAt; }

    public String getRazorpayPlanIdMonthly() { return razorpayPlanIdMonthly; }
    public void setRazorpayPlanIdMonthly(String razorpayPlanIdMonthly) { this.razorpayPlanIdMonthly = razorpayPlanIdMonthly; }

    public String getRazorpayPlanIdYearly() { return razorpayPlanIdYearly; }
    public void setRazorpayPlanIdYearly(String razorpayPlanIdYearly) { this.razorpayPlanIdYearly = razorpayPlanIdYearly; }

    public Double getRazorpayPlanPriceMonthly() { return razorpayPlanPriceMonthly; }
    public void setRazorpayPlanPriceMonthly(Double razorpayPlanPriceMonthly) { this.razorpayPlanPriceMonthly = razorpayPlanPriceMonthly; }

    public Double getRazorpayPlanPriceYearly() { return razorpayPlanPriceYearly; }
    public void setRazorpayPlanPriceYearly(Double razorpayPlanPriceYearly) { this.razorpayPlanPriceYearly = razorpayPlanPriceYearly; }

    // Helper for pro-rata math
    public Double getDailyRate(boolean isYearly) {
        return isYearly ? (yearlyPrice / 365.0) : (monthlyPrice / 30.0);
    }

    /**
     * Resolves the price actually charged for a billing cycle: the promo price
     * if one is configured and {@code now} falls within its window, else the
     * base price. Single source of truth shared by Razorpay charge amount
     * resolution (via {@code RazorpayPricingService}) and admin-panel display.
     */
    public Double resolveEffectivePrice(boolean isYearly, LocalDateTime now) {
        Double basePrice = isYearly ? yearlyPrice : monthlyPrice;
        Double promoPrice = isYearly ? promoPriceYearly : promoPriceMonthly;

        if (promoPrice == null) {
            return basePrice;
        }
        if (promoStartsAt != null && now.isBefore(promoStartsAt)) {
            return basePrice;
        }
        if (promoEndsAt != null && !now.isBefore(promoEndsAt)) {
            return basePrice;
        }
        return promoPrice;
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