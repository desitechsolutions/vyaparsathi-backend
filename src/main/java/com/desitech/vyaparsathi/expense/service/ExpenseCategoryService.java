package com.desitech.vyaparsathi.expense.service;

import com.desitech.vyaparsathi.expense.entity.ExpenseCategory;
import com.desitech.vyaparsathi.expense.repository.ExpenseCategoryRepository;
import com.desitech.vyaparsathi.expense.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseCategoryService {

    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;

    @Transactional(readOnly = true)
    public List<ExpenseCategory> getRootCategories(Long shopId) {
        return categoryRepository.findByShopIdAndParentIdIsNullAndIsActiveTrue(shopId);
    }

    @Transactional(readOnly = true)
    public List<ExpenseCategory> getSubcategories(Long shopId, Long parentId) {
        return categoryRepository.findByShopIdAndParentIdAndIsActiveTrue(shopId, parentId);
    }

    @Transactional(readOnly = true)
    public ExpenseCategory getCategoryHierarchy(Long shopId, Long categoryId) {
        return categoryRepository.findById(categoryId)
                .filter(c -> c.getShop().getId().equals(shopId))
                .orElseThrow(() -> new RuntimeException("Category not found: " + categoryId));
    }

    @Transactional
    public ExpenseCategory createCategory(Long shopId, ExpenseCategory category) {
        validateCategoryRequest(category);

        com.desitech.vyaparsathi.shop.entity.Shop shop = new com.desitech.vyaparsathi.shop.entity.Shop();
        shop.setId(shopId);
        category.setShop(shop);
        if (category.getIsActive() == null) category.setIsActive(true);

        // Validate parent exists if specified
        if (category.getParentId() != null) {
            categoryRepository.findById(category.getParentId())
                    .filter(p -> p.getShop().getId().equals(shopId) && p.getIsActive())
                    .orElseThrow(() -> new RuntimeException("Parent category not found or inactive: " + category.getParentId()));
        }

        ExpenseCategory saved = categoryRepository.save(category);
        log.info("[Category] Created category: {} (parentId={})", saved.getId(), saved.getParentId());
        return saved;
    }

    @Transactional
    public ExpenseCategory updateCategory(Long shopId, Long categoryId, ExpenseCategory updates) {
        ExpenseCategory category = categoryRepository.findById(categoryId)
                .filter(c -> c.getShop().getId().equals(shopId))
                .orElseThrow(() -> new RuntimeException("Category not found: " + categoryId));

        if (updates.getName() != null) category.setName(updates.getName());
        if (updates.getDescription() != null) category.setDescription(updates.getDescription());
        if (updates.getIconCode() != null) category.setIconCode(updates.getIconCode());
        if (updates.getColorCode() != null) category.setColorCode(updates.getColorCode());
        if (updates.getBudgetThreshold() != null) category.setBudgetThreshold(updates.getBudgetThreshold());
        if (updates.getRequiresReceipt() != null) category.setRequiresReceipt(updates.getRequiresReceipt());
        if (updates.getSortOrder() != null) category.setSortOrder(updates.getSortOrder());

        ExpenseCategory saved = categoryRepository.save(category);
        log.info("[Category] Updated category: {}", categoryId);
        return saved;
    }

    @Transactional
    public void deleteCategory(Long shopId, Long categoryId) {
        ExpenseCategory category = categoryRepository.findById(categoryId)
                .filter(c -> c.getShop().getId().equals(shopId))
                .orElseThrow(() -> new RuntimeException("Category not found: " + categoryId));

        // Check if category has expenses (check DRAFT status as sample - could extend to all statuses)
        long expenseCount = 0;
        for (com.desitech.vyaparsathi.expense.entity.Expense.ExpenseStatus status : com.desitech.vyaparsathi.expense.entity.Expense.ExpenseStatus.values()) {
            expenseCount += expenseRepository.findByShopIdAndCategoryAndStatus(
                    shopId, categoryId, status, Pageable.unpaged()).getTotalElements();
        }

        if (expenseCount > 0) {
            throw new RuntimeException("Cannot delete category with existing expenses");
        }

        // Check if category has subcategories
        List<ExpenseCategory> subcategories = categoryRepository.findByShopIdAndParentIdAndIsActiveTrue(shopId, categoryId);
        if (!subcategories.isEmpty()) {
            throw new RuntimeException("Cannot delete category with subcategories");
        }

        category.setIsActive(false);
        categoryRepository.save(category);
        log.info("[Category] Deleted category: {}", categoryId);
    }

    private void validateCategoryRequest(ExpenseCategory category) {
        if (category.getName() == null || category.getName().isBlank()) {
            throw new IllegalArgumentException("Category name is required");
        }
    }
}
