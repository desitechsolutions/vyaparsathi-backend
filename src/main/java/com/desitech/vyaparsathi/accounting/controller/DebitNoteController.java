package com.desitech.vyaparsathi.accounting.controller;

import com.desitech.vyaparsathi.accounting.dto.DebitNoteCreateDto;
import com.desitech.vyaparsathi.accounting.dto.DebitNoteDto;
import com.desitech.vyaparsathi.accounting.service.DebitNoteService;
import com.desitech.vyaparsathi.common.payload.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/debit-notes")
public class DebitNoteController {

    private final DebitNoteService debitService;

    public DebitNoteController(DebitNoteService debitService) {
        this.debitService = debitService;
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
}
