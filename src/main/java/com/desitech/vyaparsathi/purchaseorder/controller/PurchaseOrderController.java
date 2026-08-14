package com.desitech.vyaparsathi.purchaseorder.controller;

import com.desitech.vyaparsathi.auth.security.CustomUserDetails;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderCancelDto;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderDto;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderItemDto;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderTokenData;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import com.desitech.vyaparsathi.purchaseorder.service.PurchaseOrderPdfService;
import com.desitech.vyaparsathi.purchaseorder.service.PurchaseOrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    @Autowired
    private PurchaseOrderPdfService pdfService;
    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired
    private JwtUtil jwtUtil;

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

    /**
     * Duplicate an existing PO. Creates a fresh DRAFT with:
     *   - a new auto-generated PO number,
     *   - the same supplier, notes, freight, and line items (qty / cost /
     *     discount / GST / HSN preserved),
     *   - order date = today, expected delivery cleared,
     *   - status = DRAFT, all lifecycle stamps (sent/received/cancelled) cleared.
     * Used by the "Duplicate" action in the list + detail pages.
     */
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<PurchaseOrderDto> duplicate(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.duplicatePurchaseOrder(id));
    }

    // ─── Signed-URL PDF download ─────────────────────────────────────
    // Mirrors the quotation / invoice pattern: authenticated call to
    // /{id}/signed-url returns a short-lived JWT URL, then GET /signed?token=…
    // (permitAll but validates the JWT) serves the actual PDF bytes. Splits
    // auth from download so the shop can share the URL with a supplier without
    // exposing session cookies.

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @GetMapping("/{id}/signed-url")
    public ResponseEntity<String> getSignedUrl(@PathVariable Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found: " + id));
        String token = jwtUtil.generatePurchaseOrderToken(po.getId(), po.getPoNumber());
        return ResponseEntity.ok("/api/purchase-orders/signed?token=" + token);
    }

    @GetMapping("/signed")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> getSignedPdf(@RequestParam String token,
                                          @RequestParam(defaultValue = "false") boolean download) {
        try {
            PurchaseOrderTokenData data = jwtUtil.validatePurchaseOrderToken(token);
            byte[] pdf = pdfService.generatePdf(data.getPurchaseOrderId());
            String safeNo = data.getPoNumber() != null
                    ? data.getPoNumber().replace('/', '_')
                    : String.valueOf(data.getPurchaseOrderId());
            String filename = "purchase_order_" + safeNo + ".pdf";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentLength(pdf.length);
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
            String disposition = download ? "attachment" : "inline";
            headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + filename + "\"");
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Invalid or expired purchase-order token", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Invalid or expired access");
        }
    }
}
