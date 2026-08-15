package com.desitech.vyaparsathi.purchaseorder.controller;

import com.desitech.vyaparsathi.auth.security.CustomUserDetails;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderCancelDto;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderDto;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderItemDto;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderRejectDto;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderSendDto;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderTokenData;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderAttachment;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderAttachmentRepository;
import com.desitech.vyaparsathi.common.util.FileStorageService;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;
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
    @Autowired
    private PurchaseOrderAttachmentRepository attachmentRepository;
    @Autowired
    private FileStorageService fileStorageService;

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
    public ResponseEntity<PurchaseOrderDto> submit(@PathVariable Long id,
                                                   @AuthenticationPrincipal CustomUserDetails principal) {
        // V85: pass the authenticated user id so submittedBy is stamped for the
        // audit timeline. Threshold-based routing to PENDING_APPROVAL happens
        // inside the service based on shop policy.
        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(purchaseOrderService.submitPurchaseOrder(id, userId));
    }

    /**
     * List POs currently waiting for OWNER/ADMIN approval — powers the
     * /purchase-orders/approvals queue on the FE.
     */
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @GetMapping("/pending-approval")
    public ResponseEntity<List<PurchaseOrderDto>> getPendingApproval() {
        return ResponseEntity.ok(purchaseOrderService.findPendingApprovalOrders());
    }

    /**
     * Approve a PENDING_APPROVAL PO. Transitions to SUBMITTED so the
     * receiving workflow can pick it up as usual. OWNER/ADMIN only.
     */
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PostMapping("/{id}/approve")
    public ResponseEntity<PurchaseOrderDto> approve(@PathVariable Long id,
                                                    @AuthenticationPrincipal CustomUserDetails principal) {
        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(purchaseOrderService.approvePurchaseOrder(id, userId));
    }

    /**
     * Reject a PENDING_APPROVAL PO with a required reason. Transitions to
     * REJECTED (not DRAFT) so the approver's comment stays as a banner until
     * the requester clicks Revise. OWNER/ADMIN only.
     */
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PostMapping("/{id}/reject")
    public ResponseEntity<PurchaseOrderDto> reject(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderRejectDto body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(
                purchaseOrderService.rejectPurchaseOrder(id, body.getReason(), userId));
    }

    /**
     * Move a REJECTED PO back into DRAFT for revision + resubmit. Keeps the
     * rejection reason on the row so the FE can render it as a reference
     * banner during edit.
     *
     * <p>RBAC: STAFF included alongside OWNER/ADMIN because the requester is
     * typically the person who filed the PO — locking them out would force an
     * OWNER/ADMIN to click Revise on every rejection, defeating the
     * "requester edits and resubmits" pattern this workflow exists to enable.
     * Matches Quotation / Sale controller conventions.
     */
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
    @PostMapping("/{id}/revise")
    public ResponseEntity<PurchaseOrderDto> revise(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(purchaseOrderService.reviseRejectedPurchaseOrder(id, userId));
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
     * Send the PO to the supplier. Phase 5 wires real email dispatch: server
     * renders the PDF, attaches it, and emails supplier.email (or the
     * override supplied in the body). Body is entirely optional — an empty
     * POST still works and uses server-defaults.
     */
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PostMapping("/{id}/send")
    public ResponseEntity<PurchaseOrderDto> send(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) PurchaseOrderSendDto body) {
        String to = body != null ? body.getTo() : null;
        String subject = body != null ? body.getSubject() : null;
        String bodyText = body != null ? body.getBody() : null;
        boolean attachPdf = body == null || !Boolean.FALSE.equals(body.getAttachPdf());
        return ResponseEntity.ok(purchaseOrderService.sendToSupplier(id, to, subject, bodyText, attachPdf));
    }

    // ─── Attachments (Phase 5) ────────────────────────────────────────

    /** List attachments for a PO. Read-only; anyone with PO read access can see them. */
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @GetMapping("/{id}/attachments")
    public ResponseEntity<List<AttachmentSummaryDto>> listAttachments(@PathVariable Long id) {
        List<AttachmentSummaryDto> out = attachmentRepository
                .findByPurchaseOrderIdOrderByCreatedAtDesc(id)
                .stream()
                .map(a -> new AttachmentSummaryDto(a.getId(), a.getFileName(), a.getFileType(), a.getFilePath(), a.getUploadedBy()))
                .toList();
        return ResponseEntity.ok(out);
    }

    /**
     * Upload one attachment against a PO. Multipart request — the file goes
     * through {@link FileStorageService}, metadata rows persist here.
     */
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PostMapping(value = "/{id}/attachments", consumes = {"multipart/form-data"})
    public ResponseEntity<AttachmentSummaryDto> uploadAttachment(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails principal) throws Exception {
        var po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found: " + id));
        // FileStorageService takes a userId UUID; upload folder is per-doc-type
        // so PO attachments can be lifecycle-managed independently of receipts / invoices.
        UUID userUuid = principal != null && principal.getId() != null
                ? new UUID(0L, principal.getId()) : UUID.randomUUID();
        String storedPath = fileStorageService.storeFile(file, "purchase-order-attachments/" + po.getId(), userUuid);

        PurchaseOrderAttachment att = new PurchaseOrderAttachment();
        att.setPurchaseOrder(po);
        att.setFileName(file.getOriginalFilename());
        att.setFileType(file.getContentType());
        att.setFilePath(storedPath);
        att.setUploadedBy(principal != null ? principal.getId() : null);
        att = attachmentRepository.save(att);
        return ResponseEntity.ok(new AttachmentSummaryDto(att.getId(), att.getFileName(),
                att.getFileType(), att.getFilePath(), att.getUploadedBy()));
    }

    /** Remove an attachment. Bytes on the storage backend are NOT deleted —
     * that's a follow-up (retention policy). Metadata row is removed here. */
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        var att = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + attachmentId));
        if (att.getPurchaseOrder() == null || !att.getPurchaseOrder().getId().equals(id)) {
            throw new IllegalStateException("Attachment does not belong to PO " + id);
        }
        attachmentRepository.delete(att);
        return ResponseEntity.noContent().build();
    }

    /** Compact projection of an attachment — the file bytes are addressed
     * separately via the file-serving endpoint, not returned here. */
    public record AttachmentSummaryDto(Long id, String fileName, String fileType,
                                       String filePath, Long uploadedBy) {}

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

    /**
     * Force-close (short-close) a PO. Sets status to RECEIVED and records the
     * reason. Any GRN attempt on this PO after close is rejected by the
     * receiving service's SUBMITTED/PARTIALLY_RECEIVED status guard.
     */
    // ── V92 enterprise endpoints ────────────────────────────────────────

    @Autowired private com.desitech.vyaparsathi.purchaseorder.service.PurchaseOrderReportService poReportService;
    @Autowired private com.desitech.vyaparsathi.purchaseorder.service.PurchaseOrderSuggestionService poSuggestionService;

    @GetMapping("/{id}/history")
    public ResponseEntity<java.util.List<com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderStatusHistory>> history(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.getStatusHistory(id));
    }

    @GetMapping("/{id}/approvals")
    public ResponseEntity<java.util.List<com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderApproval>> approvals(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.getOrSeedApprovals(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PostMapping("/approvals/{approvalId}/approve")
    public ResponseEntity<com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderApproval> approveStep(
            @PathVariable Long approvalId,
            @RequestBody(required = false) java.util.Map<String, String> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                com.desitech.vyaparsathi.auth.security.CustomUserDetails principal) {
        Long userId = principal != null ? principal.getId() : null;
        String note = body != null ? body.get("note") : null;
        return ResponseEntity.ok(purchaseOrderService.approveStep(approvalId, userId, note));
    }

    @GetMapping("/reports/aging")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> agingReport(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(poReportService.agingByStatus(days));
    }

    @GetMapping("/reports/supplier-spend")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> supplierSpend(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        java.time.LocalDate f = from != null ? java.time.LocalDate.parse(from) : null;
        java.time.LocalDate t = to != null ? java.time.LocalDate.parse(to) : null;
        return ResponseEntity.ok(poReportService.supplierSpend(f, t));
    }

    @GetMapping("/reports/fulfillment")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> fulfillment() {
        return ResponseEntity.ok(poReportService.fulfillmentRate());
    }

    @GetMapping("/reports/budget-vs-actual")
    public ResponseEntity<java.util.Map<String, Object>> budgetVsActual(
            @RequestParam(required = false) java.math.BigDecimal budget) {
        return ResponseEntity.ok(poReportService.budgetVsActual(budget));
    }

    @GetMapping("/reports/export.csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        java.time.LocalDate f = from != null ? java.time.LocalDate.parse(from) : null;
        java.time.LocalDate t = to != null ? java.time.LocalDate.parse(to) : null;
        byte[] body = poReportService.exportPoCsv(f, t);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.parseMediaType("text/csv"));
        headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"po-register.csv\"");
        return new ResponseEntity<>(body, headers, org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/suggest-from-low-stock")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> suggestFromLowStock() {
        return ResponseEntity.ok(poSuggestionService.suggestFromLowStock());
    }

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PostMapping("/{id}/force-close")
    public ResponseEntity<PurchaseOrderDto> forceClose(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                com.desitech.vyaparsathi.auth.security.CustomUserDetails principal) {
        String reason = body != null ? body.get("reason") : null;
        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(purchaseOrderService.forceClose(id, reason, userId));
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
