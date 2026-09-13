package com.desitech.vyaparsathi.sales.scheduler;

import com.desitech.vyaparsathi.sales.enums.OfflineSalesStatus;
import com.desitech.vyaparsathi.sales.repository.OfflineSalesQueueRepository;
import com.desitech.vyaparsathi.sales.service.OfflineSalesProcessorService;
import com.desitech.vyaparsathi.sales.service.OfflineSalesQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Offline Sales Scheduler
 *
 * Background job to periodically process queued offline sales.
 * Only iterates shops that actually have pending records — avoids a full
 * shop-table scan on every cycle.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfflineSalesScheduler {

    private final OfflineSalesProcessorService offlineSalesProcessorService;
    private final OfflineSalesQueueService offlineSalesQueueService;
    private final OfflineSalesQueueRepository offlineSalesQueueRepository;

    @Value("${offline.sales.retention-days:7}")
    private int retentionDays;

    private static final List<OfflineSalesStatus> PROCESSABLE_STATUSES =
        List.of(OfflineSalesStatus.DRAFT, OfflineSalesStatus.PENDING, OfflineSalesStatus.FAILED);

    /** Process queued offline sales every 5 minutes (fallback if frontend sync fails). */
    @Scheduled(fixedRate = 300000)
    public void processQueuedSalesScheduled() {
        log.debug("[OfflineSalesScheduler] Running scheduled processing job");

        try {
            List<Long> shopIds = offlineSalesQueueRepository
                .findDistinctShopIdsWithStatus(PROCESSABLE_STATUSES);

            if (shopIds.isEmpty()) {
                log.debug("[OfflineSalesScheduler] No shops with pending sales");
                return;
            }

            log.debug("[OfflineSalesScheduler] Processing queued sales for {} shop(s)", shopIds.size());

            for (Long shopId : shopIds) {
                try {
                    offlineSalesProcessorService.processQueuedSales(shopId);
                } catch (Exception e) {
                    log.error("[OfflineSalesScheduler] Error processing sales for shop {}", shopId, e);
                }
            }

            log.debug("[OfflineSalesScheduler] Scheduled processing cycle completed");
        } catch (Exception e) {
            log.error("[OfflineSalesScheduler] Critical error during scheduled processing", e);
        }
    }

    /** Cleanup old completed and exhausted-failed records daily at 2 AM. */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldRecords() {
        log.info("[OfflineSalesScheduler] Running cleanup job (retention={} days)", retentionDays);

        try {
            // 1. Clean old COMPLETED records
            List<Long> completedShopIds = offlineSalesQueueRepository
                .findDistinctShopIdsWithStatus(List.of(OfflineSalesStatus.COMPLETED));

            int totalCleaned = 0;

            for (Long shopId : completedShopIds) {
                try {
                    int cleaned = offlineSalesQueueService.cleanupOldRecords(shopId, retentionDays);
                    totalCleaned += cleaned;
                } catch (Exception e) {
                    log.error("[OfflineSalesScheduler] Error cleaning up completed records for shop {}", shopId, e);
                }
            }

            // 2. LOW-20: Also clean exhausted FAILED records that are past retention
            //    (retryCount >= max-retries and old enough that manual review is unlikely)
            try {
                int exhaustedCleaned = offlineSalesQueueService.cleanupExhaustedFailedRecords(retentionDays);
                totalCleaned += exhaustedCleaned;
                if (exhaustedCleaned > 0) {
                    log.info("[OfflineSalesScheduler] Cleaned {} exhausted FAILED record(s)", exhaustedCleaned);
                }
            } catch (Exception e) {
                log.error("[OfflineSalesScheduler] Error cleaning exhausted FAILED records", e);
            }

            log.info("[OfflineSalesScheduler] Cleanup job completed. Total records removed: {}", totalCleaned);
        } catch (Exception e) {
            log.error("[OfflineSalesScheduler] Error during cleanup", e);
        }
    }
}
