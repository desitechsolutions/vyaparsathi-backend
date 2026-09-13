package com.desitech.vyaparsathi.expense.service;

import com.desitech.vyaparsathi.expense.dto.ExpenseDto;
import com.desitech.vyaparsathi.expense.entity.Expense;
import com.desitech.vyaparsathi.expense.mapper.ExpenseMapper;
import com.desitech.vyaparsathi.expense.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Expense Reconciliation Service
 *
 * Handles:
 * 1. Bank statement import & parsing
 * 2. Matching expenses to bank transactions
 * 3. Reconciliation status tracking
 * 4. Export reconciliation reports
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseReconciliationService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseMapper expenseMapper;

    // ── Reconciliation Queries ─────────────────────────────────────

    @Transactional(readOnly = true)
    public ReconciliationSummary getReconciliationSummary(
            Long shopId,
            LocalDate startDate,
            LocalDate endDate) {

        // Get approved expenses in range
        List<Expense> approvedExpenses = expenseRepository.findApprovedExpensesByDateRange(
                shopId, startDate, endDate);

        // Calculate totals
        BigDecimal totalApproved = approvedExpenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Count by status
        long reimbursedCount = approvedExpenses.stream()
                .filter(e -> Expense.ExpenseStatus.REIMBURSED.equals(e.getStatus()))
                .count();

        long pendingCount = approvedExpenses.stream()
                .filter(e -> Expense.ExpenseStatus.APPROVED.equals(e.getStatus()))
                .count();

        return ReconciliationSummary.builder()
                .totalExpenses(approvedExpenses.size())
                .totalAmount(totalApproved.doubleValue())
                .reimbursedCount(reimbursedCount)
                .pendingCount(pendingCount)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<ExpenseDto> getUnmatchedExpenses(Long shopId, Pageable pageable) {
        return expenseRepository.findByShopIdAndStatus(
                shopId,
                Expense.ExpenseStatus.APPROVED,
                pageable)
                .map(expenseMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<ExpenseDto> getReimbursedExpenses(Long shopId, Pageable pageable) {
        return expenseRepository.findByShopIdAndStatus(
                shopId,
                Expense.ExpenseStatus.REIMBURSED,
                pageable)
                .map(expenseMapper::toDto);
    }

    @Transactional
    public void markAsReimbursed(Long shopId, Long expenseId, String bankReference) {
        Expense expense = expenseRepository.findByIdAndIsDeletedFalse(expenseId)
                .filter(e -> e.getShop().getId().equals(shopId))
                .orElseThrow(() -> new RuntimeException("Expense not found: " + expenseId));

        if (!Expense.ExpenseStatus.APPROVED.equals(expense.getStatus())) {
            throw new RuntimeException("Only APPROVED expenses can be marked as reimbursed");
        }

        expense.setStatus(Expense.ExpenseStatus.REIMBURSED);
        expense.setReimbursementDate(java.time.LocalDateTime.now());
        expenseRepository.save(expense);

        log.info("[Reconciliation] Marked expense {} as reimbursed. Bank ref: {}", expenseId, bankReference);
    }

    // ── DTOs ──────────────────────────────────────────────────────────

    public record ReconciliationSummary(
            Integer totalExpenses,
            Double totalAmount,
            Long reimbursedCount,
            Long pendingCount,
            LocalDate startDate,
            LocalDate endDate
    ) {
        public static ReconciliationSummaryBuilder builder() {
            return new ReconciliationSummaryBuilder();
        }

        public static class ReconciliationSummaryBuilder {
            private Integer totalExpenses;
            private Double totalAmount;
            private Long reimbursedCount;
            private Long pendingCount;
            private LocalDate startDate;
            private LocalDate endDate;

            public ReconciliationSummaryBuilder totalExpenses(Integer totalExpenses) {
                this.totalExpenses = totalExpenses;
                return this;
            }

            public ReconciliationSummaryBuilder totalAmount(Double totalAmount) {
                this.totalAmount = totalAmount;
                return this;
            }

            public ReconciliationSummaryBuilder reimbursedCount(Long reimbursedCount) {
                this.reimbursedCount = reimbursedCount;
                return this;
            }

            public ReconciliationSummaryBuilder pendingCount(Long pendingCount) {
                this.pendingCount = pendingCount;
                return this;
            }

            public ReconciliationSummaryBuilder startDate(LocalDate startDate) {
                this.startDate = startDate;
                return this;
            }

            public ReconciliationSummaryBuilder endDate(LocalDate endDate) {
                this.endDate = endDate;
                return this;
            }

            public ReconciliationSummary build() {
                return new ReconciliationSummary(totalExpenses, totalAmount, reimbursedCount, pendingCount, startDate, endDate);
            }
        }
    }
}
