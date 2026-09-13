package com.desitech.vyaparsathi.expense.repository;

import com.desitech.vyaparsathi.expense.entity.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ExpenseCategory
 *
 * Supports hierarchical category queries (root categories, subcategories)
 */
@Repository
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    /**
     * Find all root categories (parent_id = null) for a shop
     */
    @Query("SELECT ec FROM ExpenseCategory ec WHERE ec.shop.id = :shopId AND ec.parentId IS NULL AND ec.isActive = true")
    List<ExpenseCategory> findByShopIdAndParentIdIsNullAndIsActiveTrue(@Param("shopId") Long shopId);

    /**
     * Find subcategories of a parent category
     */
    @Query("SELECT ec FROM ExpenseCategory ec WHERE ec.shop.id = :shopId AND ec.parentId = :parentId AND ec.isActive = true")
    List<ExpenseCategory> findByShopIdAndParentIdAndIsActiveTrue(@Param("shopId") Long shopId, @Param("parentId") Long parentId);

    /**
     * Find category by name
     */
    @Query("SELECT ec FROM ExpenseCategory ec WHERE ec.shop.id = :shopId AND LOWER(ec.name) = LOWER(:name)")
    Optional<ExpenseCategory> findByShopIdAndNameIgnoreCase(@Param("shopId") Long shopId, @Param("name") String name);

    /**
     * Find category by ID and verify it belongs to shop
     */
    @Query("SELECT ec FROM ExpenseCategory ec WHERE ec.id = :categoryId AND ec.shop.id = :shopId")
    Optional<ExpenseCategory> findByIdAndShopId(@Param("categoryId") Long categoryId, @Param("shopId") Long shopId);

    /**
     * Get all active categories for a shop (flat list for dropdowns)
     */
    @Query("SELECT ec FROM ExpenseCategory ec WHERE ec.shop.id = :shopId AND ec.isActive = true ORDER BY ec.sortOrder, ec.name")
    List<ExpenseCategory> findByShopIdAndIsActiveTrueOrderBySortOrder(@Param("shopId") Long shopId);

    /**
     * Check if category exists and is active
     */
    @Query("SELECT CASE WHEN COUNT(ec) > 0 THEN true ELSE false END FROM ExpenseCategory ec WHERE ec.id = :categoryId AND ec.shop.id = :shopId AND ec.isActive = true")
    boolean existsByIdAndShopIdAndIsActiveTrue(@Param("categoryId") Long categoryId, @Param("shopId") Long shopId);

    /**
     * Get full hierarchy starting from a root category
     */
    @Query("SELECT ec FROM ExpenseCategory ec " +
           "WHERE ec.shop.id = :shopId AND (ec.id = :categoryId OR ec.parentId = :categoryId) " +
           "AND ec.isActive = true " +
           "ORDER BY ec.sortOrder, ec.name")
    List<ExpenseCategory> findCategoryHierarchy(@Param("shopId") Long shopId, @Param("categoryId") Long categoryId);

    /**
     * Count active categories per shop
     */
    @Query("SELECT COUNT(ec) FROM ExpenseCategory ec WHERE ec.shop.id = :shopId AND ec.isActive = true")
    long countByShopIdAndIsActiveTrue(@Param("shopId") Long shopId);
}
