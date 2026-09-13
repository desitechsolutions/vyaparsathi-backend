package com.desitech.vyaparsathi.expense.controller;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.expense.entity.ExpenseCategory;
import com.desitech.vyaparsathi.expense.service.ExpenseCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/expense-categories")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
@RequiredArgsConstructor
@Tag(name = "Expense Categories", description = "Hierarchical expense category management")
public class ExpenseCategoryController {

    private final ExpenseCategoryService categoryService;

    @GetMapping
    @Operation(summary = "List root categories", description = "Get all root (top-level) expense categories")
    public ResponseEntity<List<ExpenseCategory>> listRootCategories() {
        Long shopId = TenantContext.getCurrentShopId();
        List<ExpenseCategory> categories = categoryService.getRootCategories(shopId);
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/{id}/subcategories")
    @Operation(summary = "Get subcategories", description = "Get all subcategories for a parent category")
    public ResponseEntity<List<ExpenseCategory>> getSubcategories(@PathVariable Long id) {
        Long shopId = TenantContext.getCurrentShopId();
        List<ExpenseCategory> subcategories = categoryService.getSubcategories(shopId, id);
        return ResponseEntity.ok(subcategories);
    }

    @GetMapping("/{id}/hierarchy")
    @Operation(summary = "Get full hierarchy", description = "Get category and all its descendants")
    public ResponseEntity<ExpenseCategory> getCategoryHierarchy(@PathVariable Long id) {
        Long shopId = TenantContext.getCurrentShopId();
        ExpenseCategory hierarchy = categoryService.getCategoryHierarchy(shopId, id);
        return ResponseEntity.ok(hierarchy);
    }

    @PostMapping
    @Operation(summary = "Create category", description = "Create a new expense category (root or subcategory)")
    public ResponseEntity<ExpenseCategory> createCategory(@Valid @RequestBody ExpenseCategory category) {
        Long shopId = TenantContext.getCurrentShopId();
        ExpenseCategory created = categoryService.createCategory(shopId, category);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update category", description = "Update category details")
    public ResponseEntity<ExpenseCategory> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody ExpenseCategory updates) {

        Long shopId = TenantContext.getCurrentShopId();
        ExpenseCategory updated = categoryService.updateCategory(shopId, id, updates);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete category", description = "Soft delete a category (if no expenses exist)")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        Long shopId = TenantContext.getCurrentShopId();
        categoryService.deleteCategory(shopId, id);
        return ResponseEntity.noContent().build();
    }
}
