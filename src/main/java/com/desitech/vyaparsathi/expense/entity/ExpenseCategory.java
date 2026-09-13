package com.desitech.vyaparsathi.expense.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Hierarchical Expense Category
 *
 * Supports tree structure: Travel > Airfare, Accommodation, Meals
 */
@Entity
@Table(name = "expense_category", indexes = {
    @Index(name = "idx_exp_cat_shop_id", columnList = "shop_id"),
    @Index(name = "idx_exp_cat_parent_id", columnList = "parent_id"),
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseCategory extends ShopAwareEntity {

    @Column(nullable = false, length = 100)
    private String name; // Airfare, Meals, Accommodation, etc.

    @Column(name = "parent_id")
    private Long parentId; // For hierarchy

    @Column(name = "icon_code", length = 50)
    private String iconCode; // Material UI icon

    @Column(name = "color_code", length = 7)
    private String colorCode; // Hex color

    @Column(name = "budget_threshold", precision = 12, scale = 2)
    private BigDecimal budgetThreshold; // If > this, needs approval

    @Column(name = "requires_receipt")
    private Boolean requiresReceipt; // Must have receipt?

    @Column(name = "allowed_payment_methods", columnDefinition = "JSON")
    private String allowedPaymentMethodsJson; // JSON array

    @Column(name = "default_tags", columnDefinition = "JSON")
    private String defaultTagsJson; // JSON array

    private String description;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "is_active")
    private Boolean isActive;

    @PrePersist
    @Override
    public void onCreate() {
        super.onCreate();
        if (this.isActive == null) this.isActive = true;
        if (this.requiresReceipt == null) this.requiresReceipt = false;
    }
}
