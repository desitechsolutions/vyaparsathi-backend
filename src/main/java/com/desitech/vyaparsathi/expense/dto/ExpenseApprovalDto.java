package com.desitech.vyaparsathi.expense.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseApprovalDto {
    private Long id;
    private Long expenseId;
    private String approverId;
    private Integer level;
    private String status; // PENDING, APPROVED, REJECTED, ESCALATED
    private LocalDateTime actionDate;
    private String comment;
    private String rejectionReason;
    private Boolean isCurrentLevel;
}
