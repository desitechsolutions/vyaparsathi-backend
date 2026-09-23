package com.desitech.vyaparsathi.expense.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.expense.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Expense (enhanced for enterprise)
 *
 * Supports filtering by status, employee, category, date range, and approvals
 */
public interface ExpenseRepository extends BaseRepository<Expense, Long> {

    // ── Basic queries (legacy compatibility) ──────────────────────────
    @Query("SELECT e FROM Expense e WHERE e.isDeleted = false AND e.shop.id = :shopId")
    Page<Expense> findByShopIdAndNotDeleted(@Param("shopId") Long shopId, Pageable pageable);

    /** EXP-DASH fix: COUNT-only query — avoids the full table scan of Pageable.unpaged(). */
    @Query("SELECT COUNT(e) FROM Expense e WHERE e.shop.id = :shopId AND e.isDeleted = false")
    long countByShopIdAndNotDeleted(@Param("shopId") Long shopId);


    Optional<Expense> findByIdAndIsDeletedFalse(Long id);

    List<Expense> findByDateBetweenAndIsDeletedFalse(LocalDateTime start, LocalDateTime end);

    // ── Enterprise queries ───────────────────────────────────────────
    /**
     * Get expenses by shop and status
     */
    @Query("SELECT e FROM Expense e WHERE e.shop.id = :shopId AND e.status = :status AND e.isDeleted = false " +
           "ORDER BY e.expenseDate DESC")
    Page<Expense> findByShopIdAndStatus(
        @Param("shopId") Long shopId,
        @Param("status") Expense.ExpenseStatus status,
        Pageable pageable
    );

    /**
     * Get employee's expenses
     */
    @Query("SELECT e FROM Expense e WHERE e.shop.id = :shopId AND e.employeeId = :employeeId AND e.isDeleted = false " +
           "ORDER BY e.expenseDate DESC")
    Page<Expense> findByShopIdAndEmployeeId(
        @Param("shopId") Long shopId,
        @Param("employeeId") String employeeId,
        Pageable pageable
    );

    /**
     * Get expenses by category and status
     */
    @Query("SELECT e FROM Expense e WHERE e.shop.id = :shopId AND e.expenseCategoryId = :categoryId " +
           "AND e.status = :status AND e.isDeleted = false " +
           "ORDER BY e.expenseDate DESC")
    Page<Expense> findByShopIdAndCategoryAndStatus(
        @Param("shopId") Long shopId,
        @Param("categoryId") Long categoryId,
        @Param("status") Expense.ExpenseStatus status,
        Pageable pageable
    );

    /**
     * Get expenses with policy violations
     */
    @Query("SELECT e FROM Expense e WHERE e.shop.id = :shopId AND e.policyViolationsJson IS NOT NULL " +
           "AND e.isDeleted = false ORDER BY e.expenseDate DESC")
    Page<Expense> findPolicyViolations(@Param("shopId") Long shopId, Pageable pageable);

    /**
     * Get expenses pending escalation
     */
    @Query("SELECT e FROM Expense e WHERE e.shop.id = :shopId AND e.requiresEscalation = true " +
           "AND e.isDeleted = false ORDER BY e.expenseDate DESC")
    List<Expense> findEscalationPending(@Param("shopId") Long shopId);

    /**
     * Get recurring expense instances
     */
    @Query("SELECT e FROM Expense e WHERE e.recurringExpenseId = :recurringId AND e.isDeleted = false " +
           "ORDER BY e.expenseDate DESC")
    List<Expense> findRecurringInstances(@Param("recurringId") Long recurringId);

    /**
     * Date range query for reports
     */
    @Query("SELECT e FROM Expense e WHERE e.shop.id = :shopId " +
           "AND e.expenseDate BETWEEN :startDate AND :endDate " +
           "AND e.status = 'APPROVED' AND e.isDeleted = false " +
           "ORDER BY e.expenseDate DESC")
    List<Expense> findApprovedExpensesByDateRange(
        @Param("shopId") Long shopId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Get top vendors for a shop
     */
    @Query(value = "SELECT e.vendor_name, COUNT(e.id) as count, SUM(e.amount) as total " +
           "FROM expense e WHERE e.shop_id = :shopId AND e.status = 'APPROVED' AND e.is_deleted = false " +
           "GROUP BY e.vendor_name ORDER BY total DESC LIMIT 10",
           nativeQuery = true)
    List<Object[]> findTopVendors(@Param("shopId") Long shopId);

    /**
     * Sum spending by category for a period
     */
    @Query("SELECT SUM(e.amount) FROM Expense e " +
           "WHERE e.shop.id = :shopId AND e.expenseCategoryId = :categoryId " +
           "AND e.expenseDate BETWEEN :startDate AND :endDate " +
           "AND e.status = 'APPROVED' AND e.isDeleted = false")
    Optional<java.math.BigDecimal> sumByCategoryAndPeriod(
        @Param("shopId") Long shopId,
        @Param("categoryId") Long categoryId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Check for recurring instance on specific date
     */
    @Query("SELECT e FROM Expense e WHERE e.recurringExpenseId = :recurringId " +
           "AND e.expenseDate = :date AND e.isDeleted = false")
    Optional<Expense> findRecurringInstanceOnDate(
        @Param("recurringId") Long recurringId,
        @Param("date") LocalDate date
    );

    /**
     * Count pending approvals for a shop
     */
    @Query("SELECT COUNT(e) FROM Expense e WHERE e.shop.id = :shopId AND e.status = 'PENDING_APPROVAL' " +
           "AND e.isDeleted = false")
    long countPendingApprovals(@Param("shopId") Long shopId);

    /**
     * Find expenses by date range (legacy compatibility)
     */
    @Query("SELECT e FROM Expense e WHERE e.date BETWEEN :start AND :end AND e.isDeleted = false " +
           "ORDER BY e.date DESC")
    List<Expense> findByDateBetweenAndDeletedFalse(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    /**
     * EXP-7 fix: fetch expenses by shop + date range in SQL (replaces the unbounded
     * Pageable.unpaged() query + in-memory date filtering in getAllCategorySpending).
     * Only APPROVED expenses count toward spending analytics.
     */
    @Query("SELECT e FROM Expense e WHERE e.shop.id = :shopId " +
           "AND e.expenseDate BETWEEN :startDate AND :endDate " +
           "AND e.isDeleted = false " +
           "ORDER BY e.expenseDate DESC")
    List<Expense> findByShopIdAndExpenseDateRange(
        @Param("shopId") Long shopId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * EXP-12 fix: count expenses by category in a single query (replaces the
     * N+1 pattern in ExpenseCategoryService.deleteCategory that ran one paginated
     * query per status enum value — 7 separate DB round-trips per delete call).
     */
    @Query("SELECT COUNT(e) FROM Expense e WHERE e.shop.id = :shopId " +
           "AND e.expenseCategoryId = :categoryId AND e.isDeleted = false")
    long countExpensesByShopAndCategory(
        @Param("shopId") Long shopId,
        @Param("categoryId") Long categoryId
    );
}