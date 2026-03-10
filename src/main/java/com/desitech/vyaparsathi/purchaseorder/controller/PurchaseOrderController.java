package com.desitech.vyaparsathi.purchaseorder.controller;

import com.desitech.vyaparsathi.supplier.dto.SupplierPaymentDto;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderDto;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderItemDto;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderPaymentSummaryDto;
import com.desitech.vyaparsathi.purchaseorder.service.PurchaseOrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {
    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderController.class);
    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PostMapping
    public ResponseEntity<PurchaseOrderDto> create(@Valid @RequestBody PurchaseOrderDto dto) {
        return ResponseEntity.ok(purchaseOrderService.createPurchaseOrder(dto));
    }

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @GetMapping
    public ResponseEntity<List<PurchaseOrderDto>> getAll() {
        return ResponseEntity.ok(purchaseOrderService.findAllPurchaseOrders());
    }

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrderDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.findPurchaseOrderById(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PutMapping("/{id}")
    public ResponseEntity<PurchaseOrderDto> update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderDto dto) {
        return ResponseEntity.ok(purchaseOrderService.updatePurchaseOrder(id, dto));
    }

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PostMapping("/{id}/submit")
    public ResponseEntity<PurchaseOrderDto> submit(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.submitPurchaseOrder(id));
    }

    /**
     * Signals that receiving has begun for this PO.
     * Transitions the PO from SUBMITTED → PARTIALLY_RECEIVED.
     */
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PostMapping("/{id}/receive")
    public ResponseEntity<PurchaseOrderDto> markAsReceiving(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.markAsReceiving(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @GetMapping("/{id}/items")
    public ResponseEntity<List<PurchaseOrderItemDto>> getItemsForPurchaseOrder(@PathVariable Long id) {
        PurchaseOrderDto po = purchaseOrderService.findPurchaseOrderById(id);
        return ResponseEntity.ok(po.getItems() != null ? po.getItems() : List.of());
    }

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        purchaseOrderService.deletePurchaseOrder(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/pending")
    public List<PurchaseOrderDto> getPendingPurchaseOrders() {
        return purchaseOrderService.getPendingPurchaseOrders();
    }

    // ─── Payment Endpoints ────────────────────────────────────────────────────

    /**
     * Record a payment for a Purchase Order.
     *
     * <pre>POST /api/purchase-orders/{id}/payments</pre>
     * Body: {@link SupplierPaymentDto} (amount, paymentMethod, reference, notes, …)
     */
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PostMapping("/{id}/payments")
    public ResponseEntity<SupplierPaymentDto> recordPayment(
            @PathVariable Long id,
            @Valid @RequestBody SupplierPaymentDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(purchaseOrderService.recordPayment(id, dto));
    }

    /**
     * List all payments recorded against a Purchase Order (paginated).
     *
     * <pre>GET /api/purchase-orders/{id}/payments</pre>
     */
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @GetMapping("/{id}/payments")
    public ResponseEntity<Page<SupplierPaymentDto>> getPayments(
            @PathVariable Long id,
            Pageable pageable) {
        return ResponseEntity.ok(purchaseOrderService.getPayments(id, pageable));
    }

    /**
     * Returns the payment summary (totalAmount, totalPaid, amountDue, paymentStatus).
     *
     * <pre>GET /api/purchase-orders/{id}/payment-summary</pre>
     */
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @GetMapping("/{id}/payment-summary")
    public ResponseEntity<PurchaseOrderPaymentSummaryDto> getPaymentSummary(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.getPaymentSummary(id));
    }
}