package com.desitech.vyaparsathi.purchasereturn.controller;

import com.desitech.vyaparsathi.purchasereturn.dto.CreatePurchaseReturnDto;
import com.desitech.vyaparsathi.purchasereturn.dto.PurchaseReturnDto;
import com.desitech.vyaparsathi.purchasereturn.service.PurchaseReturnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchase-returns")
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
@Tag(name = "Purchase Returns & Debit Notes", description = "Operations for vendor purchase returns and debit note management")
public class PurchaseReturnController {

    @Autowired
    private PurchaseReturnService purchaseReturnService;

    @PostMapping
    @Operation(summary = "Create a draft Purchase Return", description = "Creates a new Purchase Return record in DRAFT status")
    public ResponseEntity<PurchaseReturnDto> createPurchaseReturn(@Valid @RequestBody CreatePurchaseReturnDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseReturnService.createPurchaseReturn(dto));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve Purchase Return", description = "Approves a Purchase Return, deducts inventory stock, and issues a Debit Note")
    public ResponseEntity<PurchaseReturnDto> approvePurchaseReturn(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseReturnService.approvePurchaseReturn(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel draft Purchase Return", description = "Cancels a DRAFT Purchase Return")
    public ResponseEntity<PurchaseReturnDto> cancelPurchaseReturn(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseReturnService.cancelPurchaseReturn(id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Purchase Return by ID", description = "Retrieves details of a specific Purchase Return")
    public ResponseEntity<PurchaseReturnDto> getPurchaseReturnById(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseReturnService.getPurchaseReturnById(id));
    }

    @GetMapping
    @Operation(summary = "List Purchase Returns", description = "Lists purchase returns, optionally filtered by supplierId")
    public ResponseEntity<Page<PurchaseReturnDto>> getPurchaseReturns(
            @RequestParam(required = false) Long supplierId,
            Pageable pageable) {
        return ResponseEntity.ok(purchaseReturnService.getPurchaseReturns(supplierId, pageable));
    }
}
