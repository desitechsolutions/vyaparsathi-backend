package com.desitech.vyaparsathi.expense.controller;

import com.desitech.vyaparsathi.expense.dto.ExpenseDto;
import com.desitech.vyaparsathi.expense.dto.UpdateExpenseDto;
import com.desitech.vyaparsathi.expense.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/expenses")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
@Tag(name = "Expense Management", description = "Operations for managing operational business expenses. Note: Inventory/stock purchases should be recorded through Purchase Orders, not as expenses.")
public class ExpenseController {

    @Autowired
    private ExpenseService service;

    @PostMapping
    @Operation(summary = "Create a new operational expense", 
               description = "Creates a new operational expense. Inventory/stock purchases will be rejected - use Purchase Orders instead. Valid types include: RENT, UTILITIES, SALARY, MARKETING, etc.")
    @ApiResponse(responseCode = "200", description = "Expense created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid expense type (inventory purchases not allowed) or validation error")
    public ResponseEntity<ExpenseDto> create(@Valid @RequestBody ExpenseDto dto) {
        return ResponseEntity.ok(service.create(dto));
    }

    @GetMapping
    @Operation(summary = "List operational expenses", description = "Retrieve paginated list of operational expenses for a shop")
    public ResponseEntity<Page<ExpenseDto>> list(Pageable pageable) {
        return ResponseEntity.ok(service.list(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get expense details", description = "Retrieve details of a specific operational expense")
    public ResponseEntity<ExpenseDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update operational expense", 
               description = "Updates an operational expense. Inventory/stock purchase types will be rejected.")
    @ApiResponse(responseCode = "200", description = "Expense updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid expense type (inventory purchases not allowed) or validation error")
    public ResponseEntity<ExpenseDto> update(@PathVariable Long id, @Valid @RequestBody UpdateExpenseDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete expense", description = "Soft delete an operational expense")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}