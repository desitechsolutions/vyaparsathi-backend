package com.desitech.vyaparsathi.accounting.controller;

import com.desitech.vyaparsathi.accounting.dto.DebitNoteCreateDto;
import com.desitech.vyaparsathi.accounting.dto.DebitNoteDto;
import com.desitech.vyaparsathi.accounting.dto.DebitNoteTokenData;
import com.desitech.vyaparsathi.accounting.entity.DebitNote;
import com.desitech.vyaparsathi.accounting.repository.DebitNoteRepository;
import com.desitech.vyaparsathi.accounting.service.DebitNoteService;
import com.desitech.vyaparsathi.accounting.service.NotePdfService;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.common.payload.ApiResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/debit-notes")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class DebitNoteController {

    private static final Logger logger = LoggerFactory.getLogger(DebitNoteController.class);

    private final DebitNoteService debitService;
    private final DebitNoteRepository debitRepo;
    private final NotePdfService notePdfService;
    private final JwtUtil jwtUtil;

    public DebitNoteController(DebitNoteService debitService,
                               DebitNoteRepository debitRepo,
                               NotePdfService notePdfService,
                               JwtUtil jwtUtil) {
        this.debitService = debitService;
        this.debitRepo = debitRepo;
        this.notePdfService = notePdfService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DebitNoteDto>> createDebitNote(@Valid @RequestBody DebitNoteCreateDto createDto) {
        DebitNoteDto dto = debitService.createDebitNote(createDto);
        return ResponseEntity.ok(new ApiResponse<>("success", "Debit Note created successfully", dto));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<DebitNoteDto>>> getDebitNotes(@PageableDefault(size = 20) Pageable pageable) {
        Page<DebitNoteDto> page = debitService.getAllDebitNotes(pageable);
        return ResponseEntity.ok(new ApiResponse<>("success", "Debit Notes fetched successfully", page));
    }

    /** Look up debit notes issued against a given purchase return (most recent first). */
    @GetMapping("/by-purchase-return/{returnId}")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> getByPurchaseReturn(@PathVariable Long returnId) {
        java.util.List<java.util.Map<String, Object>> results = debitRepo.findByPurchaseReturnIdOrderByIdDesc(returnId).stream()
                .map(dn -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", dn.getId());
                    m.put("debitNoteNo", dn.getDebitNoteNo());
                    m.put("debitNoteDate", dn.getDebitNoteDate());
                    m.put("totalAmount", dn.getTotalAmount());
                    m.put("appliedAmount", dn.getAppliedAmount());
                    m.put("status", dn.getStatus() != null ? dn.getStatus().name() : null);
                    return m;
                }).toList();
        return ResponseEntity.ok(new ApiResponse<>("success", "Debit notes for purchase return", results));
    }

    /** Issues a signed URL a client can use to download the debit note PDF. */
    @GetMapping("/{id}/signed-url")
    public ResponseEntity<String> getSignedUrl(@PathVariable Long id) {
        DebitNote note = debitRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Debit Note", id));
        String token = jwtUtil.generateDebitNoteToken(note.getId(), note.getDebitNoteNo());
        return ResponseEntity.ok("/api/v1/debit-notes/signed?token=" + token);
    }

    // ── V98 enterprise endpoints — get one, apply, reverse, cancel, reports ──

    @org.springframework.beans.factory.annotation.Autowired
    private com.desitech.vyaparsathi.accounting.service.DebitNoteApplicationService applicationService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.desitech.vyaparsathi.accounting.service.DebitNoteReportService reportService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getOne(@PathVariable Long id) {
        DebitNote n = debitRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Debit Note", id));
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("debitNoteNo", n.getDebitNoteNo());
        m.put("debitNoteDate", n.getDebitNoteDate());
        m.put("reason", n.getReason());
        m.put("taxableAmount", n.getTaxableAmount());
        m.put("cgstAmount", n.getCgstAmount());
        m.put("sgstAmount", n.getSgstAmount());
        m.put("igstAmount", n.getIgstAmount());
        m.put("totalAmount", n.getTotalAmount());
        m.put("appliedAmount", n.getAppliedAmount());
        m.put("outstanding", n.getTotalAmount().subtract(
                n.getAppliedAmount() == null ? java.math.BigDecimal.ZERO : n.getAppliedAmount()));
        m.put("status", n.getStatus());
        m.put("notes", n.getNotes());
        if (n.getSupplier() != null) {
            java.util.Map<String, Object> s = new java.util.LinkedHashMap<>();
            s.put("id", n.getSupplier().getId());
            s.put("name", n.getSupplier().getName());
            s.put("phone", n.getSupplier().getPhone());
            s.put("email", n.getSupplier().getEmail());
            s.put("gstin", n.getSupplier().getGstin());
            s.put("address", n.getSupplier().getAddress());
            m.put("supplier", s);
        }
        if (n.getPurchaseReturn() != null) {
            m.put("purchaseReturnId", n.getPurchaseReturn().getId());
            m.put("purchaseReturnNo", n.getPurchaseReturn().getReturnNo());
        }
        if (n.getPurchaseInvoice() != null) {
            m.put("purchaseInvoiceId", n.getPurchaseInvoice().getId());
        }
        return ResponseEntity.ok(new ApiResponse<>("success", "Debit Note", m));
    }

    @PostMapping("/{id}/apply")
    public ResponseEntity<ApiResponse<com.desitech.vyaparsathi.accounting.entity.DebitNoteApplication>> apply(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                com.desitech.vyaparsathi.auth.security.CustomUserDetails principal) {
        java.math.BigDecimal amount = new java.math.BigDecimal(body.get("amount").toString());
        Long invoiceId = body.get("purchaseInvoiceId") != null ? Long.valueOf(body.get("purchaseInvoiceId").toString()) : null;
        Long paymentId = body.get("supplierPaymentId") != null ? Long.valueOf(body.get("supplierPaymentId").toString()) : null;
        String note = body.get("note") != null ? body.get("note").toString() : null;
        String user = principal != null ? principal.getUsername() : "system";
        var app = applicationService.apply(id, invoiceId, paymentId, amount, user, note);
        return ResponseEntity.ok(new ApiResponse<>("success", "Applied", app));
    }

    @GetMapping("/{id}/applications")
    public ResponseEntity<ApiResponse<java.util.List<com.desitech.vyaparsathi.accounting.entity.DebitNoteApplication>>> listApplications(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>("success", "Applications",
                applicationService.listApplications(id)));
    }

    @PostMapping("/applications/{appId}/reverse")
    public ResponseEntity<ApiResponse<com.desitech.vyaparsathi.accounting.entity.DebitNoteApplication>> reverse(
            @PathVariable Long appId,
            @RequestBody(required = false) java.util.Map<String, String> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                com.desitech.vyaparsathi.auth.security.CustomUserDetails principal) {
        String note = body != null ? body.get("note") : null;
        String user = principal != null ? principal.getUsername() : "system";
        return ResponseEntity.ok(new ApiResponse<>("success", "Reversed",
                applicationService.reverse(appId, user, note)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<DebitNote>> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String note = body != null ? body.get("note") : null;
        return ResponseEntity.ok(new ApiResponse<>("success", "Cancelled",
                applicationService.cancelDebitNote(id, note)));
    }

    @GetMapping("/reports/aging")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> aging() {
        return ResponseEntity.ok(new ApiResponse<>("success", "Aging", reportService.aging()));
    }

    @GetMapping("/reports/supplier-summary")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> supplierSummary() {
        return ResponseEntity.ok(new ApiResponse<>("success", "Supplier summary", reportService.supplierSummary()));
    }

    @GetMapping("/reports/export.csv")
    public ResponseEntity<byte[]> exportCsv() {
        byte[] body = reportService.exportCsv();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"debit-notes.csv\"");
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/reports/export.xlsx")
    public ResponseEntity<byte[]> exportXlsx() {
        byte[] body = reportService.exportXlsx();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"debit-notes.xlsx\"");
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    /** Streams the debit note PDF; validates the signed token. */
    @GetMapping("/signed")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> getSignedPdf(@RequestParam String token,
                                          @RequestParam(defaultValue = "false") boolean download) {
        try {
            DebitNoteTokenData data = jwtUtil.validateDebitNoteToken(token);
            byte[] pdf = notePdfService.generateDebitNotePdf(data.getDebitNoteId());
            String filename = "debit_note_" + (data.getDebitNoteNo() != null
                    ? data.getDebitNoteNo().replace('/', '_') : data.getDebitNoteId()) + ".pdf";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentLength(pdf.length);
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
            String disposition = download ? "attachment" : "inline";
            headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + filename + "\"");
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Invalid or expired debit note token", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Invalid or expired access");
        }
    }
}
