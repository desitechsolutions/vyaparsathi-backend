package com.desitech.vyaparsathi.einvoice.controller;

import com.desitech.vyaparsathi.common.payload.ApiResponse;
import com.desitech.vyaparsathi.einvoice.dto.EInvoiceResponseDto;
import com.desitech.vyaparsathi.einvoice.dto.EWayBillRequestDto;
import com.desitech.vyaparsathi.einvoice.dto.EWayBillResponseDto;
import com.desitech.vyaparsathi.einvoice.service.EInvoiceService;
import com.desitech.vyaparsathi.einvoice.service.EWayBillService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * E-Invoice & E-Way Bill REST endpoints.
 * Primary paths: /api/v1/einvoice/* and /api/v1/ewaybill/*
 * Alias paths:   /api/invoices/{id}/einvoice/* and /api/invoices/{id}/ewaybill/*
 *
 * Issue 5 Fix: Added RESTful path-style aliases to match frontend expectations.
 * Issue 5 Fix: E-Way Bill threshold is configurable via shop.ewaybill.threshold property,
 *              defaulting to ₹50,000 if not set.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class EInvoiceController {

    private final EInvoiceService einvoiceService;
    private final EWayBillService ewayBillService;

    /**
     * Issue 5 Decision: Configurable per-store E-Way Bill threshold.
     * Default: ₹50,000 (statutory minimum for mandatory E-Way Bill in India).
     * Override via application.properties: shop.ewaybill.threshold=50000
     */
    @Value("${shop.ewaybill.threshold:50000}")
    private double ewayBillThreshold;

    public EInvoiceController(EInvoiceService einvoiceService, EWayBillService ewayBillService) {
        this.einvoiceService = einvoiceService;
        this.ewayBillService = ewayBillService;
    }

    // ─── E-Invoice Endpoints ─────────────────────────────────────────────────

    /** Primary path: POST /api/v1/einvoice/generate/{saleId} */
    @PostMapping("/einvoice/generate/{saleId}")
    public ResponseEntity<ApiResponse<EInvoiceResponseDto>> generateIrn(@PathVariable Long saleId) {
        EInvoiceResponseDto result = einvoiceService.generateIrn(saleId);
        return ResponseEntity.ok(new ApiResponse<>("success", "E-Invoice IRN generated successfully", result));
    }

    /** Alias: POST /api/invoices/{id}/einvoice/generate */
    @PostMapping("/invoices/{id}/einvoice/generate")
    public ResponseEntity<ApiResponse<EInvoiceResponseDto>> generateIrnAlias(@PathVariable Long id) {
        return generateIrn(id);
    }

    /** Primary path: POST /api/v1/einvoice/cancel/{saleId} */
    @PostMapping("/einvoice/cancel/{saleId}")
    public ResponseEntity<ApiResponse<EInvoiceResponseDto>> cancelIrn(
            @PathVariable Long saleId,
            @RequestParam(defaultValue = "Order cancelled") String reason) {
        EInvoiceResponseDto result = einvoiceService.cancelIrn(saleId, reason);
        return ResponseEntity.ok(new ApiResponse<>("success", "E-Invoice IRN cancelled successfully", result));
    }

    /** Alias: POST /api/invoices/{id}/einvoice/cancel */
    @PostMapping("/invoices/{id}/einvoice/cancel")
    public ResponseEntity<ApiResponse<EInvoiceResponseDto>> cancelIrnAlias(
            @PathVariable Long id,
            @RequestParam(defaultValue = "Cancelled via portal") String reason) {
        return cancelIrn(id, reason);
    }

    // ─── E-Way Bill Endpoints ─────────────────────────────────────────────────

    /** Primary path: POST /api/v1/ewaybill/generate */
    @PostMapping("/ewaybill/generate")
    public ResponseEntity<ApiResponse<EWayBillResponseDto>> generateEWayBill(
            @RequestBody @Valid EWayBillRequestDto request) {
        EWayBillResponseDto result = ewayBillService.generateEWayBill(request);
        return ResponseEntity.ok(new ApiResponse<>("success", "E-Way Bill generated successfully", result));
    }

    /** Alias: POST /api/invoices/{id}/ewaybill/generate */
    @PostMapping("/invoices/{id}/ewaybill/generate")
    public ResponseEntity<ApiResponse<EWayBillResponseDto>> generateEWayBillAlias(
            @PathVariable Long id,
            @RequestBody EWayBillRequestDto request) {
        if (request == null) {
            request = new EWayBillRequestDto();
        }
        request.setSaleId(id);
        EWayBillResponseDto result = ewayBillService.generateEWayBill(request);
        return ResponseEntity.ok(new ApiResponse<>("success", "E-Way Bill generated successfully", result));
    }

    /**
     * Returns the configured E-Way Bill threshold for this store.
     * Frontend uses this to conditionally show the Generate E-Way Bill button.
     */
    @GetMapping("/ewaybill/threshold")
    public ResponseEntity<ApiResponse<Double>> getEwayBillThreshold() {
        return ResponseEntity.ok(new ApiResponse<>("success", "E-Way Bill threshold", ewayBillThreshold));
    }
}
