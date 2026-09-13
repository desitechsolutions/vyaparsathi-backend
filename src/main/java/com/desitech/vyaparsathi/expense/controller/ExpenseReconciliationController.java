package com.desitech.vyaparsathi.expense.controller;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.expense.dto.ExpenseDto;
import com.desitech.vyaparsathi.expense.service.ExpenseReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/expenses/reconciliation")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
@RequiredArgsConstructor
@Tag(name = "Expense Reconciliation", description = "Bank reconciliation and reimbursement tracking")
public class ExpenseReconciliationController {

    private final ExpenseReconciliationService reconciliationService;

    @GetMapping("/summary")
    @Operation(summary = "Get reconciliation summary", description = "Summary of expenses awaiting reconciliation")
    public ResponseEntity<ExpenseReconciliationService.ReconciliationSummary> getSummary(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {

        Long shopId = TenantContext.getCurrentShopId();
        ExpenseReconciliationService.ReconciliationSummary summary = reconciliationService.getReconciliationSummary(
                shopId, startDate, endDate);

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/unmatched")
    @Operation(summary = "Get unmatched expenses", description = "Approved expenses awaiting bank reconciliation")
    public ResponseEntity<Page<ExpenseDto>> getUnmatchedExpenses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Long shopId = TenantContext.getCurrentShopId();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("expenseDate").descending());
        Page<ExpenseDto> expenses = reconciliationService.getUnmatchedExpenses(shopId, pageable);

        return ResponseEntity.ok(expenses);
    }

    @GetMapping("/reimbursed")
    @Operation(summary = "Get reimbursed expenses", description = "Expenses matched to bank transactions")
    public ResponseEntity<Page<ExpenseDto>> getReimbursedExpenses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Long shopId = TenantContext.getCurrentShopId();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("reimbursementDate").descending());
        Page<ExpenseDto> expenses = reconciliationService.getReimbursedExpenses(shopId, pageable);

        return ResponseEntity.ok(expenses);
    }

    @PostMapping("/{expenseId}/mark-reimbursed")
    @Operation(summary = "Mark as reimbursed", description = "Link expense to bank transaction and mark as reimbursed")
    public ResponseEntity<Void> markAsReimbursed(
            @PathVariable Long expenseId,
            @RequestBody MarkReimbursedRequest request) {

        Long shopId = TenantContext.getCurrentShopId();
        reconciliationService.markAsReimbursed(shopId, expenseId, request.bankReference);

        return ResponseEntity.ok().build();
    }

    // ── DTOs ──────────────────────────────────────────────────────────

    public record MarkReimbursedRequest(String bankReference) {}
}
