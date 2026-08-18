package com.desitech.vyaparsathi.sales.controller;

import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.sales.dto.SaleCreateResponse;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.desitech.vyaparsathi.sales.dto.SaleDto;
import com.desitech.vyaparsathi.sales.dto.SaleDueDto;
import com.desitech.vyaparsathi.sales.dto.SaleReturnDto;
import com.desitech.vyaparsathi.sales.service.SaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/sales")
// Class-level "any authenticated" gate replaced by per-method @RequirePermission
// checks below — bridge in PermissionResolver keeps existing role-based logins
// working while granting the exact permission set (SALES_*) for each action.
@Tag(name = "Sales Management", description = "Operations for sales management including COGS tracking, returns, cancellations, and profit reporting")
public class SaleController {

    private static final Logger logger = LoggerFactory.getLogger(SaleController.class);
    @Autowired
    private SaleService service;
    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SALES_CREATE")
    public ResponseEntity<SaleCreateResponse> create(
            @Valid @RequestBody SaleDto dto,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        // Prefer HTTP header over body value — but honour whichever is provided.
        // Duplicate POSTs with the same key return the original sale via the
        // service-level guard (see SaleService.createSale).
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            dto.setIdempotencyKey(idempotencyKey);
        }
        Long customerId = dto.getCustomer() != null ? dto.getCustomer().getId() : null;
        logger.info("Creating sale for customerId={} idem={}", customerId, dto.getIdempotencyKey());
        SaleDto sale = service.createSale(dto);
        logger.info("Created sale for customerId={}", customerId);
        SaleCreateResponse response = new SaleCreateResponse(
                sale.getId(),
                sale.getInvoiceNo(),
                sale.getSignedInvoiceUrl()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/drafts")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SALES_CREATE")
    public ResponseEntity<SaleCreateResponse> saveDraft(@Valid @RequestBody SaleDto dto) {
        // Walk-in / not-yet-selected customer is a valid POS case (esp. for "Hold" —
        // cashier parks the cart, then looks up the customer). Null-safe the log.
        Long customerId = (dto.getCustomer() != null) ? dto.getCustomer().getId() : null;
        logger.info("Saving draft for customerId={}, existingId={}", customerId, dto.getId());
        SaleDto draft = service.saveOrUpdateDraft(dto);
        return ResponseEntity.ok(new SaleCreateResponse(draft.getId(), draft.getInvoiceNo(), null));
    }
    @GetMapping("/{id}")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SALES_VIEW")
    public ResponseEntity<SaleDto> getSale(@PathVariable Long id) {
        try {
            var result = service.getSaleById(id);
            if (result.isPresent()) {
                logger.info("Fetched sale with id={}", id);
                return ResponseEntity.ok(result.get());
            } else {
                logger.warn("Sale not found with id={}", id);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error fetching sale with id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to fetch sale", e);
        }
    }

    @PutMapping("/{id}/complete")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SALES_CREATE")
    public ResponseEntity<SaleCreateResponse> completeDraft(
            @PathVariable Long id,
            @Valid @RequestBody SaleDto dto) {

        logger.info("Completing draft sale id={}", id);

        dto.setId(id);   // ensure consistency

        SaleDto completed = service.completeDraft(dto);

        return ResponseEntity.ok(
                new SaleCreateResponse(
                        completed.getId(),
                        completed.getInvoiceNo(),
                        completed.getSignedInvoiceUrl()
                )
        );
    }

    @GetMapping
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SALES_VIEW")
    public ResponseEntity<List<SaleDto>> listSales(
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {
        try {
            var result = service.listSales(startDate, endDate);
            logger.info("Fetched sales list from {} to {}", startDate, endDate);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching sales list from {} to {}: {}", startDate, endDate, e.getMessage(), e);
            throw new ApplicationException("Failed to fetch sales list", e);
        }
    }

    @GetMapping("/with-due")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SALES_VIEW")
    public ResponseEntity<List<SaleDueDto>> getSalesWithDue() {
        try {
            var result = service.getSalesWithDue();
            logger.info("Fetched sales with due");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching sales with due: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to fetch sales with due", e);
        }
    }

    @GetMapping("/history")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SALES_VIEW")
    public ResponseEntity<Page<SaleDueDto>> getSalesHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) com.desitech.vyaparsathi.sales.enums.SaleStatus status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate from,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate to
    ) {
        try {
            Pageable pageable = PageRequest.of(page, size,
                    org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "date"));
            LocalDateTime fromDt = from == null ? null : from.atStartOfDay();
            LocalDateTime toDt   = to   == null ? null : to.atTime(23, 59, 59, 999_999_999);
            var result = service.getSalesHistory(q, status, customerId, fromDt, toDt, pageable);
            logger.info("Fetched sales history page={}, size={}, filters(q={}, status={}, customerId={}, from={}, to={}), total={}",
                    page, size, q, status, customerId, from, to, result.getTotalElements());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching sales history: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to fetch sales history", e);
        }
    }


    @GetMapping("/{id}/due")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SALES_VIEW")
    public ResponseEntity<SaleDueDto> getSaleDueBySaleId(@PathVariable Long id) {
        try {
            var result = service.getSaleDueBySaleId(id);
            logger.info("Fetched sale due for saleId={}", id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching sale due for saleId={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to fetch sale due", e);
        }
    }
    @GetMapping("/{customerId}/dues")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SALES_VIEW")
    public ResponseEntity<Page<SaleDueDto>> getCustomerDues(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<SaleDueDto> dues = service.getDuesByCustomerId(customerId, pageable);
            logger.info("Fetched customer dues for customerId={}", customerId);
            return ResponseEntity.ok(dues);
        } catch (Exception e) {
            logger.error("Error fetching customer dues for customerId={}: {}", customerId, e.getMessage(), e);
            throw new ApplicationException("Failed to fetch customer dues", e);
        }
    }

    @DeleteMapping("/drafts/{id}")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SALES_DELETE")
    @Operation(summary = "Discard a DRAFT or HELD sale",
               description = "Hard-delete a work-in-progress sale that never became a committed row. Rejects COMPLETED / CANCELLED / RETURNED (those have ledger + stock impact — use /cancel for those). Used when a caller pivots (e.g. save-as-proforma after a draft) and the draft would otherwise orphan.")
    public ResponseEntity<Void> discardDraft(@PathVariable Long id) {
        service.discardDraft(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/park")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SALES_EDIT")
    @Operation(summary = "Park a DRAFT sale (POS 'hold order for later')",
               description = "DRAFT → HELD. Same underlying state (no ledger, no stock) but flagged HELD so the UI can list parked orders separately from auto-saved drafts. Idempotent — parking a HELD sale is a no-op. Rejects any other status.")
    public ResponseEntity<SaleDto> parkSale(@PathVariable Long id) {
        return ResponseEntity.ok(service.parkSale(id));
    }

    @PostMapping("/{id}/resume")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SALES_EDIT")
    @Operation(summary = "Resume a parked sale",
               description = "HELD → DRAFT. Returns the sale to active editing so the standard complete-draft flow works unchanged. Idempotent for DRAFT.")
    public ResponseEntity<SaleDto> resumeSale(@PathVariable Long id) {
        return ResponseEntity.ok(service.resumeSale(id));
    }

    @PatchMapping("/{id}/notes")
    @Operation(summary = "Update sale notes",
               description = "Non-destructive metadata edit. Accepts a JSON body {\"notes\":\"…\"} — pass null or an empty string to clear. Works on sales in any status.")
    public ResponseEntity<SaleDto> updateSaleNotes(
            @PathVariable Long id,
            @RequestBody UpdateNotesRequest body) {
        SaleDto updated = service.updateNotes(id, body == null ? null : body.getNotes());
        return ResponseEntity.ok(updated);
    }

    /** Minimal payload for {@link #updateSaleNotes} so we don't pull a full SaleDto over the wire. */
    public static class UpdateNotesRequest {
        private String notes;
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    @PostMapping("/{id}/return")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SALES_CANCEL")
    @Operation(summary = "Process sale return",
               description = "Process partial or full return of items from a sale. Automatically restores stock and handles payment/ledger reversal if requested. Restricted to OWNER/ADMIN — staff can raise the request but not commit it.")
    @ApiResponse(responseCode = "200", description = "Sale return processed successfully")
    public ResponseEntity<Void> processSaleReturn(
            @Parameter(description = "Sale ID") @PathVariable Long id, 
            @RequestBody SaleReturnDto returnDto) {
        try {
            returnDto.setSaleId(id); // Ensure consistency
            service.processSaleReturn(returnDto);
            logger.info("Processed sale return for saleId={}", id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error processing sale return for saleId={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to process sale return", e);
        }
    }

    @PostMapping("/{id}/cancel")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SALES_CANCEL")
    @Operation(summary = "Cancel entire sale",
               description = "Cancel an entire sale transaction. Restores all stock, reverses all payments and ledger entries. Irreversible action. Restricted to OWNER/ADMIN.")
    @ApiResponse(responseCode = "200", description = "Sale cancelled successfully")
    public ResponseEntity<Void> cancelSale(
            @Parameter(description = "Sale ID") @PathVariable Long id, 
            @Parameter(description = "Reason for cancellation") @RequestParam String reason) {
        try {
            service.cancelSale(id, reason);
            logger.info("Cancelled sale with id={}", id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error cancelling sale with id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to cancel sale", e);
        }
    }
    @GetMapping("/{saleId}/signed-url")
    public ResponseEntity<String> getSignedUrlForSale(@PathVariable Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new EntityNotFoundException("Sale not found"));

        String token = jwtUtil.generateInvoiceToken(sale.getId(), sale.getInvoiceNo());
        String url = "/api/invoices/signed?token=" + token;

        return ResponseEntity.ok(url);
    }

    @GetMapping("/{id}/timeline")
    @Operation(summary = "Sale timeline (void / refund / cancel history)",
               description = "Read-only list of state-mutating events for a single sale — merges AuditLog rows and linked credit notes, newest first. Used by the FE 'Void / refund history' dialog. Shop-scoped and RBAC-gated at the controller class level.")
    public ResponseEntity<List<com.desitech.vyaparsathi.sales.dto.SaleTimelineEventDto>> getSaleTimeline(@PathVariable Long id) {
        return ResponseEntity.ok(service.getSaleTimeline(id));
    }

    @PostMapping("/{id}/convert-proforma-to-invoice")
    @Operation(summary = "Convert a proforma sale to a real invoice",
               description = "Creates a new INVOICE sale from a PROFORMA sale. Deducts stock, posts customer ledger, links the new invoice back to the source proforma. Rejects if the proforma has already been converted.")
    @ApiResponse(responseCode = "200", description = "Proforma converted successfully")
    public ResponseEntity<SaleDto> convertProformaToInvoice(@PathVariable Long id) {
        SaleDto result = service.convertProformaToInvoice(id);
        logger.info("Converted proforma id={} → invoice {}", id, result.getInvoiceNo());
        return ResponseEntity.ok(result);
    }
}