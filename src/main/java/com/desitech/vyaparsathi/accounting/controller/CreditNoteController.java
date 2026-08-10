package com.desitech.vyaparsathi.accounting.controller;

import com.desitech.vyaparsathi.accounting.dto.CreditNoteCreateDto;
import com.desitech.vyaparsathi.accounting.dto.CreditNoteDto;
import com.desitech.vyaparsathi.accounting.service.CreditNoteService;
import com.desitech.vyaparsathi.common.payload.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/credit-notes")
public class CreditNoteController {

    private final CreditNoteService creditService;

    public CreditNoteController(CreditNoteService creditService) {
        this.creditService = creditService;
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
}
