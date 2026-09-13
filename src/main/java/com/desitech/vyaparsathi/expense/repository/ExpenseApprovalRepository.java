package com.desitech.vyaparsathi.expense.repository;

import com.desitech.vyaparsathi.expense.entity.ExpenseApproval;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ExpenseApproval (workflow tracking)
 */
@Repository
public interface ExpenseApprovalRepository extends JpaRepository<ExpenseApproval, Long> {

    /**
     * Get all approvals for an expense (ordered by level)
     */
    List<ExpenseApproval> findByExpenseIdOrderByApprovalLevel(Long expenseId);

    /**
     * Get current level approval (which one is waiting?)
     */
    Optional<ExpenseApproval> findByExpenseIdAndIsCurrentLevelTrue(Long expenseId);

    /**
     * Get pending approvals for a specific approver (inbox)
     */
    @Query("SELECT ea FROM ExpenseApproval ea " +
           "WHERE ea.approverId = :approverId " +
           "AND ea.status = 'PENDING' " +
           "AND ea.isCurrentLevel = true " +
           "ORDER BY ea.createdAt ASC")
    Page<ExpenseApproval> findPendingApprovalsForApprover(
        @Param("approverId") String approverId,
        Pageable pageable
    );

    /**
     * Count pending approvals for a user
     */
    long countByApproverIdAndStatusAndIsCurrentLevelTrue(String approverId, ExpenseApproval.ApprovalStatus status);

    /**
     * Get specific level approval for an expense
     */
    Optional<ExpenseApproval> findByExpenseIdAndApprovalLevel(Long expenseId, Integer level);

    /**
     * Get all rejections for an expense
     */
    List<ExpenseApproval> findByExpenseIdAndStatus(Long expenseId, ExpenseApproval.ApprovalStatus status);

    /**
     * Check if expense is approved by all levels
     */
    @Query("SELECT COUNT(ea) FROM ExpenseApproval ea " +
           "WHERE ea.expenseId = :expenseId " +
           "AND ea.status != 'APPROVED'")
    long countNonApprovedLevels(@Param("expenseId") Long expenseId);
}
