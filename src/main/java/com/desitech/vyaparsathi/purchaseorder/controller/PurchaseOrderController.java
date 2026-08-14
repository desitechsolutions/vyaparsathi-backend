package com.desitech.vyaparsathi.purchaseorder.controller;

import com.desitech.vyaparsathi.auth.security.CustomUserDetails;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderCancelDto;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderDto;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderItemDto;
import com.desitech.vyaparsathi.purchaseorder.service.PurchaseOrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    /**
     * Zoho-parity: an "open" alias for the pending endpoint. The old
     * {@code /pending} path stays for backward-compat with any client
     * still calling it.
     */
    @GetMapping("/open")
    public List<PurchaseOrderDto> getOpenOrders() {
        return purchaseOrderService.findOpenOrders();
    }

    /**
     * Cancel a committed PO. Requires a written reason; the current user
     * id is stamped on the row so the timeline shows who authorised it.
     */
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<PurchaseOrderDto> cancel(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderCancelDto body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(
                purchaseOrderService.cancelPurchaseOrder(id, body.getReason(), userId));
    }

    /**
     * Send the PO to the supplier. Phase 1 records the timestamp; Phase 5
     * dispatches the actual email via the existing EmailService.
     */
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PostMapping("/{id}/send")
    public ResponseEntity<PurchaseOrderDto> send(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.sendToSupplier(id));
    }

    /**
     * Mark the PO fully received. In normal flow this is invoked by the
     * receiving listener once every line hits ordered = received. Manual
     * endpoint exists so an admin can close a PO under an edge case
     * (e.g., supplier can't deliver the last N units and both sides agree
     * to close early).
     */
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PostMapping("/{id}/mark-received")
    public ResponseEntity<PurchaseOrderDto> markReceived(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.markAsReceived(id));
    }
}
