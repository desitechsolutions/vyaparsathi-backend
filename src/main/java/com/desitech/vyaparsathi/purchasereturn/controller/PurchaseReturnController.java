package com.desitech.vyaparsathi.purchasereturn.controller;

import com.desitech.vyaparsathi.purchasereturn.dto.CreatePurchaseReturnDto;
import com.desitech.vyaparsathi.purchasereturn.dto.PurchaseReturnDto;
import com.desitech.vyaparsathi.purchasereturn.service.PurchaseReturnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchase-returns")
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
@Tag(name = "Purchase Returns & Debit Notes", description = "Operations for vendor purchase returns and debit note management")
public class PurchaseReturnController {

    @Autowired
    private PurchaseReturnService purchaseReturnService;

    @PostMapping
    @Operation(summary = "Create a draft Purchase Return", description = "Creates a new Purchase Return record in DRAFT status")
    public ResponseEntity<PurchaseReturnDto> createPurchaseReturn(@Valid @RequestBody CreatePurchaseReturnDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseReturnService.createPurchaseReturn(dto));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve Purchase Return", description = "Approves a Purchase Return, deducts inventory stock, and issues a Debit Note")
    public ResponseEntity<PurchaseReturnDto> approvePurchaseReturn(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseReturnService.approvePurchaseReturn(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel draft Purchase Return", description = "Cancels a DRAFT Purchase Return")
    public ResponseEntity<PurchaseReturnDto> cancelPurchaseReturn(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseReturnService.cancelPurchaseReturn(id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Purchase Return by ID", description = "Retrieves details of a specific Purchase Return")
    public ResponseEntity<PurchaseReturnDto> getPurchaseReturnById(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseReturnService.getPurchaseReturnById(id));
    }

    @GetMapping
    @Operation(summary = "List Purchase Returns", description = "Lists purchase returns, optionally filtered by supplierId")
    public ResponseEntity<Page<PurchaseReturnDto>> getPurchaseReturns(
            @RequestParam(required = false) Long supplierId,
            Pageable pageable) {
        return ResponseEntity.ok(purchaseReturnService.getPurchaseReturns(supplierId, pageable));
    }

    // ── Signed URL / server-rendered PDF ─────────────────────────────────

    @org.springframework.beans.factory.annotation.Autowired
    private com.desitech.vyaparsathi.purchasereturn.service.PurchaseReturnPdfService pdfService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.desitech.vyaparsathi.auth.security.JwtUtil jwtUtil;
    @org.springframework.beans.factory.annotation.Autowired
    private com.desitech.vyaparsathi.purchasereturn.repository.PurchaseReturnRepository prRepo;

    @GetMapping("/{id}/signed-url")
    public ResponseEntity<String> signedUrl(@PathVariable Long id) {
        var pr = prRepo.findById(id).orElseThrow(() ->
                new com.desitech.vyaparsathi.common.exception.ResourceNotFoundException("Purchase Return not found: " + id));
        String token = jwtUtil.generatePurchaseReturnToken(pr.getId(), pr.getReturnNo());
        return ResponseEntity.ok("/api/purchase-returns/signed?token=" + token);
    }

    @GetMapping("/signed")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> signedPdf(@RequestParam String token,
                                       @RequestParam(defaultValue = "false") boolean download) {
        try {
            var data = jwtUtil.validatePurchaseReturnToken(token);
            byte[] pdf = pdfService.generatePdf(data.getPurchaseReturnId());
            String safeNo = data.getReturnNo() != null
                    ? data.getReturnNo().replace('/', '_')
                    : String.valueOf(data.getPurchaseReturnId());
            String filename = "purchase_return_" + safeNo + ".pdf";
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
            headers.setContentLength(pdf.length);
            headers.set(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
            String disposition = download ? "attachment" : "inline";
            headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                    disposition + "; filename=\"" + filename + "\"");
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                    .body("Invalid or expired access");
        }
    }
}
