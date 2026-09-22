package com.desitech.vyaparsathi.sales.controller;

import com.desitech.vyaparsathi.sales.dto.OfflineSalesQueueRequest;
import com.desitech.vyaparsathi.sales.dto.OfflineSalesQueueResponse;
import com.desitech.vyaparsathi.sales.entity.OfflineSalesQueue;
import com.desitech.vyaparsathi.sales.repository.OfflineSalesQueueRepository;
import com.desitech.vyaparsathi.sales.service.OfflineSalesProcessorService;
import com.desitech.vyaparsathi.sales.service.OfflineSalesQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline Sales Queue Controller
 *
 * Endpoints:
 * 1. POST /api/sales/offline-queue - Enqueue an offline sale
 * 2. GET /api/sales/offline-queue/{clientTxnId} - Get queue status
 * 3. GET /api/sales/offline-queue/shop/{shopId}/pending - Get pending count
 * 4. POST /api/sales/offline-queue/shop/{shopId}/process - Trigger processing (manual)
 * 5. GET /api/sales/offline-queue/shop/{shopId}/stats - Get processing statistics
 */
@Slf4j
@RestController
@RequestMapping("/api/sales/offline-queue")
@RequiredArgsConstructor
public class OfflineSalesQueueController {

    private final OfflineSalesQueueService offlineSalesQueueService;
    private final OfflineSalesProcessorService offlineSalesProcessorService;
    private final OfflineSalesQueueRepository offlineSalesQueueRepository;

    /**
     * Validates that the requested shopId matches the calling user's shop from the JWT.
     * Returns the validated shopId, or throws a 403 ResponseStatusException.
     */
    private Long validateShopOwnership(Long requestedShopId) {
        Long jwtShopId = com.desitech.vyaparsathi.common.configs.TenantContext.getCurrentShopId();
        if (jwtShopId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.FORBIDDEN, "Shop context not found in token");
        }
        if (requestedShopId != null && !requestedShopId.equals(jwtShopId)) {
            log.warn("[Security] Shop ownership mismatch: requested={}, jwt={}", requestedShopId, jwtShopId);
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.FORBIDDEN, "Access denied to requested shop");
        }
        return jwtShopId;
    }

    /**
     * List pending/failed/draft sales for a shop
     *
     * Called by:
     * 1. Frontend "Offline Queue" drawer to list all unsynced sales
     * 2. Admin tooling for monitoring
     *
     * Query params:
     *   shopId  (required) — the shop to fetch for
     *   statuses (optional, comma-separated) — filter by status(es).
     *             Defaults to DRAFT,PENDING,FAILED if not provided.
     *
     * Example: GET /api/sales/offline-queue?shopId=1
     * Example: GET /api/sales/offline-queue?shopId=1&statuses=DRAFT,FAILED
     */
    @GetMapping
    public ResponseEntity<List<OfflineSalesQueueResponse>> getPendingSales(
        @RequestParam Long shopId,
        @RequestParam(value = "statuses", required = false) String statusesParam
    ) {
        log.info("[Controller] Get pending sales for shop: {}, statuses: {}", shopId, statusesParam);

        try {
            // ★ SECURITY: verify caller owns this shop
            Long verifiedShopId = validateShopOwnership(shopId);

            List<com.desitech.vyaparsathi.sales.enums.OfflineSalesStatus> statuses = null;
            if (statusesParam != null && !statusesParam.isBlank()) {
                statuses = java.util.Arrays.stream(statusesParam.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(com.desitech.vyaparsathi.sales.enums.OfflineSalesStatus::valueOf)
                    .collect(java.util.stream.Collectors.toList());
            }

            List<com.desitech.vyaparsathi.sales.entity.OfflineSalesQueue> sales =
                offlineSalesQueueService.getPendingSales(verifiedShopId, statuses);

            List<OfflineSalesQueueResponse> responses = sales.stream()
                .map(q -> offlineSalesQueueService.toPublicResponse(q))
                .toList();

            return ResponseEntity.ok(responses);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (IllegalArgumentException e) {
            log.warn("[Controller] Invalid status value in request: {}", statusesParam);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("[Controller] Failed to get pending sales", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Enqueue an offline sale from frontend
     *
     * Frontend calls this when:
     * - App detects offline mode
     * - User completes a sale while offline
     * - Retry occurs on network failure
     *
     * Response includes clientTxnId and offlineSaleNo for receipt printing
     */
    @PostMapping
    public ResponseEntity<OfflineSalesQueueResponse> enqueueSale(
        @RequestHeader(value = "X-Shop-Id", required = false) Long headerShopId,
        @RequestParam(value = "shopId", required = false) Long paramShopId,
        @RequestBody OfflineSalesQueueRequest request
    ) {
        // ★ SECURITY: always take shopId from JWT (TenantContext), treat header/param as hint only
        Long jwtShopId = com.desitech.vyaparsathi.common.configs.TenantContext.getCurrentShopId();
        Long requestedShopId = headerShopId != null ? headerShopId : paramShopId;

        if (jwtShopId == null) {
            log.warn("[Controller] No shop context in JWT for offline sale enqueue");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // If the frontend sends a shopId, it must match what the token says
        if (requestedShopId != null && !requestedShopId.equals(jwtShopId)) {
            log.warn("[Security] Offline enqueue shopId mismatch: requested={}, jwt={}", requestedShopId, jwtShopId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Long shopId = jwtShopId;

        // OFF-9 fix: derive userId exclusively from the authenticated principal.
        // The X-User-Id header is a client-supplied value and must not be trusted —
        // an attacker could attribute sales to any other user within the same tenant.
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String userId = (auth != null && auth.isAuthenticated() && auth.getName() != null
                         && !auth.getName().equals("anonymousUser"))
                        ? auth.getName() : null;
        if (userId == null) {
            log.warn("[Controller] Unauthenticated offline sale enqueue attempt for shop={}", shopId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("[Controller] Enqueue offline sale: shop={}, clientTxnId={}",
            shopId, request.getClientTxnId());

        try {
            OfflineSalesQueueResponse response = offlineSalesQueueService.enqueueSale(shopId, userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("[Controller] Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("[Controller] Failed to enqueue sale", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get status of a queued sale
     *
     * Frontend polls this to track:
     * - When sale moved from DRAFT → PROCESSING → COMPLETED
     * - Invoice number once available
     * - Any errors if processing failed
     */
    @GetMapping("/{clientTxnId}")
    public ResponseEntity<OfflineSalesQueueResponse> getSaleStatus(
        @PathVariable String clientTxnId
    ) {
        log.info("[Controller] Get status for clientTxnId: {}", clientTxnId);

        // OFF-3 fix: use the tenant-scoped single-query overload so tenant verification
        // and data fetch are atomic — eliminates the TOCTOU race and cross-tenant
        // existence oracle in the original two-query pattern.
        Long jwtShopId = com.desitech.vyaparsathi.common.configs.TenantContext.getCurrentShopId();
        if (jwtShopId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            OfflineSalesQueueResponse response = offlineSalesQueueService.getSaleStatus(clientTxnId, jwtShopId);
            return ResponseEntity.ok(response);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (RuntimeException e) {
            log.warn("[Controller] Sale not found or access denied: {}", clientTxnId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get pending sale count for a shop
     *
     * Used by frontend for:
     * - UI badge showing pending sync count
     * - Deciding when to show sync button/indicator
     */
    @GetMapping("/shop/{shopId}/pending")
    public ResponseEntity<Map<String, Object>> getPendingCount(
        @PathVariable Long shopId
    ) {
        log.info("[Controller] Get pending count for shop: {}", shopId);

        try {
            validateShopOwnership(shopId);
            long pendingCount = offlineSalesQueueService.getPendingCount(shopId);
            Map<String, Object> response = new HashMap<>();
            response.put("shopId", shopId);
            response.put("pendingCount", pendingCount);
            response.put("hasOfflineData", pendingCount > 0);

            return ResponseEntity.ok(response);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            log.error("[Controller] Failed to get pending count", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Trigger offline sales processing (manual or automatic)
     *
     * Called by:
     * 1. Frontend when app comes online (auto-trigger)
     * 2. Admin endpoint for manual processing
     * 3. Scheduler job for batch processing
     *
     * Processing is ASYNC - returns immediately, processes in background
     */
    @PostMapping("/shop/{shopId}/process")
    public ResponseEntity<Map<String, Object>> triggerProcessing(
        @PathVariable Long shopId
    ) {
        log.info("[Controller] Triggering offline sales processing for shop: {}", shopId);

        try {
            validateShopOwnership(shopId);
            offlineSalesProcessorService.processQueuedSales(shopId);

            Map<String, Object> response = new HashMap<>();
            response.put("shopId", shopId);
            response.put("message", "Processing triggered. Check status with GET /pending");
            response.put("processingStarted", true);

            return ResponseEntity.accepted().body(response);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            log.error("[Controller] Failed to trigger processing", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get processing statistics
     *
     * Shows:
     * - How many pending (DRAFT + PENDING)
     * - How many failed (retriable)
     * - How many conflicted (manual review needed)
     */
    @GetMapping("/shop/{shopId}/stats")
    public ResponseEntity<Map<String, Object>> getStats(
        @PathVariable Long shopId
    ) {
        log.info("[Controller] Get processing stats for shop: {}", shopId);

        try {
            validateShopOwnership(shopId);
            var stats = offlineSalesProcessorService.getStats(shopId);

            Map<String, Object> response = new HashMap<>();
            response.put("shopId", shopId);
            response.put("pending", stats.getPending());
            response.put("failed", stats.getFailed());
            response.put("conflicted", stats.getConflicted());
            response.put("total", stats.getPending() + stats.getFailed() + stats.getConflicted());

            return ResponseEntity.ok(response);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            log.error("[Controller] Failed to get stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get conflicted sales for admin review
     */
    @GetMapping("/shop/{shopId}/conflicted")
    public ResponseEntity<List<OfflineSalesQueueResponse>> getConflictedSales(
        @PathVariable Long shopId
    ) {
        log.info("[Controller] Get conflicted sales for shop: {}", shopId);

        try {
            validateShopOwnership(shopId);
            List<OfflineSalesQueue> conflicts = offlineSalesQueueService.getConflictedSales(shopId);
            List<OfflineSalesQueueResponse> responses = conflicts.stream()
                .map(q -> OfflineSalesQueueResponse.builder()
                    .id(q.getId())
                    .clientTxnId(q.getClientTxnId())
                    .status(q.getStatus())
                    .offlineSaleNo(q.getOfflineSaleNo())
                    .errorCode(q.getErrorCode())
                    .errorMessage(q.getErrorMessage())
                    .retryCount(q.getRetryCount())
                    .createdAt(q.getCreatedAt())
                    .updatedAt(q.getUpdatedAt())
                    .build())
                .toList();

            return ResponseEntity.ok(responses);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            log.error("[Controller] Failed to get conflicted sales", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Retry a failed or conflicted sale
     *
     * Resets status to PENDING and clears error details
     * Backend scheduler will pick it up for reprocessing
     */
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @PostMapping("/{queueId}/retry")
    public ResponseEntity<OfflineSalesQueueResponse> retrySale(
        @PathVariable Long queueId
    ) {
        log.info("[Controller] Retry sale: queueId={}", queueId);

        try {
            var queuedSale = offlineSalesQueueRepository.findById(queueId)
                .orElseThrow(() -> new RuntimeException("Queue record not found"));
            // Verify the record belongs to the calling user's shop
            validateShopOwnership(queuedSale.getShopId());

            offlineSalesQueueService.resetToRetryable(queueId);
            offlineSalesProcessorService.processQueuedSales(queuedSale.getShopId());

            // Ownership already verified by validateShopOwnership above; use unchecked overload.
            OfflineSalesQueueResponse response = offlineSalesQueueService.getSaleStatusUnchecked(queuedSale.getClientTxnId());
            return ResponseEntity.accepted().body(response);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            log.error("[Controller] Failed to retry sale", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Override a conflicted sale (manual admin action)
     *
     * Marks sale as COMPLETED without re-processing
     * Used when admin manually creates the sale and wants to link it
     */
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @PostMapping("/{queueId}/override")
    public ResponseEntity<OfflineSalesQueueResponse> overrideConflict(
        @PathVariable Long queueId,
        @RequestBody Map<String, Object> request
    ) {
        log.info("[Controller] Override conflict: queueId={}, saleId={}", queueId, request.get("saleId"));

        try {
            var queuedSale = offlineSalesQueueRepository.findById(queueId)
                .orElseThrow(() -> new RuntimeException("Queue record not found"));
            // Verify the record belongs to the calling user's shop
            validateShopOwnership(queuedSale.getShopId());

            Long saleId = Long.parseLong(request.get("saleId").toString());
            offlineSalesQueueService.markCompleted(queueId, saleId);

            // Ownership already verified by validateShopOwnership above; use unchecked overload.
            OfflineSalesQueueResponse response = offlineSalesQueueService.getSaleStatusUnchecked(queuedSale.getClientTxnId());
            return ResponseEntity.ok(response);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            log.error("[Controller] Failed to override conflict", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check endpoint
     * Useful for monitoring offline queue system availability
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("service", "OfflineSalesQueue");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }
}
