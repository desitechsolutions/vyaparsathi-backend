package com.desitech.vyaparsathi.expense.service;

import com.desitech.vyaparsathi.expense.entity.Expense;
import com.desitech.vyaparsathi.expense.entity.ExpenseApproval;
import com.desitech.vyaparsathi.expense.repository.ExpenseApprovalRepository;
import com.desitech.vyaparsathi.expense.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Expense Approval Service
 *
 * Handles:
 * 1. Approval chain initialization
 * 2. Multi-level approval workflow
 * 3. Approval inbox management
 * 4. Escalation handling
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseApprovalService {

    private final ExpenseApprovalRepository approvalRepository;
    private final ExpenseRepository expenseRepository;

    // ── Approval Workflow ────────────────────────────────────────────

    /**
     * Initialize approval chain for an expense
     * Creates approval records for each level
     */
    @Transactional
    public void initializeApprovalChain(Expense expense, List<String> approverIds) {
        // Clear any existing approvals
        List<ExpenseApproval> existing = approvalRepository.findByExpenseIdOrderByApprovalLevel(expense.getId());
        approvalRepository.deleteAll(existing);

        // Create new approval records
        for (int i = 0; i < approverIds.size(); i++) {
            ExpenseApproval approval = ExpenseApproval.builder()
                .expenseId(expense.getId())
                .approverId(approverIds.get(i))
                .approvalLevel(i + 1)
                .status(ExpenseApproval.ApprovalStatus.PENDING)
                .isCurrentLevel(i == 0) // First approver is current
                .build();

            approvalRepository.save(approval);
        }

        log.info("[Approval] Initialized approval chain for expense {} with {} levels",
            expense.getId(), approverIds.size());
    }

    /**
     * Approve expense at current level.
     *
     * EXP-1 fix (a): pass the completed level number to moveToNextLevel directly
     * instead of re-querying isCurrentLevel (which was already set to false and saved
     * before the query runs, making the original implementation always return level 0
     * and loop back to level 1 forever).
     *
     * EXP-1 fix (b): when all levels are done, update Expense.status → APPROVED and
     * set approvedDate (previously the Expense was never updated after full approval).
     */
    @Transactional
    public void approveExpense(Long expenseId, String approverId, String comment) {
        ExpenseApproval current = approvalRepository.findByExpenseIdAndIsCurrentLevelTrue(expenseId)
            .orElseThrow(() -> new RuntimeException("No current approval level for expense: " + expenseId));

        if (!current.getApproverId().equals(approverId)) {
            throw new RuntimeException("Not authorized to approve: " + approverId);
        }

        int completedLevel = current.getApprovalLevel();
        current.setStatus(ExpenseApproval.ApprovalStatus.APPROVED);
        current.setActionDate(LocalDateTime.now());
        current.setComment(comment);
        current.setIsCurrentLevel(false);
        approvalRepository.save(current);

        // EXP-1 fix (a): pass completedLevel so we don't re-query the already-cleared row
        boolean moreRemain = moveToNextLevel(expenseId, completedLevel);

        // EXP-1 fix (b): if no more levels, mark the expense itself as APPROVED
        if (!moreRemain) {
            expenseRepository.findById(expenseId).ifPresent(e -> {
                e.setStatus(Expense.ExpenseStatus.APPROVED);
                e.setApprovedDate(LocalDateTime.now());
                expenseRepository.save(e);
                log.info("[Approval] Expense {} fully approved", expenseId);
            });
        }

        log.info("[Approval] Expense {} approved at level {} by {}",
            expenseId, completedLevel, approverId);
    }

    /**
     * Reject expense at current level.
     *
     * EXP-3 fix: update Expense.status → REJECTED after persisting the rejection
     * (previously only the ExpenseApproval row was marked REJECTED; the parent
     * Expense remained in SUBMITTED/PENDING_APPROVAL, making it invisible in the
     * REJECTED filter and leaving it stuck in the approval queue).
     */
    @Transactional
    public void rejectExpense(Long expenseId, String approverId, String reason) {
        ExpenseApproval current = approvalRepository.findByExpenseIdAndIsCurrentLevelTrue(expenseId)
            .orElseThrow(() -> new RuntimeException("No current approval level for expense: " + expenseId));

        if (!current.getApproverId().equals(approverId)) {
            throw new RuntimeException("Not authorized to approve: " + approverId);
        }

        current.setStatus(ExpenseApproval.ApprovalStatus.REJECTED);
        current.setActionDate(LocalDateTime.now());
        current.setRejectionReason(reason);
        current.setIsCurrentLevel(false);
        approvalRepository.save(current);

        // EXP-3 fix: propagate rejection status to the parent Expense
        expenseRepository.findById(expenseId).ifPresent(e -> {
            e.setStatus(Expense.ExpenseStatus.REJECTED);
            e.setRejectionReason(reason);
            expenseRepository.save(e);
        });

        log.info("[Approval] Expense {} rejected at level {} by {}",
            expenseId, current.getApprovalLevel(), approverId);
    }

    /**
     * Escalate to next level (skip current approver)
     */
    @Transactional
    public void escalateExpense(Long expenseId, String reason) {
        ExpenseApproval current = approvalRepository.findByExpenseIdAndIsCurrentLevelTrue(expenseId)
            .orElseThrow(() -> new RuntimeException("No current approval level for expense: " + expenseId));

        int completedLevel = current.getApprovalLevel();
        current.setStatus(ExpenseApproval.ApprovalStatus.ESCALATED);
        current.setActionDate(LocalDateTime.now());
        current.setComment(reason);
        current.setIsCurrentLevel(false);
        approvalRepository.save(current);

        moveToNextLevel(expenseId, completedLevel);

        log.info("[Approval] Expense {} escalated from level {}",
            expenseId, completedLevel);
    }

    /**
     * Move approval to next level.
     *
     * EXP-1 fix: accepts the completedLevel directly (passed by caller) instead of
     * re-querying isCurrentLevel which was already cleared before this method runs.
     * The original re-query always returned 0 (empty → orElse(0)), causing level 0+1=1
     * to be activated on every approval regardless of actual position in the chain.
     *
     * @param completedLevel the level that was just approved/escalated
     * @return true if a next level was found and activated, false if chain is complete
     */
    private boolean moveToNextLevel(Long expenseId, int completedLevel) {
        Optional<ExpenseApproval> nextLevel = approvalRepository.findByExpenseIdAndApprovalLevel(
            expenseId, completedLevel + 1);

        if (nextLevel.isPresent()) {
            ExpenseApproval next = nextLevel.get();
            next.setIsCurrentLevel(true);
            approvalRepository.save(next);
            return true;
        }

        // No more levels — approval chain complete
        return false;
    }

    // ── Approval Inbox ──────────────────────────────────────────────

    /**
     * Get pending approvals for a user (their inbox)
     */
    @Transactional(readOnly = true)
    public Page<ExpenseApproval> getPendingApprovalsForUser(String userId, Pageable pageable) {
        return approvalRepository.findPendingApprovalsForApprover(userId, pageable);
    }

    /**
     * Get count of pending approvals
     */
    @Transactional(readOnly = true)
    public long getPendingCount(String userId) {
        return approvalRepository.countByApproverIdAndStatusAndIsCurrentLevelTrue(
            userId,
            ExpenseApproval.ApprovalStatus.PENDING
        );
    }

    /**
     * Get approval timeline for an expense
     */
    @Transactional(readOnly = true)
    public List<ExpenseApproval> getApprovalTimeline(Long expenseId) {
        return approvalRepository.findByExpenseIdOrderByApprovalLevel(expenseId);
    }

    /**
     * Check if expense is fully approved
     */
    @Transactional(readOnly = true)
    public boolean isFullyApproved(Long expenseId) {
        long pendingLevels = approvalRepository.countNonApprovedLevels(expenseId);
        return pendingLevels == 0;
    }

    /**
     * Check if any level has rejected the expense
     */
    @Transactional(readOnly = true)
    public boolean hasBeenRejected(Long expenseId) {
        List<ExpenseApproval> approvals = approvalRepository.findByExpenseIdAndStatus(
            expenseId,
            ExpenseApproval.ApprovalStatus.REJECTED
        );
        return !approvals.isEmpty();
    }
}
