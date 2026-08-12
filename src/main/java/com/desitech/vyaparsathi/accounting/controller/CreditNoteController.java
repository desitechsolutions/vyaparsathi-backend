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
        try {
            CreditNoteTokenData data = jwtUtil.validateCreditNoteToken(token);
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
            logger.error("Invalid or expired credit note token", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Invalid or expired access");
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
}
