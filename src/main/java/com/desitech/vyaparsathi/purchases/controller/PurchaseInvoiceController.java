package com.desitech.vyaparsathi.purchases.controller;

import com.desitech.vyaparsathi.common.payload.ApiResponse;
import com.desitech.vyaparsathi.purchases.dto.PurchaseCreateDto;
import com.desitech.vyaparsathi.purchases.dto.PurchaseInvoiceDto;
import com.desitech.vyaparsathi.purchases.service.PurchaseInvoiceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/purchases")
public class PurchaseInvoiceController {

    private final PurchaseInvoiceService purchaseService;

    public PurchaseInvoiceController(PurchaseInvoiceService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseInvoiceDto>> createPurchaseInvoice(
            @Valid @RequestBody PurchaseCreateDto createDto) {
        PurchaseInvoiceDto created = purchaseService.createPurchaseInvoice(createDto);
        return ResponseEntity.ok(new ApiResponse<>("success", "Purchase invoice recorded successfully", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PurchaseInvoiceDto>>> getPurchaseInvoices(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<PurchaseInvoiceDto> page = purchaseService.getAllPurchaseInvoices(pageable);
        return ResponseEntity.ok(new ApiResponse<>("success", "Purchase invoices fetched", page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseInvoiceDto>> getPurchaseInvoiceById(@PathVariable Long id) {
        PurchaseInvoiceDto dto = purchaseService.getPurchaseInvoiceById(id);
        return ResponseEntity.ok(new ApiResponse<>("success", "Purchase invoice details fetched", dto));
    }
}
