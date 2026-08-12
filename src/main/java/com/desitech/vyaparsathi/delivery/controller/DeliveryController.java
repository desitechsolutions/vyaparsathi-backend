package com.desitech.vyaparsathi.delivery.controller;

import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.common.util.FileStorageService;
import com.desitech.vyaparsathi.delivery.dto.DeliveryChallanTokenData;
import com.desitech.vyaparsathi.delivery.dto.DeliveryDTO;
import com.desitech.vyaparsathi.delivery.dto.DeliveryMetricsDto;
import com.desitech.vyaparsathi.delivery.dto.DeliveryStatusHistoryDTO;
import com.desitech.vyaparsathi.delivery.entity.Delivery;
import com.desitech.vyaparsathi.delivery.enums.DeliveryStatus;
import com.desitech.vyaparsathi.delivery.repository.DeliveryRepository;
import com.desitech.vyaparsathi.delivery.service.DeliveryChallanPdfService;
import com.desitech.vyaparsathi.delivery.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/deliveries")
@Tag(name = "Deliveries", description = "Delivery lifecycle, POD, COD and operations metrics.")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STAFF')")
public class DeliveryController {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryController.class);

    private final DeliveryService service;
    private final DeliveryRepository deliveryRepo;
    private final DeliveryChallanPdfService pdfService;
    private final JwtUtil jwtUtil;
    private final FileStorageService fileStorageService;

    public DeliveryController(DeliveryService service,
                              DeliveryRepository deliveryRepo,
                              DeliveryChallanPdfService pdfService,
                              JwtUtil jwtUtil,
                              @org.springframework.beans.factory.annotation.Autowired(required = false)
                              FileStorageService fileStorageService) {
        this.service = service;
        this.deliveryRepo = deliveryRepo;
        this.pdfService = pdfService;
        this.jwtUtil = jwtUtil;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Create a delivery")
    public ResponseEntity<DeliveryDTO> create(@Valid @RequestBody DeliveryDTO delivery) {
        return ResponseEntity.status(201).body(service.createDelivery(delivery));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a delivery by id")
    public ResponseEntity<DeliveryDTO> get(@PathVariable Long id) {
        return service.getDelivery(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Search/list deliveries",
            description = "Paginated. Filter by status, delivery person, sale, date range, or free-text q " +
                    "(customer / address / tracking number).")
    public Page<DeliveryDTO> list(
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(required = false) Long deliveryPersonId,
            @RequestParam(required = false) Long saleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 25, sort = "createdAt") Pageable pageable
    ) {
        return service.search(status, deliveryPersonId, saleId,
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.atTime(23, 59, 59, 999_999_999),
                q, pageable);
    }

    /**
     * Backwards-compat: the old FE hits `?saleId=...` and expects a bare JSON array.
     * Kept as `/by-sale` so both flows continue to work.
     */
    @GetMapping("/by-sale/{saleId}")
    @Operation(summary = "List all deliveries for a sale")
    public List<DeliveryDTO> bySale(@PathVariable Long saleId) {
        return service.getDeliveriesBySaleId(saleId);
    }

    @PatchMapping("/{id}/details")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Update address, charge, notes, ETA, tracking, COD amount")
    public ResponseEntity<DeliveryDTO> updateDetails(@PathVariable Long id, @RequestBody DeliveryDTO delivery) {
        return ResponseEntity.ok(service.updateDeliveryDetails(id, delivery));
    }

    @PatchMapping("/{id}/person")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Assign / reassign delivery person")
    public ResponseEntity<DeliveryDTO> assignPerson(
            @PathVariable Long id,
            @RequestBody DeliveryDTO delivery
    ) {
        return ResponseEntity.ok(service.assignDeliveryPerson(id, delivery.getDeliveryPerson()));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update delivery status",
            description = "Server validates the transition. Setting the same status is a no-op (idempotent). " +
                    "The audit trail uses the authenticated principal — the old changedBy query param is ignored.")
    public ResponseEntity<DeliveryDTO> updateStatus(
            @PathVariable Long id,
            @RequestParam DeliveryStatus status,
            @Parameter(description = "Optional note attached to the audit-trail row.")
            @RequestParam(required = false) String note
    ) {
        return ResponseEntity.ok(service.updateStatus(id, status, note));
    }

    @PatchMapping("/{id}/pod")
    @Operation(summary = "Capture proof-of-delivery",
            description = "Record recipient name, signature URL, photo URL, OTP and mark POD collected. " +
                    "Pass codCollected=true to also mark the delivery's COD as collected.")
    public ResponseEntity<DeliveryDTO> capturePod(@PathVariable Long id, @RequestBody DeliveryDTO body) {
        return ResponseEntity.ok(service.capturePod(id, body));
    }

    @PostMapping("/{id}/attempts")
    @Operation(summary = "Log a failed delivery attempt")
    public ResponseEntity<DeliveryDTO> recordAttempt(
            @PathVariable Long id,
            @RequestParam(required = false) String reason
    ) {
        return ResponseEntity.ok(service.recordFailedAttempt(id, reason));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Full audit trail for a delivery")
    public List<DeliveryStatusHistoryDTO> history(@PathVariable Long id) {
        return service.getStatusHistory(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Delete a delivery (owner only, non-delivered rows only)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteDelivery(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/metrics")
    @Operation(summary = "Operations metrics",
            description = "Total / delivered / cancelled / in-progress; on-time %; avg lead time; " +
                    "COD collected; per-person productivity breakdown. Defaults to last 30 days.")
    public ResponseEntity<DeliveryMetricsDto> metrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(service.getMetrics(from, to));
    }

    // ── Bulk operations ──────────────────────────────────────────────

    @PostMapping("/bulk-assign")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Assign N deliveries to one person",
            description = "Terminal-state deliveries are silently skipped; the response reports how many actually changed hands.")
    public ResponseEntity<Map<String, Object>> bulkAssign(@RequestBody BulkAssignRequest body) {
        int changed = service.bulkAssign(body.getDeliveryIds(), body.getPersonId());
        return ResponseEntity.ok(Map.of(
                "requested", body.getDeliveryIds() == null ? 0 : body.getDeliveryIds().size(),
                "changed", changed
        ));
    }

    public static class BulkAssignRequest {
        private List<Long> deliveryIds;
        private Long personId;
        public List<Long> getDeliveryIds() { return deliveryIds; }
        public void setDeliveryIds(List<Long> deliveryIds) { this.deliveryIds = deliveryIds; }
        public Long getPersonId() { return personId; }
        public void setPersonId(Long personId) { this.personId = personId; }
    }

    // ── POD file uploads ─────────────────────────────────────────────

    @PostMapping(value = "/{id}/pod/signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload signature image", description = "Stores the file via the configured storage backend and updates podSignatureUrl.")
    public ResponseEntity<DeliveryDTO> uploadSignature(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(service.setPodSignatureUrl(id, storePodFile(id, "signature", file)));
    }

    @PostMapping(value = "/{id}/pod/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload delivery photo", description = "Stores the file via the configured storage backend and updates podPhotoUrl.")
    public ResponseEntity<DeliveryDTO> uploadPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(service.setPodPhotoUrl(id, storePodFile(id, "photo", file)));
    }

    private String storePodFile(Long deliveryId, String kind, MultipartFile file) {
        if (fileStorageService == null) {
            throw new ApplicationException("File storage service is not configured for this deployment", null);
        }
        if (file == null || file.isEmpty()) {
            throw new ApplicationException("Uploaded file is empty", null);
        }
        try {
            // Folder segregates by delivery id so files stay browsable per delivery.
            // UUID is required by the interface — we generate a fresh one per upload
            // so filenames are stable and non-guessable.
            String folder = "deliveries/" + deliveryId + "/pod-" + kind;
            return fileStorageService.storeFile(file, folder, UUID.randomUUID());
        } catch (Exception e) {
            logger.error("Failed to store POD {} for delivery {}: {}", kind, deliveryId, e.getMessage(), e);
            throw new ApplicationException("Failed to upload " + kind, e);
        }
    }

    // ── Delivery challan PDF (Rule 55) via signed URL ────────────────────

    @GetMapping("/{id}/challan-signed-url")
    @Operation(summary = "Issue a short-lived signed URL for the challan PDF")
    public ResponseEntity<String> getChallanSignedUrl(@PathVariable Long id) {
        Delivery d = deliveryRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Delivery", id));
        String token = jwtUtil.generateDeliveryChallanToken(d.getId(), d.getChallanNo());
        return ResponseEntity.ok("/api/deliveries/challan/signed?token=" + token);
    }

    @GetMapping("/challan/signed")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Fetch challan PDF via signed token (public — access is gated by the JWT)")
    public ResponseEntity<?> getSignedChallanPdf(@RequestParam String token,
                                                 @RequestParam(defaultValue = "false") boolean download) {
        boolean tenantSetByUs = false;
        try {
            DeliveryChallanTokenData data = jwtUtil.validateDeliveryChallanToken(token);

            // This endpoint is @PreAuthorize("permitAll()") so JwtAuthenticationFilter
            // never populated TenantContext. The PDF service is @Transactional and may
            // dirty-mark the Delivery (e.g. lazy-assigning a challan number), whose
            // commit-time @PreUpdate listener demands a shop id. Derive the shop from
            // the delivery's own row and set it here — the signed JWT itself is the
            // authorization boundary for this fetch.
            if (TenantContext.getCurrentShopId() == null) {
                Delivery d = deliveryRepo.findById(data.getDeliveryId()).orElse(null);
                if (d != null && d.getShop() != null && d.getShop().getId() != null) {
                    TenantContext.setCurrentShopId(d.getShop().getId());
                    tenantSetByUs = true;
                }
            }

            byte[] pdf = pdfService.generatePdf(data.getDeliveryId());
            String filename = "delivery_challan_" + (data.getChallanNo() != null
                    ? data.getChallanNo().replace('/', '_') : data.getDeliveryId()) + ".pdf";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentLength(pdf.length);
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
            String disposition = download ? "attachment" : "inline";
            headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + filename + "\"");
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Invalid or expired delivery challan token", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Invalid or expired access");
        } finally {
            // Clear only what we set. JwtAuthenticationFilter's own finally handles
            // the auth-header flow — this branch only fires when we populated the
            // context from a public request.
            if (tenantSetByUs) {
                TenantContext.clear();
            }
        }
    }
}
