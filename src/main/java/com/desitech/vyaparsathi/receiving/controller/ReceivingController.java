package com.desitech.vyaparsathi.receiving.controller;

import com.desitech.vyaparsathi.auth.security.CustomUserDetails;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.receiving.dto.CreateReceivingDto;
import com.desitech.vyaparsathi.receiving.dto.ReceivingActionRequest;
import com.desitech.vyaparsathi.receiving.dto.ReceivingDto;
import com.desitech.vyaparsathi.receiving.dto.ReceivingTicketDTO;
import com.desitech.vyaparsathi.receiving.dto.ReceivingTokenData;
import com.desitech.vyaparsathi.receiving.dto.ThreeWayMatchDto;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.entity.ReceivingApproval;
import com.desitech.vyaparsathi.receiving.entity.ReceivingBinAssignment;
import com.desitech.vyaparsathi.receiving.entity.ReceivingNotificationLog;
import com.desitech.vyaparsathi.receiving.entity.ReceivingStatusHistory;
import com.desitech.vyaparsathi.receiving.entity.ReceivingTicket;
import com.desitech.vyaparsathi.receiving.repository.ReceivingRepository;
import com.desitech.vyaparsathi.receiving.service.GrnPdfService;
import com.desitech.vyaparsathi.receiving.service.ReceivingApprovalService;
import com.desitech.vyaparsathi.receiving.service.ReceivingBinService;
import com.desitech.vyaparsathi.receiving.service.ReceivingBulkImportService;
import com.desitech.vyaparsathi.receiving.service.ReceivingNotificationService;
import com.desitech.vyaparsathi.receiving.service.ReceivingReportService;
import com.desitech.vyaparsathi.receiving.service.ReceivingService;
import com.desitech.vyaparsathi.receiving.service.ThreeWayMatchService;
import com.desitech.vyaparsathi.receiving.service.TicketDebitNoteService;
import com.desitech.vyaparsathi.accounting.dto.DebitNoteDto;
import com.desitech.vyaparsathi.receiving.entity.ApInvoice;
import com.desitech.vyaparsathi.receiving.repository.ApInvoiceRepository;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.extern.java.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/receiving")
@PreAuthorize("hasAnyRole('ADMIN','OWNER')")
public class ReceivingController {

    private static final Logger log = LoggerFactory.getLogger(ReceivingController.class);

    @Autowired
    private ReceivingService receivingService;

    @Autowired
    private GrnPdfService grnPdfService;

    @Autowired
    private ReceivingRepository receivingRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired private ThreeWayMatchService threeWayMatchService;
    @Autowired private ReceivingApprovalService approvalService;
    @Autowired private ReceivingBinService binService;
    @Autowired private ReceivingBulkImportService bulkImportService;
    @Autowired private ReceivingReportService reportService;
    @Autowired private ReceivingNotificationService notificationService;
    @Autowired private TicketDebitNoteService ticketDebitNoteService;
    @Autowired private ApInvoiceRepository apInvoiceRepository;
    @Autowired private com.desitech.vyaparsathi.receiving.service.ReceivingReturnService receivingReturnService;
    @Autowired private com.desitech.vyaparsathi.receiving.service.ReceivingExcelExporter excelExporter;
    @Autowired private com.desitech.vyaparsathi.receiving.service.AdvanceShipmentNoticeService asnService;

    @GetMapping
    public ResponseEntity<List<ReceivingDto>> getAllReceivings() {
        return ResponseEntity.ok(receivingService.getAllReceivings());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReceivingDto> getReceivingById(@PathVariable Long id) {
        return ResponseEntity.ok(receivingService.getReceivingById(id));
    }
    @GetMapping("/by-po/{poId}")
    public ResponseEntity<List<ReceivingDto>> getReceivingByPurchaseOrderId(@PathVariable Long poId) {
        return ResponseEntity.ok(receivingService.getAllByPurchaseOrderId(poId));
    }
    @GetMapping("/by-po-number/{poNumber}")
    public ResponseEntity<List<ReceivingDto>> getReceivingByPoNumber(@PathVariable String poNumber) {
        log.info("searching Receiving with PO Number: {}", poNumber);
        return ResponseEntity.ok(receivingService.getAllByPoNumber(poNumber));
    }

    @PostMapping
    public ResponseEntity<ReceivingDto> createReceiving(@Valid @RequestBody ReceivingDto receivingDto) {
        ReceivingDto receiving = receivingService.createReceiving(receivingDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(receiving);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReceivingDto> updateReceiving(
            @PathVariable Long id,
            @Valid @RequestBody ReceivingDto receivingDto) {

        return receivingService.updateReceiving(id, receivingDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReceiving(@PathVariable Long id) {
        receivingService.deleteReceiving(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tickets")
    public ResponseEntity<ReceivingTicket> createReceivingTicket(@Valid @RequestBody ReceivingTicketDTO receivingTicketDTO) {
        ReceivingTicket ticket = receivingService.createReceivingTicket(receivingTicketDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(ticket);
    }

    @GetMapping("/receiving-tickets/{id}")
    public ResponseEntity<List<ReceivingTicket>> getReceivingTicketByReceivingId(@PathVariable Long id) {
        return ResponseEntity.ok(receivingService.getReceivingTicketByReceivingId(id));
    }
    @GetMapping("/tickets/{id}")
    public ResponseEntity<ReceivingTicket> getReceivingTicketById(@PathVariable Long id) {
        return ResponseEntity.of(receivingService.getReceivingTicketById(id));
    }

    @GetMapping("/tickets")
    public ResponseEntity<List<ReceivingTicket>> getAllReceivingTickets() {
        return ResponseEntity.of(Optional.ofNullable(receivingService.getAllReceivingTickets()));
    }
    @PostMapping("/receive-goods")
    public ResponseEntity<ReceivingDto> receiveGoods(@Valid @RequestBody CreateReceivingDto createReceivingDto) {
        ReceivingDto receiving = receivingService.createInitialReceivingRecord(createReceivingDto);
        return ResponseEntity.ok(receiving);
    }

    // Added: Endpoint for updating ReceivingTicket (basic, expand as needed)
    @PutMapping("/tickets/{id}")
    public ResponseEntity<ReceivingTicket> updateReceivingTicket(@PathVariable Long id, @Valid @RequestBody ReceivingTicketDTO receivingTicketDTO) {
        return receivingService.updateReceivingTicket(id, receivingTicketDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Added: Endpoint for deleting ReceivingTicket
    @DeleteMapping("/tickets/{id}")
    public ResponseEntity<Void> deleteReceivingTicket(@PathVariable Long id) {
        receivingService.deleteReceivingTicket(id);
        return ResponseEntity.noContent().build();
    }

    // Added: Endpoint for attaching files to ReceivingTicket (using multipart)
    @PostMapping("/tickets/{id}/attachments")
    public ResponseEntity<ReceivingTicket> addAttachmentToTicket(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        ReceivingTicket updatedTicket = receivingService.addAttachmentToTicket(id, file);
        return ResponseEntity.ok(updatedTicket);
    }

    // ── Phase 6.1: state-machine entry points ────────────────────────────────

    /** DRAFT → PENDING (idempotent for committed statuses). */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER','STAFF')")
    public ResponseEntity<ReceivingDto> confirmReceiving(
            @PathVariable Long id,
            @RequestBody(required = false) ReceivingActionRequest body) {
        String note = body != null ? body.getNote() : null;
        return ResponseEntity.ok(receivingService.confirmReceiving(id, note));
    }

    /** Stamps approver + approvalNote on a committed GRN. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<ReceivingDto> approveReceiving(
            @PathVariable Long id,
            @RequestBody(required = false) ReceivingActionRequest body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        String note = body != null ? body.getNote() : null;
        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(receivingService.approveReceiving(id, note, userId));
    }

    /** Opens a top-level dispute ticket on a GRN. */
    @PostMapping("/{id}/dispute")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER','STAFF')")
    public ResponseEntity<ReceivingTicket> disputeReceiving(
            @PathVariable Long id,
            @RequestBody ReceivingActionRequest body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        String raiser = principal != null ? principal.getUsername() : "system";
        ReceivingTicket ticket = receivingService.disputeReceiving(
                id, body != null ? body.getNote() : null, raiser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ticket);
    }

    /** Voids a committed GRN and reverses stock. Draft GRNs must be deleted, not cancelled. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<ReceivingDto> cancelReceiving(
            @PathVariable Long id,
            @RequestBody ReceivingActionRequest body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        String reason = body != null ? body.getNote() : null;
        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(receivingService.cancelReceiving(id, reason, userId));
    }

    /** Timeline widget on the GRN detail page reads from this endpoint. */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<ReceivingStatusHistory>> getStatusHistory(@PathVariable Long id) {
        return ResponseEntity.ok(receivingService.getStatusHistory(id));
    }

    /** Moves a dispute ticket to RESOLVED. */
    @PostMapping("/tickets/{ticketId}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<ReceivingTicket> resolveReceivingTicket(
            @PathVariable Long ticketId,
            @RequestBody(required = false) ReceivingActionRequest body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        Long userId = principal != null ? principal.getId() : null;
        String note = body != null ? body.getNote() : null;
        return ResponseEntity.ok(receivingService.resolveReceivingTicket(ticketId, note, userId));
    }

    // ── Phase 6.1: signed-URL GRN PDF ────────────────────────────────────────
    // Mirrors PO / invoice / quotation pattern — authenticated call to
    // /{id}/signed-url returns a short-lived JWT URL, then GET /signed?token=…
    // (permitAll, JWT-validated) serves the PDF bytes. Lets the shop share the
    // URL with a supplier / auditor without exposing session cookies.

    @PreAuthorize("hasAnyRole('ADMIN','OWNER','STAFF')")
    @GetMapping("/{id}/signed-url")
    public ResponseEntity<String> getSignedUrl(@PathVariable Long id) {
        Receiving receiving = receivingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receiving not found: " + id));
        String token = jwtUtil.generateReceivingToken(receiving.getId(), receiving.getGrNumber());
        return ResponseEntity.ok("/api/receiving/signed?token=" + token);
    }

    // ── V91 Enterprise endpoints ─────────────────────────────────────────────

    /** 3-way match summary: PO ↔ GRN ↔ AP invoice with per-line variance. */
    @GetMapping("/{id}/three-way-match")
    public ResponseEntity<ThreeWayMatchDto> threeWayMatch(@PathVariable Long id) {
        return ResponseEntity.ok(threeWayMatchService.compute(id));
    }

    /** Approval steps for a GRN (seeds on first access based on total value). */
    @GetMapping("/{id}/approvals")
    public ResponseEntity<List<ReceivingApproval>> approvals(@PathVariable Long id) {
        return ResponseEntity.ok(approvalService.getOrSeedApprovals(id));
    }

    /** Approve one step (L1 then L2 as needed). */
    @PostMapping("/approvals/{approvalId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<ReceivingApproval> approveStep(
            @PathVariable Long approvalId,
            @RequestBody(required = false) ReceivingActionRequest body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        Long userId = principal != null ? principal.getId() : null;
        String note = body != null ? body.getNote() : null;
        return ResponseEntity.ok(approvalService.approve(approvalId, userId, note));
    }

    /** Bin/location assignments for a receiving line. */
    @GetMapping("/items/{itemId}/bins")
    public ResponseEntity<List<ReceivingBinAssignment>> listBins(@PathVariable Long itemId) {
        return ResponseEntity.ok(binService.list(itemId));
    }

    @PostMapping("/items/{itemId}/bins")
    public ResponseEntity<ReceivingBinAssignment> assignBin(
            @PathVariable Long itemId,
            @RequestBody java.util.Map<String, Object> body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        String binCode = String.valueOf(body.getOrDefault("binCode", ""));
        int qty = body.get("quantity") == null ? 0 : Integer.parseInt(body.get("quantity").toString());
        String user = principal != null ? principal.getUsername() : "system";
        return ResponseEntity.ok(binService.assign(itemId, binCode, qty, user));
    }

    @DeleteMapping("/bins/{assignmentId}")
    public ResponseEntity<Void> deleteBin(@PathVariable Long assignmentId) {
        binService.remove(assignmentId);
        return ResponseEntity.noContent().build();
    }

    /** Bulk-import GRN line qtys via CSV. Only allowed on DRAFT GRNs. */
    @PostMapping(value = "/{id}/bulk-import", consumes = {"multipart/form-data"})
    public ResponseEntity<ReceivingDto> bulkImport(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(bulkImportService.importCsv(id, file));
    }

    /** Issue supplier debit note from a dispute ticket in one click. */
    @PostMapping("/tickets/{ticketId}/debit-note")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<DebitNoteDto> debitNoteFromTicket(
            @PathVariable Long ticketId,
            @RequestBody(required = false) ReceivingActionRequest body) {
        String notes = body != null ? body.getNote() : null;
        return ResponseEntity.ok(ticketDebitNoteService.issueDebitNoteForTicket(ticketId, notes));
    }

    /** Notification audit log for a GRN. */
    @GetMapping("/{id}/notifications")
    public ResponseEntity<List<ReceivingNotificationLog>> notificationLog(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.getLog(id));
    }

    // ── AP invoice CRUD (3-way match feed) ───────────────────────────────────

    @PostMapping("/ap-invoices")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> createApInvoice(@RequestBody ApInvoice invoice) {
        // 3-way match feeder. Idempotent on (shop, supplier, invoice_no) —
        // a re-submit of the same invoice returns the existing row instead of
        // failing on the unique constraint (users double-click; modals reopen).
        if (invoice.getReceivingId() != null) {
            var existing = apInvoiceRepository.findByReceivingId(invoice.getReceivingId()).orElse(null);
            if (existing != null && java.util.Objects.equals(existing.getInvoiceNo(), invoice.getInvoiceNo())) {
                return ResponseEntity.ok(apInvoiceToMap(existing));
            }
        }
        ApInvoice saved;
        try {
            saved = apInvoiceRepository.save(invoice);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(java.util.Map.of(
                    "message", "An invoice with this number already exists for this supplier. Reuse or renumber it.",
                    "code", "DUPLICATE_INVOICE"));
        }
        // Sync the invoice ref onto the receiving so the Record-qty / Edit
        // pages show it in their header. Only overwrites empty fields so
        // manual edits already made on the receiving aren't clobbered.
        if (saved.getReceivingId() != null) {
            receivingRepository.findById(saved.getReceivingId()).ifPresent(r -> {
                boolean touched = false;
                if ((r.getSupplierInvoiceNo() == null || r.getSupplierInvoiceNo().isBlank())
                        && saved.getInvoiceNo() != null) {
                    r.setSupplierInvoiceNo(saved.getInvoiceNo());
                    touched = true;
                }
                if (r.getSupplierInvoiceDate() == null && saved.getInvoiceDate() != null) {
                    r.setSupplierInvoiceDate(saved.getInvoiceDate());
                    touched = true;
                }
                if (touched) receivingRepository.save(r);
            });
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(apInvoiceToMap(saved));
    }

    @GetMapping("/ap-invoices/by-receiving/{receivingId}")
    public ResponseEntity<?> apInvoiceForReceiving(@PathVariable Long receivingId) {
        return apInvoiceRepository.findByReceivingId(receivingId)
                .<ResponseEntity<?>>map(inv -> ResponseEntity.ok((Object) apInvoiceToMap(inv)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Flattens the AP invoice into a plain map so Jackson never touches the
     * lazy-loaded {@code shop} proxy — that was the source of the "ByteBuddy"
     * serialization crash on the FE.
     */
    private java.util.Map<String, Object> apInvoiceToMap(ApInvoice inv) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", inv.getId());
        m.put("supplierId", inv.getSupplierId());
        m.put("receivingId", inv.getReceivingId());
        m.put("invoiceNo", inv.getInvoiceNo());
        m.put("invoiceDate", inv.getInvoiceDate());
        m.put("dueDate", inv.getDueDate());
        m.put("paymentTermsDays", inv.getPaymentTermsDays());
        m.put("subtotal", inv.getSubtotal());
        m.put("taxAmount", inv.getTaxAmount());
        m.put("totalAmount", inv.getTotalAmount());
        m.put("matchStatus", inv.getMatchStatus());
        m.put("varianceAmount", inv.getVarianceAmount());
        m.put("varianceNote", inv.getVarianceNote());
        m.put("notes", inv.getNotes());
        return m;
    }

    // ── Reports ──────────────────────────────────────────────────────────────

    @GetMapping("/reports/pending")
    public ResponseEntity<List<java.util.Map<String, Object>>> pendingReceivals() {
        return ResponseEntity.ok(reportService.pendingReceivals());
    }

    @GetMapping("/reports/discrepancy")
    public ResponseEntity<List<java.util.Map<String, Object>>> discrepancyReport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        java.time.LocalDate fromD = from != null ? java.time.LocalDate.parse(from) : java.time.LocalDate.now().minusMonths(1);
        java.time.LocalDate toD = to != null ? java.time.LocalDate.parse(to) : java.time.LocalDate.now();
        return ResponseEntity.ok(reportService.discrepancyReport(fromD, toD));
    }

    @GetMapping("/reports/aging")
    public ResponseEntity<List<java.util.Map<String, Object>>> agingGrns(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(reportService.agingGrns(days));
    }

    @GetMapping("/reports/aging-tickets")
    public ResponseEntity<List<java.util.Map<String, Object>>> agingTickets(
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(reportService.agingTickets(hours));
    }

    @GetMapping("/reports/expiry")
    public ResponseEntity<List<java.util.Map<String, Object>>> expiryTracking(
            @RequestParam(defaultValue = "30") int windowDays) {
        return ResponseEntity.ok(reportService.expiryTracking(windowDays));
    }

    @GetMapping("/reports/supplier-performance")
    public ResponseEntity<List<java.util.Map<String, Object>>> supplierPerformance() {
        return ResponseEntity.ok(reportService.supplierPerformance());
    }

    // ── V93 P0/P1 gap closures ──────────────────────────────────────────

    /** Seed a Return-to-Vendor draft from a GRN — pulls damaged + rejected units. */
    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<com.desitech.vyaparsathi.purchasereturn.dto.PurchaseReturnDto> createReturn(
            @PathVariable Long id,
            @RequestBody(required = false) ReceivingActionRequest body) {
        String notes = body != null ? body.getNote() : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(receivingReturnService.createReturnFromReceiving(id, notes));
    }

    /** XLSX export of the GRN register — richer alternative to CSV. */
    @GetMapping("/reports/export.xlsx")
    public ResponseEntity<byte[]> exportXlsx(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        java.time.LocalDate fromD = from != null ? java.time.LocalDate.parse(from) : null;
        java.time.LocalDate toD = to != null ? java.time.LocalDate.parse(to) : null;
        byte[] body = excelExporter.exportGrnXlsx(fromD, toD);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"grn-register.xlsx\"");
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    // ── ASN endpoints (Advance Shipment Notice) ─────────────────────────

    @PostMapping("/asn")
    public ResponseEntity<com.desitech.vyaparsathi.receiving.entity.AdvanceShipmentNotice> createAsn(
            @RequestBody com.desitech.vyaparsathi.receiving.entity.AdvanceShipmentNotice payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(asnService.create(payload));
    }

    @GetMapping("/asn/by-po/{poId}")
    public ResponseEntity<List<com.desitech.vyaparsathi.receiving.entity.AdvanceShipmentNotice>> listAsn(@PathVariable Long poId) {
        return ResponseEntity.ok(asnService.listByPurchaseOrder(poId));
    }

    @PostMapping("/asn/{asnId}/consume")
    public ResponseEntity<com.desitech.vyaparsathi.receiving.entity.AdvanceShipmentNotice> consumeAsn(
            @PathVariable Long asnId,
            @RequestParam Long receivingId) {
        return ResponseEntity.ok(asnService.consumeForReceiving(asnId, receivingId));
    }

    @GetMapping("/reports/export.csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        java.time.LocalDate fromD = from != null ? java.time.LocalDate.parse(from) : null;
        java.time.LocalDate toD = to != null ? java.time.LocalDate.parse(to) : null;
        byte[] body = reportService.exportGrnCsv(fromD, toD);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"grn-register.csv\"");
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/signed")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> getSignedPdf(@RequestParam String token,
                                          @RequestParam(defaultValue = "false") boolean download) {
        try {
            ReceivingTokenData data = jwtUtil.validateReceivingToken(token);
            byte[] pdf = grnPdfService.generatePdf(data.getReceivingId());
            String safeNo = data.getGrNumber() != null
                    ? data.getGrNumber().replace('/', '_')
                    : String.valueOf(data.getReceivingId());
            String filename = "grn_" + safeNo + ".pdf";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentLength(pdf.length);
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
            String disposition = download ? "attachment" : "inline";
            headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + filename + "\"");
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Invalid or expired receiving token", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Invalid or expired access");
        }
    }
}