package com.desitech.vyaparsathi.compliance.controller;

import com.desitech.vyaparsathi.accounting.entity.CreditNote;
import com.desitech.vyaparsathi.common.payload.ApiResponse;
import com.desitech.vyaparsathi.compliance.dto.StandaloneNoteCreateDto;
import com.desitech.vyaparsathi.compliance.service.ComplianceCreditNoteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/compliance")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class ComplianceCreditNoteController {

    private final ComplianceCreditNoteService service;

    public ComplianceCreditNoteController(ComplianceCreditNoteService service) {
        this.service = service;
    }

    /**
     * Creates a standalone GST compliance credit note.
     *
     * <p>Accepts either a {@code saleId} (to reference a known Sale) or an explicit
     * {@code originalInvoiceNumber} + {@code originalInvoiceDate} pair. Returns the
     * saved note id and note number for the client to display.
     */
    @PostMapping("/credit-notes")
    public ResponseEntity<ApiResponse<CreatedNoteResponse>> create(
            @Valid @RequestBody StandaloneNoteCreateDto dto) {
        CreditNote saved = service.create(dto);
        CreatedNoteResponse resp = new CreatedNoteResponse(
                saved.getId(), saved.getCreditNoteNo(), saved.getTotalAmount());
        return ResponseEntity.ok(new ApiResponse<>("success", "Credit note created", resp));
    }

    /** Minimal projection returned to the caller after note creation. */
    public record CreatedNoteResponse(Long id, String creditNoteNo, java.math.BigDecimal totalAmount) {}
}
