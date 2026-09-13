package com.desitech.vyaparsathi.sales.service;

import com.desitech.vyaparsathi.sales.dto.OfflineSalesQueueRequest;
import com.desitech.vyaparsathi.sales.dto.OfflineSalesQueueResponse;
import com.desitech.vyaparsathi.sales.entity.OfflineSalesQueue;
import com.desitech.vyaparsathi.sales.enums.OfflineSalesStatus;
import com.desitech.vyaparsathi.sales.repository.OfflineSalesQueueRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Offline Sales Queue Service
 *
 * Handles:
 * 1. Enqueuing offline sales from frontend (with idempotency)
 * 2. Status tracking and error management
 * 3. Cleanup of old completed records
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineSalesQueueService {

    private final OfflineSalesQueueRepository offlineSalesQueueRepository;
    private final ObjectMapper objectMapper;

    /**
     * Enqueue an offline sale from the frontend
     *
     * Idempotency check: if clientTxnId already exists, return existing record
     * This is CRUCIAL to prevent duplicates on network retries
     */
    @Transactional
    public OfflineSalesQueueResponse enqueueSale(Long shopId, String userId, OfflineSalesQueueRequest request) {
        validateEnqueueRequest(shopId, userId, request);

        // ★ IDEMPOTENCY CHECK ★
        // If this clientTxnId was already queued, return the existing record
        Optional<OfflineSalesQueue> existingOpt = offlineSalesQueueRepository
            .findByShopIdAndClientTxnId(shopId, request.getClientTxnId());

        if (existingOpt.isPresent()) {
            log.info("[OfflineQueue] Duplicate clientTxnId detected: {}. Returning existing record.",
                request.getClientTxnId());
            return toPublicResponse(existingOpt.get());
        }

        // ★ CREATE NEW QUEUED RECORD ★
        try {
            String requestPayloadJson = objectMapper.writeValueAsString(request);
            String offlineSaleNo = generateOfflineSaleNo(shopId);

            OfflineSalesQueue queuedSale = OfflineSalesQueue.builder()
                .shopId(shopId)
                .userId(userId)
                .clientTxnId(request.getClientTxnId())
                .deviceId(request.getDeviceId())
                .status(OfflineSalesStatus.DRAFT)
                .offlineSaleNo(offlineSaleNo)
                .requestPayloadJson(requestPayloadJson)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            OfflineSalesQueue saved = offlineSalesQueueRepository.save(queuedSale);
            log.info("[OfflineQueue] Enqueued sale: clientTxnId={}, offlineSaleNo={}, shop={}",
                request.getClientTxnId(), offlineSaleNo, shopId);

            return toPublicResponse(saved);
        } catch (Exception e) {
            log.error("[OfflineQueue] Failed to enqueue sale", e);
            throw new RuntimeException("Failed to enqueue offline sale: " + e.getMessage(), e);
        }
    }

    /**
     * Get status of a specific queued sale
     */
    @Transactional(readOnly = true)
    public OfflineSalesQueueResponse getSaleStatus(String clientTxnId) {
        OfflineSalesQueue sale = offlineSalesQueueRepository
            .findByClientTxnId(clientTxnId)
            .orElseThrow(() -> new RuntimeException("Sale not found: " + clientTxnId));

        return toPublicResponse(sale);
    }

    /**
     * Update sale status after processing
     */
    @Transactional
    public void updateSaleStatus(Long queueId, OfflineSalesStatus newStatus,
                                 Long saleId, String invoiceNumber, String invoiceSignedUrl,
                                 String errorCode, String errorMessage) {
        OfflineSalesQueue queue = offlineSalesQueueRepository.findById(queueId)
            .orElseThrow(() -> new RuntimeException("Queue record not found: " + queueId));

        queue.setStatus(newStatus);
        queue.setUpdatedAt(LocalDateTime.now());

        if (saleId != null) {
            queue.setSaleId(saleId);
        }
        if (invoiceNumber != null) {
            queue.setInvoiceNumber(invoiceNumber);
        }
        if (invoiceSignedUrl != null) {
            queue.setInvoiceSignedUrl(invoiceSignedUrl);
        }
        if (errorCode != null) {
            queue.setErrorCode(errorCode);
        }
        if (errorMessage != null) {
            queue.setErrorMessage(errorMessage);
        }

        if (newStatus == OfflineSalesStatus.COMPLETED || newStatus == OfflineSalesStatus.CONFLICT) {
            queue.setSyncedAt(LocalDateTime.now());
        }

        offlineSalesQueueRepository.save(queue);
        log.info("[OfflineQueue] Updated sale {} to status: {}", queueId, newStatus);
    }

    /**
     * Increment retry count
     */
    @Transactional
    public void incrementRetryCount(Long queueId) {
        OfflineSalesQueue queue = offlineSalesQueueRepository.findById(queueId)
            .orElseThrow(() -> new RuntimeException("Queue record not found: " + queueId));

        queue.setRetryCount((queue.getRetryCount() != null ? queue.getRetryCount() : 0) + 1);
        queue.setUpdatedAt(LocalDateTime.now());
        offlineSalesQueueRepository.save(queue);
    }

    /**
     * Get pending count for UI badge
     */
    @Transactional(readOnly = true)
    public long getPendingCount(Long shopId) {
        return offlineSalesQueueRepository.countPendingByShopId(shopId,
            List.of(OfflineSalesStatus.DRAFT, OfflineSalesStatus.PENDING, OfflineSalesStatus.FAILED));
    }

    /**
     * Clean up old completed records (older than 7 days)
     */
    @Transactional
    public int cleanupOldRecords(Long shopId, int daysRetention) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysRetention);
        List<OfflineSalesQueue> oldRecords = offlineSalesQueueRepository
            .findOldCompletedSales(shopId, OfflineSalesStatus.COMPLETED, cutoffDate);

        if (!oldRecords.isEmpty()) {
            offlineSalesQueueRepository.deleteAll(oldRecords);
            log.info("[OfflineQueue] Cleaned up {} old completed records for shop {}",
                oldRecords.size(), shopId);
        }

        return oldRecords.size();
    }

    /**
     * LOW-20: Clean up exhausted FAILED records across all shops.
     * Records that have exceeded max-retries AND are older than daysRetention are deleted
     * since manual review at that point is highly unlikely.
     */
    @Transactional
    public int cleanupExhaustedFailedRecords(int daysRetention) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysRetention);
        List<OfflineSalesQueue> exhausted = offlineSalesQueueRepository
            .findExhaustedFailedRecords(5, cutoffDate); // 5 = default max-retries
        if (!exhausted.isEmpty()) {
            offlineSalesQueueRepository.deleteAll(exhausted);
            log.info("[OfflineQueue] Cleaned up {} exhausted FAILED records", exhausted.size());
        }
        return exhausted.size();
    }

    /**
     * Get conflicted sales for admin review
     */
    @Transactional(readOnly = true)
    public List<OfflineSalesQueue> getConflictedSales(Long shopId) {
        return offlineSalesQueueRepository
            .findByShopIdAndStatusOrderByCreatedAtDesc(shopId, OfflineSalesStatus.CONFLICT);
    }

    /**
     * Reset a failed/conflict sale back to DRAFT so the processor picks it up.
     * PENDING would work too but the processor explicitly queries DRAFT, PENDING, and retriable FAILED —
     * using DRAFT is the clearest signal that this record is fresh again.
     */
    @Transactional
    public void resetToRetryable(Long queueId) {
        OfflineSalesQueue queue = offlineSalesQueueRepository.findById(queueId)
            .orElseThrow(() -> new RuntimeException("Queue record not found: " + queueId));

        queue.setStatus(OfflineSalesStatus.DRAFT);
        queue.setErrorCode(null);
        queue.setErrorMessage(null);
        queue.setUpdatedAt(LocalDateTime.now());
        offlineSalesQueueRepository.save(queue);
        log.info("[OfflineQueue] Reset queue {} to DRAFT for retry", queueId);
    }

    /**
     * Mark a sale as completed without re-processing (manual override)
     */
    @Transactional
    public void markCompleted(Long queueId, Long saleId) {
        OfflineSalesQueue queue = offlineSalesQueueRepository.findById(queueId)
            .orElseThrow(() -> new RuntimeException("Queue record not found: " + queueId));

        queue.setStatus(OfflineSalesStatus.COMPLETED);
        queue.setSaleId(saleId);
        queue.setSyncedAt(LocalDateTime.now());
        queue.setUpdatedAt(LocalDateTime.now());
        queue.setErrorCode(null);
        queue.setErrorMessage(null);
        offlineSalesQueueRepository.save(queue);
        log.info("[OfflineQueue] Queue {} marked as COMPLETED (manual override)", queueId);
    }

    /**
     * Get all pending/failed/draft sales for a shop (for UI list endpoint)
     * Defaults to DRAFT + PENDING + FAILED (everything still in the queue).
     */
    @Transactional(readOnly = true)
    public List<OfflineSalesQueue> getPendingSales(Long shopId, List<OfflineSalesStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            statuses = List.of(
                OfflineSalesStatus.DRAFT,
                OfflineSalesStatus.PENDING,
                OfflineSalesStatus.FAILED
            );
        }
        return offlineSalesQueueRepository.findByShopIdAndStatusIn(shopId, statuses);
    }

    // ═══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════

    private void validateEnqueueRequest(Long shopId, String userId, OfflineSalesQueueRequest request) {
        if (shopId == null || shopId <= 0) {
            throw new IllegalArgumentException("Invalid shopId");
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid userId");
        }
        if (request == null || request.getClientTxnId() == null || request.getClientTxnId().trim().isEmpty()) {
            throw new IllegalArgumentException("clientTxnId is required");
        }
        try {
            String payloadJson = objectMapper.writeValueAsString(request);
            if (payloadJson.length() > 1000000) {
                throw new IllegalArgumentException("Request payload too large (max 1MB)");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid request payload: " + e.getMessage());
        }
    }

    /**
     * Generate temporary offline sale number.
     * Format: DRAFT-{shopId}-{yyyyMMddHHmmssSSS}-{rand4}
     * The millisecond precision + random suffix prevents same-second collisions (M-5).
     */
    private String generateOfflineSaleNo(Long shopId) {
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String rand = String.format("%04d", (int)(Math.random() * 10000));
        return String.format("DRAFT-%d-%s-%s", shopId, timestamp, rand);
    }

    /**
     * Convert entity to response DTO — public so the controller can delegate to a single mapper.
     * Extracts totalAmount and customerName from the stored JSON payload for drawer display.
     */
    public OfflineSalesQueueResponse toPublicResponse(OfflineSalesQueue entity) {
        if (entity == null) return null;

        java.math.BigDecimal totalAmount = null;
        String customerName = null;
        if (entity.getRequestPayloadJson() != null) {
            try {
                com.fasterxml.jackson.databind.JsonNode node =
                    objectMapper.readTree(entity.getRequestPayloadJson());
                if (!node.path("totalAmount").isMissingNode()) {
                    totalAmount = new java.math.BigDecimal(node.path("totalAmount").asText("0"));
                }
                if (!node.path("customerName").isMissingNode()) {
                    customerName = node.path("customerName").asText(null);
                }
            } catch (Exception ignored) {
                // Non-fatal — drawer shows amount as null rather than crashing
            }
        }

        return OfflineSalesQueueResponse.builder()
            .id(entity.getId())
            .clientTxnId(entity.getClientTxnId())
            .status(entity.getStatus())
            .offlineSaleNo(entity.getOfflineSaleNo())
            .saleId(entity.getSaleId())
            .invoiceNumber(entity.getInvoiceNumber())
            .invoiceSignedUrl(entity.getInvoiceSignedUrl())
            .errorCode(entity.getErrorCode())
            .errorMessage(entity.getErrorMessage())
            .totalAmount(totalAmount)
            .customerName(customerName)
            .retryCount(entity.getRetryCount())
            .syncedAt(entity.getSyncedAt())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
