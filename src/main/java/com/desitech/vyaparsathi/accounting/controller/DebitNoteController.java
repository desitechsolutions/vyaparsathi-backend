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
