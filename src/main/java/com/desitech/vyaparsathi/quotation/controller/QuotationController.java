package com.desitech.vyaparsathi.quotation.controller;

import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.common.payload.ApiResponse;
import com.desitech.vyaparsathi.quotation.dto.QuotationDto;
import com.desitech.vyaparsathi.quotation.dto.QuotationTokenData;
import com.desitech.vyaparsathi.quotation.entity.Quotation;
import com.desitech.vyaparsathi.quotation.enums.QuotationStatus;
import com.desitech.vyaparsathi.quotation.repository.QuotationRepository;
import com.desitech.vyaparsathi.quotation.service.QuotationPdfService;
import com.desitech.vyaparsathi.quotation.service.QuotationService;
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

import java.util.Map;

@RestController
@RequestMapping("/api/quotations")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STAFF')")
public class QuotationController {

    private static final Logger logger = LoggerFactory.getLogger(QuotationController.class);

    private final QuotationService quotationService;
    private final QuotationPdfService pdfService;
    private final QuotationRepository quotationRepo;
    private final JwtUtil jwtUtil;

    public QuotationController(QuotationService quotationService,
                               QuotationPdfService pdfService,
                               QuotationRepository quotationRepo,
                               JwtUtil jwtUtil) {
        this.quotationService = quotationService;
        this.pdfService = pdfService;
        this.quotationRepo = quotationRepo;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<QuotationDto>> create(@Valid @RequestBody QuotationDto dto) {
        return ResponseEntity.ok(new ApiResponse<>("success", "Quotation created", quotationService.create(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<QuotationDto>> update(@PathVariable Long id,
                                                             @Valid @RequestBody QuotationDto dto) {
        return ResponseEntity.ok(new ApiResponse<>("success", "Quotation updated", quotationService.update(id, dto)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QuotationDto>> get(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>("success", "Quotation fetched", quotationService.get(id)));
    }

    @GetMapping
    public ResponseEntity<Page<QuotationDto>> list(@PageableDefault(size = 20) Pageable pageable,
                                                   @RequestParam(required = false) QuotationStatus status,
                                                   @RequestParam(required = false) Long customerId) {
        return ResponseEntity.ok(quotationService.list(pageable, status, customerId));
    }

    // ── State transitions ────────────────────────────────────────────────

    @PostMapping("/{id}/send")
    public ResponseEntity<ApiResponse<QuotationDto>> send(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>("success", "Quotation sent", quotationService.send(id)));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<QuotationDto>> accept(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>("success", "Quotation accepted", quotationService.accept(id)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<QuotationDto>> reject(@PathVariable Long id,
                                                            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(new ApiResponse<>("success", "Quotation rejected", quotationService.reject(id, reason)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<QuotationDto>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>("success", "Quotation cancelled", quotationService.cancel(id)));
    }

    @PostMapping("/{id}/convert-to-sale")
    public ResponseEntity<ApiResponse<QuotationDto>> convertToSale(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>("success", "Quotation converted to draft sale", quotationService.convertToSale(id)));
    }

    // ── Signed URL PDF download ──────────────────────────────────────────

    @GetMapping("/{id}/signed-url")
    public ResponseEntity<String> getSignedUrl(@PathVariable Long id) {
        Quotation q = quotationRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Quotation", id));
        String token = jwtUtil.generateQuotationToken(q.getId(), q.getQuotationNo());
        return ResponseEntity.ok("/api/quotations/signed?token=" + token);
    }

    @GetMapping("/signed")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> getSignedPdf(@RequestParam String token,
                                          @RequestParam(defaultValue = "false") boolean download) {
        try {
            QuotationTokenData data = jwtUtil.validateQuotationToken(token);
            byte[] pdf = pdfService.generatePdf(data.getQuotationId());
            String filename = "quotation_" + (data.getQuotationNo() != null
                    ? data.getQuotationNo().replace('/', '_') : data.getQuotationId()) + ".pdf";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentLength(pdf.length);
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
            String disposition = download ? "attachment" : "inline";
            headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + filename + "\"");
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Invalid or expired quotation token", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Invalid or expired access");
        }
    }
}
