package com.desitech.vyaparsathi.expense.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Expense Approval Workflow Tracking
 *
 * Each approval level is a separate record.
 * Enables multi-level routing (Employee → Manager → Accountant → Finance Head)
 */
@Entity
@Table(name = "expense_approval", indexes = {
    @Index(name = "idx_exp_appr_expense_id", columnList = "expense_id"),
    @Index(name = "idx_exp_appr_approver_id", columnList = "approver_id"),
    @Index(name = "idx_exp_appr_status", columnList = "status"),
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseApproval extends BaseEntity {

    @Column(name = "expense_id", nullable = false)
    private Long expenseId;

    @Column(name = "approver_id", nullable = false, length = 255)
    private String approverId; // User ID of approver

    @Column(name = "approval_level", nullable = false)
    private Integer approvalLevel; // 1, 2, 3...

    @Column(name = "status", length = 30)
    @Enumerated(EnumType.STRING)
    private ApprovalStatus status; // PENDING, APPROVED, REJECTED, ESCALATED

    @Column(name = "is_current_level")
    private Boolean isCurrentLevel; // Waiting on this level?

    @Column(name = "action_date")
    private LocalDateTime actionDate;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    public enum ApprovalStatus {
        PENDING, APPROVED, REJECTED, ESCALATED
    }

    @PrePersist
    @Override
    public void onCreate() {
        super.onCreate();
        if (this.status == null) this.status = ApprovalStatus.PENDING;
        if (this.isCurrentLevel == null) this.isCurrentLevel = true;
    }
}
