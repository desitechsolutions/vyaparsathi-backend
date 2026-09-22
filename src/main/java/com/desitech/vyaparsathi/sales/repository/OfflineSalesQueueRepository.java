package com.desitech.vyaparsathi.sales.repository;

import com.desitech.vyaparsathi.sales.entity.OfflineSalesQueue;
import com.desitech.vyaparsathi.sales.enums.OfflineSalesStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OfflineSalesQueueRepository extends JpaRepository<OfflineSalesQueue, Long> {

    /**
     * Find offline sale by shop and client transaction ID
     * Used for idempotency check
     */
    Optional<OfflineSalesQueue> findByShopIdAndClientTxnId(Long shopId, String clientTxnId);

    /**
     * Check if offline sale already exists (for idempotency)
     */
    boolean existsByShopIdAndClientTxnId(Long shopId, String clientTxnId);

    /**
     * Get all queued sales for a shop by status
     */
    List<OfflineSalesQueue> findByShopIdAndStatus(Long shopId, OfflineSalesStatus status);

    /**
     * Get all queued sales for a user by status
     */
    List<OfflineSalesQueue> findByUserIdAndStatus(String userId, OfflineSalesStatus status);

    /**
     * Get pending and failed sales for processing
     */
    @Query("SELECT osq FROM OfflineSalesQueue osq " +
           "WHERE osq.shopId = :shopId AND osq.status IN (:statuses) " +
           "ORDER BY osq.createdAt ASC")
    List<OfflineSalesQueue> findByShopIdAndStatusIn(
        @Param("shopId") Long shopId,
        @Param("statuses") List<OfflineSalesStatus> statuses
    );

    /**
     * Get failed sales that haven't exceeded retry limit
     */
    @Query("SELECT osq FROM OfflineSalesQueue osq " +
           "WHERE osq.shopId = :shopId AND osq.status = :status AND osq.retryCount < :maxRetries " +
           "ORDER BY osq.createdAt ASC")
    List<OfflineSalesQueue> findRetriableSales(
        @Param("shopId") Long shopId,
        @Param("status") OfflineSalesStatus status,
        @Param("maxRetries") Integer maxRetries
    );

    /**
     * Get pending count for a shop (for UI badge)
     */
    @Query("SELECT COUNT(osq) FROM OfflineSalesQueue osq " +
           "WHERE osq.shopId = :shopId AND osq.status IN (:statuses)")
    Long countPendingByShopId(
        @Param("shopId") Long shopId,
        @Param("statuses") List<OfflineSalesStatus> statuses
    );

    /**
     * Find sales by device ID (for tracking)
     */
    List<OfflineSalesQueue> findByDeviceId(String deviceId);

    /**
     * Find sales by user (for audit/cleanup)
     */
    Page<OfflineSalesQueue> findByUserId(String userId, Pageable pageable);

    /**
     * Find completed sales older than specified date (for cleanup)
     */
    @Query("SELECT osq FROM OfflineSalesQueue osq " +
           "WHERE osq.shopId = :shopId AND osq.status = :status AND osq.syncedAt < :before " +
           "ORDER BY osq.syncedAt ASC")
    List<OfflineSalesQueue> findOldCompletedSales(
        @Param("shopId") Long shopId,
        @Param("status") OfflineSalesStatus status,
        @Param("before") LocalDateTime before
    );

    /**
     * Get all conflicted sales for manual review
     */
    List<OfflineSalesQueue> findByShopIdAndStatusOrderByCreatedAtDesc(
        Long shopId,
        OfflineSalesStatus status
    );

    /**
     * Find by sale ID (for linking queue record to actual sale)
     */
    Optional<OfflineSalesQueue> findBySaleId(Long saleId);

    /**
     * Find by clientTxnId only (for polling status)
     */
    Optional<OfflineSalesQueue> findByClientTxnId(String clientTxnId);

    /**
     * OFF-3 fix: shop-scoped clientTxnId lookup for the getSaleStatus endpoint.
     * Ensures the tenant check and the data fetch happen atomically in a single query,
     * eliminating the TOCTOU race and cross-tenant existence oracle in the original
     * two-query pattern.
     */
    Optional<OfflineSalesQueue> findByClientTxnIdAndShopId(String clientTxnId, Long shopId);

    /**
     * Count all queued sales by status across all shops
     * Used for monitoring/alerting
     */
    @Query("SELECT COUNT(osq) FROM OfflineSalesQueue osq WHERE osq.status = :status")
    Long countByStatus(@Param("status") OfflineSalesStatus status);

    /**
     * Distinct shop IDs that have at least one record in any of the given statuses.
     * Used by the scheduler to avoid iterating every shop.
     */
    @Query("SELECT DISTINCT osq.shopId FROM OfflineSalesQueue osq WHERE osq.status IN (:statuses)")
    List<Long> findDistinctShopIdsWithStatus(@Param("statuses") List<OfflineSalesStatus> statuses);

    /**
     * Records stuck in PROCESSING state (JVM crash recovery).
     * Returns records that have been in PROCESSING longer than the given threshold.
     */
    @Query("SELECT osq FROM OfflineSalesQueue osq " +
           "WHERE osq.status = com.desitech.vyaparsathi.sales.enums.OfflineSalesStatus.PROCESSING " +
           "AND osq.updatedAt < :staleThreshold")
    List<OfflineSalesQueue> findStaleProcessingRecords(@Param("staleThreshold") LocalDateTime staleThreshold);

    /**
     * LOW-20: Find FAILED records that have exceeded the retry limit and are old enough to clean up.
     */
    @Query("SELECT osq FROM OfflineSalesQueue osq " +
           "WHERE osq.status = com.desitech.vyaparsathi.sales.enums.OfflineSalesStatus.FAILED " +
           "AND osq.retryCount >= :maxRetries " +
           "AND osq.updatedAt < :before")
    List<OfflineSalesQueue> findExhaustedFailedRecords(
        @Param("maxRetries") int maxRetries,
        @Param("before") LocalDateTime before
    );
}
