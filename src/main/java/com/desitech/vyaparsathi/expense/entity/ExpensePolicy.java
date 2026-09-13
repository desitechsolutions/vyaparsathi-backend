package com.desitech.vyaparsathi.expense.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Expense Policy Rules
 *
 * E.g., "Daily meal limit: ₹500", "Flight only economy class"
 */
@Entity
@Table(name = "expense_policy", indexes = {
    @Index(name = "idx_exp_pol_shop_id", columnList = "shop_id"),
    @Index(name = "idx_exp_pol_is_active", columnList = "is_active"),
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpensePolicy extends ShopAwareEntity {

    @Column(nullable = false, length = 200)
    private String name; // Daily Meal Limit, etc.

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "rule_type", length = 50)
    @Enumerated(EnumType.STRING)
    private RuleType ruleType;

    @Column(name = "affected_categories", columnDefinition = "JSON")
    private String affectedCategoriesJson; // JSON array of category IDs

    @Column(name = "limit_value", precision = 12, scale = 2)
    private BigDecimal limitValue;

    @Column(name = "frequency", length = 50)
    @Enumerated(EnumType.STRING)
    private Frequency frequency;

    @Column(name = "enforcement_action", length = 50)
    @Enumerated(EnumType.STRING)
    private EnforcementAction enforcementAction;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    public enum RuleType {
        AMOUNT_LIMIT, CATEGORY_RESTRICTION, FREQUENCY_LIMIT, REQUIRES_RECEIPT
    }

    public enum Frequency {
        DAILY, WEEKLY, MONTHLY, QUARTERLY, ANNUAL
    }

    public enum EnforcementAction {
        AUTO_REJECT, FLAG_FOR_REVIEW, ESCALATE, WARN_ONLY
    }

    @PrePersist
    @Override
    public void onCreate() {
        super.onCreate();
        if (this.isActive == null) this.isActive = true;
    }
}
