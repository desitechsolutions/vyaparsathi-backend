package com.desitech.vyaparsathi.expense.dto;

import com.desitech.vyaparsathi.common.util.CustomLocalDateTimeDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Enterprise Expense DTO
 *
 * Supports full expense lifecycle: draft → submitted → approval → reimbursement
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseDto {
    // ── Legacy fields (for backward compatibility) ──────────────────
    private Long id;
    @NotNull(message = "Shop ID cannot be null")
    private Long shopId;
    @NotBlank(message = "Expense type cannot be blank")
    private String type; // Category name
    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime date;
    private String notes;

    // ── Enterprise fields ────────────────────────────────────────────
    private String employeeId; // Who submitted
    private Long expenseCategoryId; // FK to ExpenseCategory
    private String vendorName; // Merchant name
    private Long vendorId; // FK to VendorMaster
    private LocalDate expenseDate; // When incurred
    private String paymentMethod; // CASH, CARD, UPI, etc.
    private String currency; // INR, USD, EUR
    private String description; // What for?
    private String costCenter; // Project/Department code
    private List<String> tags; // Custom tags
    private Long receiptId; // FK to Receipt
    private String receiptPath; // File path
    private String status; // DRAFT, SUBMITTED, PENDING_APPROVAL, APPROVED, REJECTED, REIMBURSED
    private Long approvalChainId; // FK to approval workflow
    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime submissionDate;
    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime approvedDate;
    private String rejectionReason;
    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime reimbursementDate;
    private List<String> policyViolations; // Violated rules
    private Boolean requiresEscalation; // Flag for manual review?
    private String escalationReason;
    private Boolean isRecurring; // Instance of recurring?
    private Long recurringExpenseId; // FK to RecurringExpense
    private List<ApprovalDto> approvalTimeline; // Approval chain state

    // ── Nested DTOs ──────────────────────────────────────────────────
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApprovalDto {
        private Long id;
        private Integer level;
        private String approverId;
        private String status; // PENDING, APPROVED, REJECTED, ESCALATED
        @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
        private LocalDateTime actionDate;
        private String comment;
        private Boolean isCurrentLevel;
    }
}