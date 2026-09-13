package com.desitech.vyaparsathi.sales.service;

import com.desitech.vyaparsathi.sales.dto.OfflineSalesQueueRequest;
import com.desitech.vyaparsathi.sales.entity.OfflineSalesQueue;
import com.desitech.vyaparsathi.sales.enums.OfflineSalesStatus;
import com.desitech.vyaparsathi.sales.repository.OfflineSalesQueueRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Async processor for queued offline sales.
 *
 * Processes DRAFT, PENDING, and retriable FAILED records.
 * Each sale runs in its own REQUIRES_NEW transaction so a failure cannot
 * leave a sibling sale in an inconsistent state.
 *
 * H-1 (stuck-PROCESSING recovery): on startup any record that has been in
 * PROCESSING for longer than {@code offline.queue.stale-minutes} minutes is
 * reset to FAILED so the next scheduler cycle can pick it up.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineSalesProcessorService {

    private final OfflineSalesQueueRepository offlineSalesQueueRepository;
    private final SaleService saleService;
    private final ObjectMapper objectMapper;

    @Value("${offline.queue.max-retries:5}")
    private int maxRetries;

    @Value("${offline.queue.stale-minutes:10}")
    private int staleMinutes;

    // Self-reference for @Transactional self-invocation (Spring proxy bypass avoidance).
    @Lazy
    @Autowired
    private OfflineSalesProcessorService self;

    // Per-shop lock: prevents two concurrent process triggers from double-processing
    // the same DRAFT records. compareAndSet(false, true) is atomic, so only one thread
    // wins the race even when multiple HTTP requests arrive in rapid succession.
    private final ConcurrentHashMap<Long, AtomicBoolean> processingFlags = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════
    // STARTUP RECOVERY (H-1)
    // ═══════════════════════════════════════════════════════════════

    @EventListener(ApplicationReadyEvent.class)
    public void recoverStaleProcessingRecords() {
        LocalDateTime staleThreshold = LocalDateTime.now().minusMinutes(staleMinutes);
        List<OfflineSalesQueue> stale = offlineSalesQueueRepository.findStaleProcessingRecords(staleThreshold);
        if (!stale.isEmpty()) {
            stale.forEach(r -> {
                r.setStatus(OfflineSalesStatus.FAILED);
                r.setErrorCode("STALE_PROCESSING");
                r.setErrorMessage("Reset from PROCESSING after server restart (retryCount=" + r.getRetryCount() + ")");
                r.setUpdatedAt(LocalDateTime.now());
            });
            offlineSalesQueueRepository.saveAll(stale);
            log.warn("[OfflineSalesProcessor] Recovered {} stale PROCESSING record(s)", stale.size());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ASYNC ENTRY POINT
    // ═══════════════════════════════════════════════════════════════

    /** Process all actionable queued sales for a shop — runs asynchronously. */
    @Async("offlineProcessorExecutor")
    public void processQueuedSales(Long shopId) {
        // Guarantee TenantContext is set on this async thread regardless of who submitted the task.
        // When triggered from an HTTP request the TenantContextTaskDecorator already propagates it,
        // but when triggered from the scheduler (which has no HTTP context) the decorator captures
        // a null shopId and skips the set — leaving all createSale() calls without a shop.
        // Setting it here explicitly fixes the scheduler-triggered path. The decorator still clears
        // it in its finally block when the method returns, so no value leaks to the next task.
        com.desitech.vyaparsathi.common.configs.TenantContext.setCurrentShopId(shopId);

        AtomicBoolean flag = processingFlags.computeIfAbsent(shopId, k -> new AtomicBoolean(false));
        if (!flag.compareAndSet(false, true)) {
            log.debug("[OfflineSalesProcessor] Shop {} already processing, skipping duplicate trigger", shopId);
            return;
        }

        log.info("[OfflineSalesProcessor] Starting processing for shop: {}", shopId);
        try {
            // DRAFT and PENDING are unconditionally retriable.
            List<OfflineSalesQueue> draftsAndPending = offlineSalesQueueRepository.findByShopIdAndStatusIn(
                shopId, List.of(OfflineSalesStatus.DRAFT, OfflineSalesStatus.PENDING)
            );

            // FAILED: only retry up to maxRetries (H-5).
            List<OfflineSalesQueue> retriableFailed = offlineSalesQueueRepository.findRetriableSales(
                shopId, OfflineSalesStatus.FAILED, maxRetries
            );

            List<OfflineSalesQueue> toProcess = new ArrayList<>(draftsAndPending);
            toProcess.addAll(retriableFailed);

            if (toProcess.isEmpty()) {
                log.info("[OfflineSalesProcessor] No actionable sales for shop: {}", shopId);
                return;
            }

            log.info("[OfflineSalesProcessor] Processing {} queued sale(s) for shop: {}", toProcess.size(), shopId);

            int successCount = 0;
            int failureCount = 0;

            for (OfflineSalesQueue queuedSale : toProcess) {
                // ★ HIGH-9: Skip records already being processed (soft distributed lock).
                // If this pod is processing and another pod queries the same batch,
                // the PROCESSING status prevents double-execution.
                if (queuedSale.getStatus() == OfflineSalesStatus.PROCESSING) {
                    log.debug("[OfflineSalesProcessor] Skipping already-PROCESSING sale: {}", queuedSale.getClientTxnId());
                    continue;
                }
                try {
                    // Delegate via Spring proxy so @Transactional(REQUIRES_NEW) is honoured (M-3).
                    self.processSingleSale(shopId, queuedSale);
                    successCount++;
                } catch (Exception e) {
                    log.error("[OfflineSalesProcessor] Failed to process sale: clientTxnId={}",
                        queuedSale.getClientTxnId(), e);
                    failureCount++;
                    handleProcessingFailure(queuedSale, e);
                }
            }

            log.info("[OfflineSalesProcessor] Done. success={}, failed={}", successCount, failureCount);

        } catch (Exception e) {
            log.error("[OfflineSalesProcessor] Critical error during processing for shop: {}", shopId, e);
        } finally {
            flag.set(false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PER-SALE PROCESSING (M-3: own transaction)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Processes a single queued sale in its own database transaction.
     * A rollback only affects this sale; sibling records are unaffected.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleSale(Long shopId, OfflineSalesQueue queuedSale) {
        log.info("[OfflineSalesProcessor] Processing sale: clientTxnId={}", queuedSale.getClientTxnId());

        // Mark as in-flight so duplicate scheduler cycles skip it.
        queuedSale.setStatus(OfflineSalesStatus.PROCESSING);
        queuedSale.setUpdatedAt(LocalDateTime.now());
        offlineSalesQueueRepository.save(queuedSale);

        try {
            OfflineSalesQueueRequest request = objectMapper.readValue(
                queuedSale.getRequestPayloadJson(), OfflineSalesQueueRequest.class
            );

            // ★ HIGH-5: Validate required fields before delegating to SaleService.
            // A malformed record would otherwise burn all retries with NPEs.
            validateOfflineRequest(request, queuedSale.getClientTxnId());

            com.desitech.vyaparsathi.sales.dto.SaleDto saleDto = mapRequestToSaleDto(request, queuedSale);
            com.desitech.vyaparsathi.sales.dto.SaleDto saleResponse = saleService.createSale(saleDto);

            log.info("[OfflineSalesProcessor] Sale created: id={}, invoice={}",
                saleResponse.getId(), saleResponse.getInvoiceNo());

            queuedSale.setStatus(OfflineSalesStatus.COMPLETED);
            queuedSale.setSaleId(saleResponse.getId());
            queuedSale.setInvoiceNumber(saleResponse.getInvoiceNo());
            queuedSale.setInvoiceSignedUrl(saleResponse.getSignedInvoiceUrl());
            queuedSale.setSyncedAt(LocalDateTime.now());
            queuedSale.setUpdatedAt(LocalDateTime.now());
            offlineSalesQueueRepository.save(queuedSale);

        } catch (com.desitech.vyaparsathi.sales.exception.DuplicateSaleException dse) {
            // Already processed — mark CONFLICT so it doesn’t retry.
            throw dse;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Wrap and rethrow — outer catch calls handleProcessingFailure.
            throw new RuntimeException("JSON deserialization error: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FAILURE HANDLING
    // ═══════════════════════════════════════════════════════════════

    private void handleProcessingFailure(OfflineSalesQueue queuedSale, Exception error) {
        String errorMsg = error.getMessage();
        String errorCode = "PROCESSING_ERROR";

        // ★ HIGH-6: Use typed exception instead of fragile string matching
        if (error instanceof com.desitech.vyaparsathi.sales.exception.DuplicateSaleException) {
            queuedSale.setStatus(OfflineSalesStatus.CONFLICT);
            errorCode = "DUPLICATE_SALE";
        } else if (error instanceof IllegalArgumentException) {
            // Validation failure — won’t improve on retry, mark as CONFLICT
            queuedSale.setStatus(OfflineSalesStatus.CONFLICT);
            errorCode = "VALIDATION_ERROR";
        } else {
            queuedSale.setStatus(OfflineSalesStatus.FAILED);
        }

        queuedSale.setErrorCode(errorCode);
        queuedSale.setErrorMessage(errorMsg != null
            ? errorMsg.substring(0, Math.min(1000, errorMsg.length())) : "Unknown error");
        queuedSale.setRetryCount((queuedSale.getRetryCount() != null ? queuedSale.getRetryCount() : 0) + 1);
        queuedSale.setUpdatedAt(LocalDateTime.now());

        offlineSalesQueueRepository.save(queuedSale);
        log.warn("[OfflineSalesProcessor] Sale marked as {}: clientTxnId={}, error={}",
            queuedSale.getStatus(), queuedSale.getClientTxnId(), errorCode);
    }

    // ═══════════════════════════════════════════════════════════════
    // VALIDATION (HIGH-5)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Validates that the deserialized request has the minimum fields required
     * to create a sale. Called before mapRequestToSaleDto() to avoid burning
     * retry attempts on records that will never succeed.
     *
     * On failure: throws IllegalArgumentException which handleProcessingFailure()
     * catches and classifies as CONFLICT (non-retriable).
     */
    private void validateOfflineRequest(OfflineSalesQueueRequest request, String clientTxnId) {
        if (request == null) {
            throw new IllegalArgumentException("Null request payload for: " + clientTxnId);
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("No items in offline sale: " + clientTxnId);
        }
        if (request.getTotalAmount() == null || request.getTotalAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid totalAmount in offline sale: " + clientTxnId);
        }
        for (OfflineSalesQueueRequest.SaleItemDto item : request.getItems()) {
            boolean isCustom = Boolean.TRUE.equals(item.getIsCustom());
            // Catalog items require a variantId; custom/service line items do not.
            if (!isCustom && item.getVariantId() == null) {
                throw new IllegalArgumentException("Catalog item missing variantId in offline sale: " + clientTxnId);
            }
            // Custom items require a name so the invoice is printable.
            if (isCustom && (item.getCustomItemName() == null || item.getCustomItemName().isBlank())) {
                throw new IllegalArgumentException("Custom item missing name in offline sale: " + clientTxnId);
            }
            if (item.getQty() == null || item.getQty() <= 0) {
                throw new IllegalArgumentException("Item has invalid qty in offline sale: " + clientTxnId);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // STATS
    // ═══════════════════════════════════════════════════════════════

    public ProcessingStats getStats(Long shopId) {
        long pending = offlineSalesQueueRepository.countPendingByShopId(shopId,
            List.of(OfflineSalesStatus.DRAFT, OfflineSalesStatus.PENDING));
        long failed = offlineSalesQueueRepository.countPendingByShopId(shopId,
            List.of(OfflineSalesStatus.FAILED));
        long conflicted = offlineSalesQueueRepository.countPendingByShopId(shopId,
            List.of(OfflineSalesStatus.CONFLICT));

        return ProcessingStats.builder()
            .pending(pending)
            .failed(failed)
            .conflicted(conflicted)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // MAPPING
    // ═══════════════════════════════════════════════════════════════

    private com.desitech.vyaparsathi.sales.dto.SaleDto mapRequestToSaleDto(
            OfflineSalesQueueRequest request, OfflineSalesQueue queuedSale) {

        com.desitech.vyaparsathi.sales.dto.SaleDto dto = new com.desitech.vyaparsathi.sales.dto.SaleDto();

        dto.setIdempotencyKey(queuedSale.getClientTxnId());

        // C-2 fix: don't set customer DTO when customerId is null (walk-in customer).
        // Previously CustomerDto.builder().id(null).build() was set unconditionally,
        // causing customerRepository.findById(null) to throw IllegalArgumentException.
        if (request.getCustomerId() != null) {
            dto.setCustomer(com.desitech.vyaparsathi.customer.dto.CustomerDto.builder()
                    .id(request.getCustomerId())
                    .build());
        }

        if (request.getItems() != null) {
            dto.setItems(request.getItems().stream()
                    .map(item -> {
                        boolean isCustom = Boolean.TRUE.equals(item.getIsCustom());
                        com.desitech.vyaparsathi.sales.dto.SaleItemDto saleItem =
                            com.desitech.vyaparsathi.sales.dto.SaleItemDto.builder()
                                .itemVariantId(isCustom ? null : item.getVariantId())
                                .qty(new java.math.BigDecimal(item.getQty()))
                                .unitPrice(item.getUnitPrice())
                                .discount(item.getDiscount())
                                .build();
                        // C-1 fix: map custom line item fields
                        if (isCustom) {
                            saleItem.setCustomItemName(item.getCustomItemName());
                            saleItem.setCustomDescription(item.getCustomDescription());
                            saleItem.setCustomHsnSac(item.getCustomHsnSac());
                            saleItem.setCustomUnit(item.getCustomUnit());
                            // gstRate only applies to custom items; catalog items use the variant's rate
                            if (item.getGstRate() != null) {
                                saleItem.setGstRate(item.getGstRate().intValue());
                            }
                        }
                        return saleItem;
                    })
                    .toList());
        }

        dto.setTotalAmount(request.getTotalAmount());
        dto.setDiscount(request.getDiscount());

        dto.setIsGstRequired("yes".equalsIgnoreCase(request.getIsGstRequired()));
        dto.setPlaceOfSupply(request.getPlaceOfSupply());
        dto.setSupplyType(request.getSupplyType());
        dto.setReverseCharge(request.getReverseCharge());
        dto.setBillToAddress(request.getBillToAddress());
        dto.setShipToAddress(request.getShipToAddress());
        dto.setConsigneeAddress(request.getConsigneeAddress());

        // H-3: preserve original notes; append offline annotation as a suffix.
        String userNotes = request.getSaleNotes();
        String offlineTag = "[Offline — device: " + queuedSale.getDeviceId() + "]";
        dto.setNotes(userNotes != null && !userNotes.isBlank()
            ? userNotes + " " + offlineTag : offlineTag);

        // Delivery fields mapping
        if (Boolean.TRUE.equals(request.getDeliveryRequired())) {
            com.desitech.vyaparsathi.delivery.dto.DeliveryDTO deliveryDTO =
                new com.desitech.vyaparsathi.delivery.dto.DeliveryDTO();
            deliveryDTO.setDeliveryAddress(request.getDeliveryAddress());
            if (request.getDeliveryCharge() != null) {
                deliveryDTO.setDeliveryCharge(request.getDeliveryCharge().doubleValue());
            }
            if (request.getDeliveryPaidBy() != null) {
                try {
                    deliveryDTO.setDeliveryPaidBy(
                        com.desitech.vyaparsathi.delivery.enums.DeliveryPaidBy.valueOf(
                            request.getDeliveryPaidBy().toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    deliveryDTO.setDeliveryPaidBy(com.desitech.vyaparsathi.delivery.enums.DeliveryPaidBy.CUSTOMER);
                }
            }
            deliveryDTO.setDeliveryNotes(request.getDeliveryNotes());
            dto.setDelivery(deliveryDTO);
        }

        if (request.getPaymentMethods() != null && !request.getPaymentMethods().isEmpty()) {
            java.math.BigDecimal totalPaid = java.math.BigDecimal.ZERO;
            java.util.List<com.desitech.vyaparsathi.payment.dto.PaymentDto> payments = new java.util.ArrayList<>();
            for (OfflineSalesQueueRequest.PaymentMethodDto pm : request.getPaymentMethods()) {
                if (pm.getAmount() != null && pm.getAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
                    com.desitech.vyaparsathi.payment.dto.PaymentDto p = new com.desitech.vyaparsathi.payment.dto.PaymentDto();
                    p.setAmount(pm.getAmount());
                    try {
                        p.setPaymentMethod(com.desitech.vyaparsathi.payment.enums.PaymentMethod.valueOf(pm.getMethod().toUpperCase()));
                    } catch (Exception e) {
                        p.setPaymentMethod(com.desitech.vyaparsathi.payment.enums.PaymentMethod.CASH);
                    }
                    // H-2: use the original sale timestamp, not "now" at processing time.
                    p.setPaymentDate(queuedSale.getCreatedAt());
                    payments.add(p);
                    totalPaid = totalPaid.add(pm.getAmount());
                }
            }
            dto.setPaymentDetails(payments);
            dto.setPaidAmount(totalPaid);
            if (dto.getTotalAmount() != null) {
                dto.setDueAmount(dto.getTotalAmount().subtract(totalPaid).max(java.math.BigDecimal.ZERO));
            }
        }

        return dto;
    }

    // ═══════════════════════════════════════════════════════════════
    // INNER DTO
    // ═══════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class ProcessingStats {
        private Long pending;
        private Long failed;
        private Long conflicted;
    }
}
