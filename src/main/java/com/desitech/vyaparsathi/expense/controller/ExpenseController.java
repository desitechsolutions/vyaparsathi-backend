package com.desitech.vyaparsathi.expense.controller;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.expense.dto.ExpenseDto;
import com.desitech.vyaparsathi.expense.dto.UpdateExpenseDto;
import com.desitech.vyaparsathi.expense.entity.Expense;
import com.desitech.vyaparsathi.expense.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/expenses")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
@Tag(name = "Expense Management", description = "Enterprise expense management with policy enforcement, approval workflows, and analytics")
public class ExpenseController {

    @Autowired
    private ExpenseService service;

    // ── CRUD ──────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Create a new operational expense",
               description = "Creates expense with policy validation and auto-approval/escalation")
    @ApiResponse(responseCode = "201", description = "Expense created successfully")
    @ApiResponse(responseCode = "400", description = "Validation error or policy violation")
    public ResponseEntity<ExpenseDto> create(@Valid @RequestBody ExpenseDto dto, Authentication auth) {
        Long shopId = TenantContext.getCurrentShopId();
        String userId = auth != null ? auth.getName() : "system";
        ExpenseDto created = service.createWithPolicyValidation(shopId, userId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "List operational expenses", description = "Retrieve paginated list of expenses with optional status filter")
    public ResponseEntity<Page<ExpenseDto>> list(
            Pageable pageable,
            @RequestParam(required = false) Expense.ExpenseStatus status) {

        if (status != null) {
            Long shopId = TenantContext.getCurrentShopId();
            return ResponseEntity.ok(service.getExpensesByStatus(shopId, status, pageable));
        }
        return ResponseEntity.ok(service.list(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get expense details", description = "Retrieve details of a specific operational expense")
    public ResponseEntity<ExpenseDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update operational expense",
               description = "Updates expense (only if DRAFT status)")
    @ApiResponse(responseCode = "200", description = "Expense updated successfully")
    @ApiResponse(responseCode = "400", description = "Can only edit DRAFT expenses")
    public ResponseEntity<ExpenseDto> update(@PathVariable Long id, @Valid @RequestBody UpdateExpenseDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete expense", description = "Soft delete an operational expense (only if DRAFT)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Approval Workflow ─────────────────────────────────────────────

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit expense for approval",
               description = "Move expense from DRAFT to SUBMITTED and initialize approval chain")
    public ResponseEntity<Void> submitForApproval(
            @PathVariable Long id,
            @RequestBody SubmitForApprovalRequest request) {

        Long shopId = TenantContext.getCurrentShopId();
        service.submitForApproval(shopId, id, request.approverIds);
        return ResponseEntity.ok().build();
    }

    // ── Analytics ──────────────────────────────────────────────────────

    @GetMapping("/analytics/pending-count")
    @Operation(summary = "Get pending approval count", description = "Count of expenses awaiting approval for this shop")
    public ResponseEntity<Long> getPendingApprovalCount() {
        Long shopId = TenantContext.getCurrentShopId();
        long count = service.getPendingApprovalCount(shopId);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/analytics/category-spending")
    @Operation(summary = "Get category spending", description = "Total spending for a category in date range")
    public ResponseEntity<CategorySpendingResponse> getCategorySpending(
            @RequestParam Long categoryId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {

        Long shopId = TenantContext.getCurrentShopId();
        BigDecimal spending = service.getCategorySpending(shopId, categoryId, startDate, endDate);
        return ResponseEntity.ok(new CategorySpendingResponse(spending.doubleValue(), categoryId));
    }

    @GetMapping("/employee/{employeeId}")
    @Operation(summary = "Get employee expenses", description = "List all expenses for a specific employee")
    public ResponseEntity<Page<ExpenseDto>> getEmployeeExpenses(
            @PathVariable String employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Long shopId = TenantContext.getCurrentShopId();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("expenseDate").descending());
        Page<ExpenseDto> expenses = service.getEmployeeExpenses(shopId, employeeId, pageable);
        return ResponseEntity.ok(expenses);
    }

    // ── DTOs ──────────────────────────────────────────────────────────

    public record SubmitForApprovalRequest(List<String> approverIds) {}
    public record CategorySpendingResponse(Double amount, Long categoryId) {}
}