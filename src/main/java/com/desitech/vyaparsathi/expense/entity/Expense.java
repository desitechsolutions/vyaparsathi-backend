package com.desitech.vyaparsathi.expense.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.common.util.LocalDateTimeAttributeConverter;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Enterprise-Grade Expense Entity
 *
 * Supports:
 * - Multi-level approval workflows
 * - Expense policies & enforcement
 * - Receipt management with OCR
 * - Recurring expenses
 * - Budget tracking
 * - Multi-currency support
 * - Comprehensive audit trail
 */
@Entity
@Table(name = "expense", indexes = {
    @Index(name = "idx_expense_shop_id", columnList = "shop_id"),
    @Index(name = "idx_expense_employee_id", columnList = "employee_id"),
    @Index(name = "idx_expense_category_id", columnList = "expense_category_id"),
    @Index(name = "idx_expense_status", columnList = "status"),
    @Index(name = "idx_expense_date", columnList = "expense_date"),
    @Index(name = "idx_expense_shop_status", columnList = "shop_id,status"),
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense extends ShopAwareEntity {

    // ── Legacy Compatibility ─────────────────────────────────────────
    @Column(nullable = false, length = 100)
    private String type; // Category name (for backward compatibility)

    // ── Core Expense Data ────────────────────────────────────────────
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDateTime date;

    private String notes;

    // ── Enterprise Fields ────────────────────────────────────────────
    @Column(name = "employee_id", length = 255)
    private String employeeId; // User who submitted

    @Column(name = "expense_category_id")
    private Long expenseCategoryId; // FK to ExpenseCategory (hierarchical)

    @Column(name = "vendor_name", length = 255)
    private String vendorName; // Who did you buy from?

    @Column(name = "vendor_id")
    private Long vendorId; // FK to VendorMaster

    @Column(name = "expense_date")
    private LocalDate expenseDate; // When incurred (vs date = when created)

    @Column(name = "payment_method", length = 50)
    private String paymentMethod; // CASH, CARD, UPI, BANK_TRANSFER, REIMBURSEMENT

    @Column(name = "currency", length = 3)
    private String currency; // INR, USD, EUR (default INR)

    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // What was this for?

    @Column(name = "cost_center", length = 100)
    private String costCenter; // Project/Department code

    @Column(name = "tags", columnDefinition = "JSON")
    private String tagsJson; // JSON array of tags

    @Column(name = "receipt_id")
    private Long receiptId; // FK to Receipt entity

    @Column(name = "receipt_path", length = 500)
    private String receiptPath; // Path to receipt file

    // ── Approval & Status ────────────────────────────────────────────
    @Column(name = "status", length = 30)
    @Enumerated(EnumType.STRING)
    private ExpenseStatus status; // DRAFT, SUBMITTED, PENDING_APPROVAL, APPROVED, REJECTED, REIMBURSED

    @Column(name = "approval_chain_id")
    private Long approvalChainId; // FK to approval workflow definition

    @Column(name = "submission_date")
    private LocalDateTime submissionDate; // When submitted for approval

    @Column(name = "approved_date")
    private LocalDateTime approvedDate; // When final approval given

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "reimbursement_date")
    private LocalDateTime reimbursementDate; // When payment issued

    // ── Policy & Compliance ──────────────────────────────────────────
    @Column(name = "policy_violations", columnDefinition = "JSON")
    private String policyViolationsJson; // JSON: [{ rule_id, violation_reason }]

    @Column(name = "requires_escalation")
    private Boolean requiresEscalation; // Flagged for manual review?

    @Column(name = "escalation_reason", length = 500)
    private String escalationReason;

    // ── Recurring Expenses ───────────────────────────────────────────
    @Column(name = "is_recurring")
    private Boolean isRecurring; // Is this instance of a recurring expense?

    @Column(name = "recurring_expense_id")
    private Long recurringExpenseId; // FK to RecurringExpense

    // ── Soft Delete ──────────────────────────────────────────────────
    @Column(name = "is_deleted")
    private Boolean isDeleted; // Soft delete flag

    // ── Audit Trail ──────────────────────────────────────────────────
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // ── Status Enum ──────────────────────────────────────────────────
    public enum ExpenseStatus {
        DRAFT,            // Not submitted
        SUBMITTED,        // Sent for approval
        PENDING_APPROVAL, // In approval queue
        APPROVED,         // All approvals done
        REJECTED,         // Rejected by approver
        REIMBURSED,       // Payment issued
        CANCELLED         // Voided
    }

    // ── Lifecycle Hooks ──────────────────────────────────────────────
    @PrePersist
    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Override
    public void onCreate() {
        super.onCreate();
        if (this.date == null) {
            this.date = LocalDateTime.now();
        }
        if (this.expenseDate == null) {
            this.expenseDate = LocalDate.now();
        }
        if (this.status == null) {
            this.status = ExpenseStatus.DRAFT;
        }
        if (this.currency == null) {
            this.currency = "INR";
        }
        if (this.isRecurring == null) {
            this.isRecurring = false;
        }
        if (this.requiresEscalation == null) {
            this.requiresEscalation = false;
        }
        if (this.isDeleted == null) {
            this.isDeleted = false;
        }
    }

    @PreUpdate
    public void onUpdate() {
        super.onUpdate();
    }
}