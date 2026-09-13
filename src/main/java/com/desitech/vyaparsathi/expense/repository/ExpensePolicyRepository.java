package com.desitech.vyaparsathi.expense.repository;

import com.desitech.vyaparsathi.expense.entity.ExpensePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for ExpensePolicy (policy rules & enforcement)
 */
@Repository
public interface ExpensePolicyRepository extends JpaRepository<ExpensePolicy, Long> {

    /**
     * Get all active policies for a shop
     */
    @Query("SELECT ep FROM ExpensePolicy ep WHERE ep.shop.id = :shopId AND ep.isActive = true ORDER BY ep.name")
    List<ExpensePolicy> findByShopIdAndIsActiveTrueOrderByName(@Param("shopId") Long shopId);

    /**
     * Find policies that affect a specific category
     */
    @Query(value = "SELECT * FROM expense_policy ep " +
           "WHERE ep.shop_id = :shopId " +
           "AND ep.is_active = true " +
           "AND (JSON_CONTAINS(ep.affected_categories, CAST(:categoryId AS CHAR)) = 1 " +
           "     OR JSON_CONTAINS(ep.affected_categories, JSON_QUOTE(CAST(:categoryId AS CHAR))) = 1)",
           nativeQuery = true)
    List<ExpensePolicy> findPoliciesAffectingCategory(
        @Param("shopId") Long shopId,
        @Param("categoryId") Long categoryId
    );

    /**
     * Find policies by rule type
     */
    @Query("SELECT ep FROM ExpensePolicy ep WHERE ep.shop.id = :shopId AND ep.ruleType = :ruleType AND ep.isActive = true")
    List<ExpensePolicy> findByShopIdAndRuleTypeAndIsActiveTrue(
        @Param("shopId") Long shopId,
        @Param("ruleType") ExpensePolicy.RuleType ruleType
    );

    /**
     * Find policies by enforcement action
     */
    @Query("SELECT ep FROM ExpensePolicy ep WHERE ep.shop.id = :shopId AND ep.enforcementAction = :action AND ep.isActive = true ORDER BY ep.name")
    List<ExpensePolicy> findByShopIdAndEnforcementActionAndIsActiveTrueOrderByName(
        @Param("shopId") Long shopId,
        @Param("action") ExpensePolicy.EnforcementAction enforcementAction
    );

    /**
     * Check if a policy exists for a shop
     */
    @Query("SELECT CASE WHEN COUNT(ep) > 0 THEN true ELSE false END FROM ExpensePolicy ep WHERE ep.id = :policyId AND ep.shop.id = :shopId")
    boolean existsByIdAndShopId(@Param("policyId") Long policyId, @Param("shopId") Long shopId);

    /**
     * Count active policies for a shop
     */
    @Query("SELECT COUNT(ep) FROM ExpensePolicy ep WHERE ep.shop.id = :shopId AND ep.isActive = true")
    long countByShopIdAndIsActiveTrue(@Param("shopId") Long shopId);
}
