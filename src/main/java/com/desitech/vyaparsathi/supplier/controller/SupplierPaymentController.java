package com.desitech.vyaparsathi.supplier.controller;

import com.desitech.vyaparsathi.payment.dto.PaymentDto;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderPaymentSummaryDto;
import com.desitech.vyaparsathi.supplier.dto.SupplierBulkPaymentRequest;
import com.desitech.vyaparsathi.supplier.dto.SupplierPaymentDto;
import com.desitech.vyaparsathi.supplier.service.SupplierPaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for supplier payment operations.
 * All endpoints are exposed under {@code /api/supplier-payments}.
 *
 * <ul>
 *   <li>POST   /api/supplier-payments        – record a single PO payment</li>
 *   <li>POST   /api/supplier-payments/bulk   – pay multiple POs in one request</li>
 *   <li>GET    /api/supplier-payments        – list payments by supplierId or purchaseOrderId</li>
 *   <li>GET    /api/supplier-payments/summary – payment summary for a PO</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/supplier-payments")
public class SupplierPaymentController {

    private static final Logger logger = LoggerFactory.getLogger(SupplierPaymentController.class);

    @Autowired
    private SupplierPaymentService supplierPaymentService;

    /**
     * Record a single payment against a purchase order.
     *
     * <pre>POST /api/supplier-payments</pre>
     * Body: {@link SupplierPaymentDto} (supplierId, purchaseOrderId, amount, paymentMethod, …)
     */
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PostMapping
    public ResponseEntity<PaymentDto> recordPayment(@Valid @RequestBody SupplierPaymentDto dto) {
        logger.info("Recording supplier payment: supplierId={}, poId={}, amount={}",
                dto.getSupplierId(), dto.getPurchaseOrderId(), dto.getAmount());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supplierPaymentService.recordPayment(dto));
    }

    /**
     * Distribute a total amount across multiple purchase orders for a supplier (FIFO).
     *
     * <pre>POST /api/supplier-payments/bulk</pre>
     * Body: {@link SupplierBulkPaymentRequest} (supplierId, selectedPoIds, totalAmount, …)
     */
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

    /**
     * List payments filtered by supplier or purchase order (paginated).
     *
     * <pre>GET /api/supplier-payments?supplierId=X</pre>
     * <pre>GET /api/supplier-payments?purchaseOrderId=X</pre>
     */
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

    /**
     * Returns the payment summary (totalAmount, totalPaid, amountDue, paymentStatus) for a PO.
     *
     * <pre>GET /api/supplier-payments/summary?purchaseOrderId=X</pre>
     */
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @GetMapping("/summary")
    public ResponseEntity<PurchaseOrderPaymentSummaryDto> getPaymentSummary(
            @RequestParam Long purchaseOrderId) {
        return ResponseEntity.ok(supplierPaymentService.getPaymentSummary(purchaseOrderId));
    }
}
