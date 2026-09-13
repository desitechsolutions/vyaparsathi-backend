package com.desitech.vyaparsathi.expense.controller;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.expense.entity.ExpenseApproval;
import com.desitech.vyaparsathi.expense.service.ExpenseApprovalService;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/expenses/approvals")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
@RequiredArgsConstructor
@Tag(name = "Expense Approvals", description = "Multi-level approval workflow management")
public class ExpenseApprovalController {

    private final ExpenseApprovalService approvalService;

    @GetMapping("/pending")
    @Operation(summary = "Get pending approvals", description = "List expenses pending approval for current user")
    public ResponseEntity<Page<ExpenseApproval>> getPendingApprovals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication auth) {

        String userId = auth != null ? auth.getName() : "system";
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("id").descending());
        Page<ExpenseApproval> approvals = approvalService.getPendingApprovalsForUser(userId, pageable);

        return ResponseEntity.ok(approvals);
    }

    @GetMapping("/pending/count")
    @Operation(summary = "Get pending approval count", description = "Count of expenses awaiting approval for current user")
    public ResponseEntity<Long> getPendingCount(Authentication auth) {
        String userId = auth != null ? auth.getName() : "system";
        long count = approvalService.getPendingCount(userId);
        return ResponseEntity.ok(count);
    }

    @PostMapping("/{expenseId}/approve")
    @Operation(summary = "Approve expense", description = "Approve an expense at current approval level and move to next")
    public ResponseEntity<Void> approveExpense(
            @PathVariable Long expenseId,
            @RequestBody ApproveRequest request,
            Authentication auth) {

        String userId = auth != null ? auth.getName() : "system";
        approvalService.approveExpense(expenseId, userId, request.comment);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{expenseId}/reject")
    @Operation(summary = "Reject expense", description = "Reject an expense at current approval level")
    public ResponseEntity<Void> rejectExpense(
            @PathVariable Long expenseId,
            @RequestBody RejectRequest request,
            Authentication auth) {

        String userId = auth != null ? auth.getName() : "system";
        approvalService.rejectExpense(expenseId, userId, request.reason);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{expenseId}/escalate")
    @Operation(summary = "Escalate expense", description = "Escalate to next approval level (skip current)")
    public ResponseEntity<Void> escalateExpense(
            @PathVariable Long expenseId,
            @RequestBody EscalateRequest request) {

        approvalService.escalateExpense(expenseId, request.reason);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{expenseId}/history")
    @Operation(summary = "Get approval history", description = "Get full approval workflow timeline for an expense")
    public ResponseEntity<List<ExpenseApproval>> getApprovalHistory(@PathVariable Long expenseId) {
        List<ExpenseApproval> timeline = approvalService.getApprovalTimeline(expenseId);
        return ResponseEntity.ok(timeline);
    }

    // ── DTOs ──────────────────────────────────────────────────────────

    public record ApproveRequest(String comment) {}
    public record RejectRequest(String reason) {}
    public record EscalateRequest(String reason) {}
}
