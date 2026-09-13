package com.desitech.vyaparsathi.expense.controller;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.expense.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/expenses/analytics")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
@RequiredArgsConstructor
@Tag(name = "Expense Analytics", description = "Analytics and reporting for expenses")
public class ExpenseAnalyticsController {

    private final ExpenseService expenseService;

    @GetMapping("/dashboard")
    @Operation(summary = "Get dashboard metrics", description = "Summary KPIs for expense dashboard")
    public ResponseEntity<DashboardMetrics> getDashboardMetrics() {
        Long shopId = TenantContext.getCurrentShopId();

        long totalExpenses = expenseService.getTotalExpensesCount(shopId);
        long pendingApprovals = expenseService.getPendingApprovalCount(shopId);

        DashboardMetrics metrics = new DashboardMetrics(
                totalExpenses,
                pendingApprovals,
                "ACTIVE"
        );

        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/category/spending")
    @Operation(summary = "Get spending by category", description = "Spending by category in a date range, optionally filtered by category")
    public ResponseEntity<List<CategorySpendingMetrics>> getCategorySpending(
            @RequestParam(required = false) Long categoryId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {

        Long shopId = TenantContext.getCurrentShopId();

        if (categoryId != null) {
            BigDecimal amount = expenseService.getCategorySpending(shopId, categoryId, startDate, endDate);
            CategorySpendingMetrics metrics = new CategorySpendingMetrics(
                    categoryId,
                    amount.doubleValue(),
                    startDate,
                    endDate
            );
            return ResponseEntity.ok(List.of(metrics));
        }

        // Return spending for all categories
        List<Map<String, Object>> rawSpending = expenseService.getAllCategorySpending(shopId, startDate, endDate);
        List<CategorySpendingMetrics> allCategorySpending = rawSpending.stream()
                .map(m -> new CategorySpendingMetrics(
                        m.get("categoryId") != null ? ((Number) m.get("categoryId")).longValue() : null,
                        ((Number) m.get("amount")).doubleValue(),
                        (LocalDate) m.get("startDate"),
                        (LocalDate) m.get("endDate")
                ))
                .toList();
        return ResponseEntity.ok(allCategorySpending);
    }

    // ── DTOs ──────────────────────────────────────────────────────────

    public record DashboardMetrics(
            Long totalExpenses,
            Long pendingApprovals,
            String status
    ) {}

    public record CategorySpendingMetrics(
            Long categoryId,
            Double amount,
            LocalDate startDate,
            LocalDate endDate
    ) {}
}
