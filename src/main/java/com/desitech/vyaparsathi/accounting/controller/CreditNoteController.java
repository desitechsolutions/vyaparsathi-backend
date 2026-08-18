package com.desitech.vyaparsathi.accounting.controller;

import com.desitech.vyaparsathi.accounting.dto.CreditNoteCreateDto;
import com.desitech.vyaparsathi.accounting.dto.CreditNoteDto;
import com.desitech.vyaparsathi.accounting.dto.CreditNoteTokenData;
import com.desitech.vyaparsathi.accounting.entity.CreditNote;
import com.desitech.vyaparsathi.accounting.enums.CreditNoteStatus;
import com.desitech.vyaparsathi.accounting.repository.CreditNoteRepository;
import com.desitech.vyaparsathi.accounting.service.CreditNoteService;
import com.desitech.vyaparsathi.accounting.service.NotePdfService;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/credit-notes")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class CreditNoteController {

    private static final Logger logger = LoggerFactory.getLogger(CreditNoteController.class);

    private final CreditNoteService creditService;
    private final CreditNoteRepository creditRepo;
    private final NotePdfService notePdfService;
    private final JwtUtil jwtUtil;

    public CreditNoteController(CreditNoteService creditService,
                                CreditNoteRepository creditRepo,
                                NotePdfService notePdfService,
                                JwtUtil jwtUtil) {
        this.creditService = creditService;
        this.creditRepo = creditRepo;
        this.notePdfService = notePdfService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreditNoteDto>> createCreditNote(@Valid @RequestBody CreditNoteCreateDto createDto) {
        CreditNoteDto dto = creditService.createCreditNote(createDto);
        return ResponseEntity.ok(new ApiResponse<>("success", "Credit Note created successfully", dto));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CreditNoteDto>>> getCreditNotes(@PageableDefault(size = 20) Pageable pageable) {
        Page<CreditNoteDto> page = creditService.getAllCreditNotes(pageable);
        return ResponseEntity.ok(new ApiResponse<>("success", "Credit Notes fetched successfully", page));
    }

    /** Look up credit notes issued against a given sale (most recent first). */
    @GetMapping("/by-sale/{saleId}")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> getBySale(@PathVariable Long saleId) {
        java.util.List<java.util.Map<String, Object>> results = creditRepo.findBySaleIdOrderByIdDesc(saleId).stream()
                .map(cn -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", cn.getId());
                    m.put("creditNoteNo", cn.getCreditNoteNo());
                    m.put("creditNoteDate", cn.getCreditNoteDate());
                    m.put("totalAmount", cn.getTotalAmount());
                    m.put("appliedAmount", cn.getAppliedAmount());
                    m.put("status", cn.getStatus() != null ? cn.getStatus().name() : null);
                    return m;
                }).toList();
        return ResponseEntity.ok(new ApiResponse<>("success", "Credit notes for sale", results));
    }

    /** Issues a signed URL a client can use to download the credit note PDF. */
    @GetMapping("/{id}/signed-url")
    public ResponseEntity<String> getSignedUrl(@PathVariable Long id) {
        CreditNote note = creditRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Credit Note", id));
        String token = jwtUtil.generateCreditNoteToken(note.getId(), note.getCreditNoteNo());
        return ResponseEntity.ok("/api/v1/credit-notes/signed?token=" + token);
    }

    /** Streams the credit note PDF; validates the signed token. */
    @GetMapping("/signed")
    @PreAuthorize("permitAll()")   // access is gated by the JWT, not by session
    public ResponseEntity<?> getSignedPdf(@RequestParam String token,
                                          @RequestParam(defaultValue = "false") boolean download) {
        CreditNoteTokenData data;
        try {
            data = jwtUtil.validateCreditNoteToken(token);
        } catch (Exception e) {
            logger.warn("Invalid or expired credit note token", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Invalid or expired access");
        }

        // Populate TenantContext from the credit note's own shop_id. The
        // signed-URL path is @PermitAll (JWT-in-URL is the credential), so
        // there is no session-derived tenant. Without this, any shop-scoped
        // query in the PDF pipeline — including the print-audit write —
        // rejects at the ShopEntityListener.
        Long previousShopId = com.desitech.vyaparsathi.common.configs.TenantContext.getCurrentShopId();
        try {
            CreditNote note = creditRepo.findById(data.getCreditNoteId())
                    .orElseThrow(() -> new EntityNotFoundAppException("Credit Note", data.getCreditNoteId()));
            if (note.getShop() != null && note.getShop().getId() != null) {
                com.desitech.vyaparsathi.common.configs.TenantContext.setCurrentShopId(note.getShop().getId());
            }

            byte[] pdf = notePdfService.generateCreditNotePdf(data.getCreditNoteId());
            String filename = "credit_note_" + (data.getCreditNoteNo() != null
                    ? data.getCreditNoteNo().replace('/', '_') : data.getCreditNoteId()) + ".pdf";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentLength(pdf.length);
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
            String disposition = download ? "attachment" : "inline";
            headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + filename + "\"");
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            // Token was valid — this is a PDF generation failure. Log the
            // real cause so we don't misdiagnose it as a token issue.
            logger.error("Credit note PDF generation failed for id={}", data.getCreditNoteId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Failed to generate credit note PDF");
        } finally {
            // Restore original (usually null) so we don't leak tenant state
            // to the next request that reuses this thread.
            if (previousShopId != null) {
                com.desitech.vyaparsathi.common.configs.TenantContext.setCurrentShopId(previousShopId);
            } else {
                com.desitech.vyaparsathi.common.configs.TenantContext.clear();
            }
        }
    }

    /**
     * Records that a portion (or all) of a credit note has been applied.
     * Body: {@code { "amount": <BigDecimal> }}.
     *
     * <p><b>Note on semantics:</b> credit notes issued from sales returns are
     * already reflected in the customer ledger at return time — so this endpoint
     * only tracks the applied portion and updates status. Wiring a
     * credit-note-to-payment-allocation is a separate follow-up when the
     * accounting model is generalized.
     */
    @PostMapping("/{id}/apply")
    @Transactional
    public ResponseEntity<ApiResponse<CreditNoteDto>> applyCreditNote(@PathVariable Long id,
                                                                       @RequestBody Map<String, Object> body) {
        CreditNote note = creditRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Credit Note", id));
        if (note.getStatus() == CreditNoteStatus.CANCELLED) {
            throw new BusinessValidationException("Cannot apply a cancelled credit note");
        }
        if (note.getStatus() == CreditNoteStatus.FULLY_APPLIED) {
            throw new BusinessValidationException("Credit note is already fully applied");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(String.valueOf(body.getOrDefault("amount", "0")));
        } catch (NumberFormatException e) {
            throw new BusinessValidationException("Invalid amount");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessValidationException("Amount must be > 0");
        }

        BigDecimal alreadyApplied = note.getAppliedAmount() != null ? note.getAppliedAmount() : BigDecimal.ZERO;
        BigDecimal remaining = note.getTotalAmount().subtract(alreadyApplied).max(BigDecimal.ZERO);
        if (amount.compareTo(remaining) > 0) {
            throw new BusinessValidationException("Amount exceeds remaining credit " + remaining);
        }

        note.setAppliedAmount(alreadyApplied.add(amount));
        if (note.getAppliedAmount().compareTo(note.getTotalAmount()) >= 0) {
            note.setStatus(CreditNoteStatus.FULLY_APPLIED);
        } else {
            note.setStatus(CreditNoteStatus.PARTIALLY_APPLIED);
        }
        creditRepo.save(note);

        CreditNoteDto dto = new CreditNoteDto();
        dto.setId(note.getId());
        dto.setCreditNoteNo(note.getCreditNoteNo());
        dto.setTotalAmount(note.getTotalAmount());
        dto.setAppliedAmount(note.getAppliedAmount());
        dto.setStatus(note.getStatus().name());
        return ResponseEntity.ok(new ApiResponse<>("success", "Credit Note applied", dto));
    }

    // ── V101 enterprise endpoints — get one, allocate, refund, reverse, cancel ──

    @org.springframework.beans.factory.annotation.Autowired
    private com.desitech.vyaparsathi.accounting.service.CreditNoteAllocationService allocationService;

    /** Full detail — used by the enterprise CreditNoteDetailPage. Returns a flat
     *  map so we don't have to grow CreditNoteDto for read-only aggregate fields
     *  (outstanding, reasonCode, restockItems, refunded, links). */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOne(@PathVariable Long id) {
        CreditNote n = creditRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Credit Note", id));
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("creditNoteNo", n.getCreditNoteNo());
        m.put("creditNoteDate", n.getCreditNoteDate());
        m.put("reason", n.getReason());
        m.put("reasonCode", n.getReasonCode() != null ? n.getReasonCode().name() : null);
        m.put("restockItems", Boolean.TRUE.equals(n.getRestockItems()));
        m.put("refunded", Boolean.TRUE.equals(n.getRefunded()));
        m.put("taxableAmount", n.getTaxableAmount());
        m.put("cgstAmount", n.getCgstAmount());
        m.put("sgstAmount", n.getSgstAmount());
        m.put("igstAmount", n.getIgstAmount());
        m.put("totalAmount", n.getTotalAmount());
        m.put("appliedAmount", n.getAppliedAmount());
        m.put("outstanding", n.getOutstandingAmount());
        m.put("status", n.getStatus() != null ? n.getStatus().name() : null);
        m.put("notes", n.getNotes());
        if (n.getCustomer() != null) {
            Map<String, Object> c = new java.util.LinkedHashMap<>();
            c.put("id", n.getCustomer().getId());
            c.put("name", n.getCustomer().getName());
            c.put("phone", n.getCustomer().getPhone());
            c.put("email", n.getCustomer().getEmail());
            c.put("gstin", n.getCustomer().getGstNumber());
            c.put("addressLine1", n.getCustomer().getAddressLine1());
            m.put("customer", c);
        }
        if (n.getSale() != null) {
            m.put("saleId", n.getSale().getId());
            m.put("invoiceNo", n.getSale().getInvoiceNo());
            m.put("invoiceDate", n.getSale().getDate());
        }
        return ResponseEntity.ok(new ApiResponse<>("success", "Credit Note", m));
    }

    /** Apply remaining credit against a customer's unpaid sale. */
    @PostMapping("/{id}/allocate")
    public ResponseEntity<ApiResponse<com.desitech.vyaparsathi.accounting.entity.CreditNoteAllocation>> allocate(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                com.desitech.vyaparsathi.auth.security.CustomUserDetails principal) {
        Long saleId = body.get("saleId") != null ? Long.valueOf(body.get("saleId").toString()) : null;
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        String note = body.get("note") != null ? body.get("note").toString() : null;
        String user = principal != null ? principal.getUsername() : "system";
        var row = allocationService.applyToInvoice(id, saleId, amount, user, note);
        return ResponseEntity.ok(new ApiResponse<>("success", "Allocated", row));
    }

    /** Record a cash/bank refund payout for the remaining credit. */
    @PostMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<com.desitech.vyaparsathi.accounting.entity.CreditNoteAllocation>> refund(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                com.desitech.vyaparsathi.auth.security.CustomUserDetails principal) {
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        String mode = body.get("paymentMode") != null ? body.get("paymentMode").toString() : "CASH";
        String ref  = body.get("paymentReference") != null ? body.get("paymentReference").toString() : null;
        String note = body.get("note") != null ? body.get("note").toString() : null;
        String user = principal != null ? principal.getUsername() : "system";
        var row = allocationService.recordRefund(id, amount, mode, ref, user, note);
        return ResponseEntity.ok(new ApiResponse<>("success", "Refund recorded", row));
    }

    @GetMapping("/{id}/allocations")
    public ResponseEntity<ApiResponse<java.util.List<com.desitech.vyaparsathi.accounting.entity.CreditNoteAllocation>>> listAllocations(
            @PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>("success", "Allocations",
                allocationService.listAllocations(id)));
    }

    @PostMapping("/allocations/{allocId}/reverse")
    public ResponseEntity<ApiResponse<com.desitech.vyaparsathi.accounting.entity.CreditNoteAllocation>> reverse(
            @PathVariable Long allocId,
            @RequestBody(required = false) Map<String, String> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                com.desitech.vyaparsathi.auth.security.CustomUserDetails principal) {
        String note = body != null ? body.get("note") : null;
        String user = principal != null ? principal.getUsername() : "system";
        return ResponseEntity.ok(new ApiResponse<>("success", "Reversed",
                allocationService.reverse(allocId, user, note)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<CreditNote>> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String note = body != null ? body.get("note") : null;
        return ResponseEntity.ok(new ApiResponse<>("success", "Cancelled",
                allocationService.cancel(id, note)));
    }
}
