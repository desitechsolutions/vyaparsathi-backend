package com.desitech.vyaparsathi.supplier.controller;

import com.desitech.vyaparsathi.payment.dto.PaymentDto;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderPaymentSummaryDto;
import com.desitech.vyaparsathi.supplier.dto.SupplierBulkPaymentRequest;
import com.desitech.vyaparsathi.supplier.dto.SupplierPayableBillDto;
import com.desitech.vyaparsathi.supplier.dto.SupplierPaymentDto;
import com.desitech.vyaparsathi.supplier.dto.SupplierStatementDto;
import com.desitech.vyaparsathi.supplier.service.SupplierPaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for supplier payment operations.
 * All endpoints are exposed under {@code /api/supplier-payments}.
 */
@RestController
@RequestMapping("/api/supplier-payments")
public class SupplierPaymentController {

    private static final Logger logger = LoggerFactory.getLogger(SupplierPaymentController.class);

    @Autowired
    private SupplierPaymentService supplierPaymentService;

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PostMapping
    public ResponseEntity<PaymentDto> recordPayment(@Valid @RequestBody SupplierPaymentDto dto) {
        logger.info("Recording supplier payment: supplierId={}, poId={}, amount={}",
                dto.getSupplierId(), dto.getPurchaseOrderId(), dto.getAmount());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supplierPaymentService.recordPayment(dto));
    }

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PostMapping("/bulk")
    public ResponseEntity<List<PaymentDto>> recordBulkPayment(
            @Valid @RequestBody SupplierBulkPaymentRequest request) {
        logger.info("Recording bulk supplier payment: supplierId={}, poCount={}, totalAmount={}",
                request.getSupplierId(),
                request.getSelectedPoIds() != null ? request.getSelectedPoIds().size() : 0,
                request.getTotalAmount());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supplierPaymentService.recordBulkPayment(request));
    }

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @GetMapping
    public ResponseEntity<Page<PaymentDto>> getPayments(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long purchaseOrderId,
            Pageable pageable) {
        if (purchaseOrderId != null) {
            return ResponseEntity.ok(
                    supplierPaymentService.getPaymentsByPurchaseOrder(purchaseOrderId, pageable));
        } else if (supplierId != null) {
            return ResponseEntity.ok(
                    supplierPaymentService.getPaymentsBySupplier(supplierId, pageable));
        }
        return ResponseEntity.badRequest().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @GetMapping("/summary")
    public ResponseEntity<PurchaseOrderPaymentSummaryDto> getPaymentSummary(
            @RequestParam Long purchaseOrderId) {
        return ResponseEntity.ok(supplierPaymentService.getPaymentSummary(purchaseOrderId));
    }

    /**
     * Issue 4 Fix: Per-PO payable breakdown with return deductions.
     * Returns: originalAmount, returnDeductions, cashPaid, netPayable per PO for frontend display.
     * GET /api/supplier-payments/payable-bills?supplierId=123
     */
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @GetMapping("/payable-bills")
    public ResponseEntity<List<SupplierPayableBillDto>> getPayableBills(
            @RequestParam Long supplierId) {
        return ResponseEntity.ok(supplierPaymentService.getPayableBills(supplierId));
    }

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @GetMapping("/statement")
    public ResponseEntity<SupplierStatementDto> getSupplierStatement(
            @RequestParam Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(supplierPaymentService.getSupplierStatement(supplierId, startDate, endDate));
    }
}
