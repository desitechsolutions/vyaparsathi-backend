package com.desitech.vyaparsathi.expense.service;

import com.desitech.vyaparsathi.expense.entity.Expense;
import com.desitech.vyaparsathi.expense.entity.ExpensePolicy;
import com.desitech.vyaparsathi.expense.repository.ExpensePolicyRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Expense Policy Service
 *
 * Handles:
 * 1. Policy CRUD
 * 2. Policy validation (apply to expenses)
 * 3. Violation detection & enforcement
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpensePolicyService {

    private final ExpensePolicyRepository policyRepository;
    private final ObjectMapper objectMapper;

    // ── Policy CRUD ──────────────────────────────────────────────────

    @Transactional
    public ExpensePolicy createPolicy(Long shopId, ExpensePolicy policy) {
        com.desitech.vyaparsathi.shop.entity.Shop shop = new com.desitech.vyaparsathi.shop.entity.Shop();
        shop.setId(shopId);
        policy.setShop(shop);
        if (policy.getIsActive() == null) policy.setIsActive(true);
        return policyRepository.save(policy);
    }

    @Transactional
    public ExpensePolicy updatePolicy(Long shopId, Long policyId, ExpensePolicy updates) {
        ExpensePolicy policy = policyRepository.findById(policyId)
            .filter(p -> p.getShop().getId().equals(shopId))
            .orElseThrow(() -> new RuntimeException("Policy not found: " + policyId));

        if (updates.getName() != null) policy.setName(updates.getName());
        if (updates.getDescription() != null) policy.setDescription(updates.getDescription());
        if (updates.getLimitValue() != null) policy.setLimitValue(updates.getLimitValue());
        if (updates.getEnforcementAction() != null) policy.setEnforcementAction(updates.getEnforcementAction());

        return policyRepository.save(policy);
    }

    @Transactional
    public void deletePolicy(Long shopId, Long policyId) {
        ExpensePolicy policy = policyRepository.findById(policyId)
            .filter(p -> p.getShop().getId().equals(shopId))
            .orElseThrow(() -> new RuntimeException("Policy not found: " + policyId));

        policyRepository.delete(policy);
        log.info("[Policy] Deleted policy: {}", policyId);
    }

    @Transactional(readOnly = true)
    public List<ExpensePolicy> getPolicies(Long shopId) {
        return policyRepository.findByShopIdAndIsActiveTrueOrderByName(shopId);
    }

    // ── Policy Enforcement ───────────────────────────────────────────

    /**
     * Validate expense against all shop policies
     * Returns list of violations (empty if compliant)
     */
    public List<PolicyViolation> validateExpense(Long shopId, Expense expense) {
        List<PolicyViolation> violations = new ArrayList<>();

        List<ExpensePolicy> policies = policyRepository.findByShopIdAndIsActiveTrueOrderByName(shopId);

        for (ExpensePolicy policy : policies) {
            PolicyViolation violation = checkPolicyViolation(expense, policy);
            if (violation != null) {
                violations.add(violation);
            }
        }

        return violations;
    }

    /**
     * Check if a single expense violates a policy
     */
    private PolicyViolation checkPolicyViolation(Expense expense, ExpensePolicy policy) {
        switch (policy.getRuleType()) {
            case AMOUNT_LIMIT:
                if (expense.getAmount().compareTo(policy.getLimitValue()) > 0) {
                    return new PolicyViolation(
                        policy.getId(),
                        policy.getName(),
                        "Amount ₹" + expense.getAmount() + " exceeds limit ₹" + policy.getLimitValue(),
                        policy.getEnforcementAction()
                    );
                }
                break;

            case REQUIRES_RECEIPT:
                if (expense.getReceiptId() == null && expense.getReceiptPath() == null) {
                    return new PolicyViolation(
                        policy.getId(),
                        policy.getName(),
                        "Receipt is required for this category",
                        policy.getEnforcementAction()
                    );
                }
                break;

            case CATEGORY_RESTRICTION:
                // EXP-6 fix: check whether this expense's category is actually in the
                // restricted list. Previously every CATEGORY_RESTRICTION policy fired a
                // violation unconditionally — affectedCategoriesJson was never consulted.
                if (policy.getAffectedCategoriesJson() != null && expense.getExpenseCategoryId() != null) {
                    try {
                        List<Long> restricted = objectMapper.readValue(
                            policy.getAffectedCategoriesJson(),
                            new TypeReference<List<Long>>() {});
                        if (restricted.contains(expense.getExpenseCategoryId())) {
                            return new PolicyViolation(
                                policy.getId(),
                                policy.getName(),
                                "Category is restricted by policy: " + policy.getName(),
                                policy.getEnforcementAction()
                            );
                        }
                    } catch (Exception e) {
                        log.warn("[Policy] Could not parse affectedCategoriesJson for policy {}: {}",
                            policy.getId(), e.getMessage());
                    }
                }
                break;

            case FREQUENCY_LIMIT:
                // TODO: Check frequency violations (e.g., max 5 dinners/week)
                break;
        }

        return null;
    }

    /**
     * Apply policy enforcement based on violations
     * Returns true if expense should be auto-rejected
     */
    public boolean enforcePolicy(Expense expense, List<PolicyViolation> violations) {
        if (violations.isEmpty()) {
            return false; // No violations
        }

        for (PolicyViolation violation : violations) {
            switch (violation.enforcementAction) {
                case AUTO_REJECT:
                    expense.setStatus(Expense.ExpenseStatus.REJECTED);
                    expense.setRejectionReason("Policy violation: " + violation.reason);
                    return true;

                case ESCALATE:
                    expense.setRequiresEscalation(true);
                    expense.setEscalationReason("Policy violation: " + violation.reason);
                    break;

                case FLAG_FOR_REVIEW:
                    // Mark for manual review (don't auto-reject)
                    break;

                case WARN_ONLY:
                    // Just log, allow user override
                    break;
            }
        }

        return false;
    }

    // ── DTOs ─────────────────────────────────────────────────────────

    public static class PolicyViolation {
        public Long policyId;
        public String policyName;
        public String reason;
        public ExpensePolicy.EnforcementAction enforcementAction;

        public PolicyViolation(Long policyId, String policyName, String reason, ExpensePolicy.EnforcementAction action) {
            this.policyId = policyId;
            this.policyName = policyName;
            this.reason = reason;
            this.enforcementAction = action;
        }
    }
}
