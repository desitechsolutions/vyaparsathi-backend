package com.desitech.vyaparsathi.expense.mapper;

import com.desitech.vyaparsathi.expense.dto.ExpenseDto;
import com.desitech.vyaparsathi.expense.dto.UpdateExpenseDto;
import com.desitech.vyaparsathi.expense.entity.Expense;
import com.desitech.vyaparsathi.expense.entity.ExpenseApproval;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * MapStruct mapper for Expense entity ↔ DTOs
 *
 * Handles conversion between entities and DTOs with support for:
 * - Legacy expense fields (type, amount, date, notes)
 * - Enterprise expense fields (category, approvals, policies, etc.)
 * - Approval timeline nested mapping
 */
@Mapper(componentModel = "spring")
public interface ExpenseMapper {

    Expense toEntity(ExpenseDto dto);

    ExpenseDto toDto(Expense entity);

    List<ExpenseDto> toDtoList(List<Expense> entities);

    @Mapping(target = "status", ignore = true) // Status changes via service methods
    @Mapping(target = "approvalChainId", ignore = true) // Set by service
    void updateEntityFromDto(UpdateExpenseDto dto, @MappingTarget Expense entity);

    // ── Approval mapping ──────────────────────────────────────────────
    default ExpenseDto.ApprovalDto toApprovalDto(ExpenseApproval approval) {
        if (approval == null) {
            return null;
        }
        return ExpenseDto.ApprovalDto.builder()
            .id(approval.getId())
            .level(approval.getApprovalLevel())
            .approverId(approval.getApproverId())
            .status(approval.getStatus() != null ? approval.getStatus().toString() : null)
            .actionDate(approval.getActionDate())
            .comment(approval.getComment())
            .isCurrentLevel(approval.getIsCurrentLevel())
            .build();
    }

    default List<ExpenseDto.ApprovalDto> toApprovalDtoList(List<ExpenseApproval> approvals) {
        if (approvals == null) {
            return null;
        }
        return approvals.stream()
            .map(this::toApprovalDto)
            .toList();
    }
}