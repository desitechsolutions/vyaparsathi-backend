package com.desitech.vyaparsathi.supplier.controller;

import com.desitech.vyaparsathi.common.payload.ApiResponse;
import com.desitech.vyaparsathi.supplier.dto.SupplierLedgerDto;
import com.desitech.vyaparsathi.supplier.service.SupplierLedgerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierLedgerController {

    private final SupplierLedgerService ledgerService;

    public SupplierLedgerController(SupplierLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/{supplierId}/ledger")
    public ResponseEntity<ApiResponse<Page<SupplierLedgerDto>>> getSupplierLedger(
            @PathVariable Long supplierId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<SupplierLedgerDto> page = ledgerService.getSupplierLedger(supplierId, pageable);
        return ResponseEntity.ok(new ApiResponse<>("success", "Supplier ledger fetched successfully", page));
    }
}
