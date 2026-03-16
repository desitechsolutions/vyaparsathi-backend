package com.desitech.vyaparsathi.inventory.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.inventory.enums.StockMovementType;
import com.desitech.vyaparsathi.inventory.entity.StockMovement;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface StockMovementRepository extends BaseRepository<StockMovement, Long> {
    List<StockMovement> findByItemVariantIdOrderByTimestampDesc(Long itemVariantId);
    List<StockMovement> findByItemVariantIdAndTimestampBetweenOrderByTimestampDesc(
            Long itemVariantId, LocalDateTime startDate, LocalDateTime endDate);
    List<StockMovement> findByTimestampBetweenOrderByTimestampDesc(
            LocalDateTime startDate, LocalDateTime endDate);
    List<StockMovement> findByItemVariantIdAndMovementType(Long itemVariantId, StockMovementType movementType);

    // NEW: Method to get current stock for one item
    @Query("SELECT SUM(m.quantity) FROM StockMovement m WHERE m.itemVariant.id = :itemVariantId")
    BigDecimal sumQuantityByItemVariantId(@Param("itemVariantId") Long itemVariantId);

    // NEW: DTO Projection for bulk stock query
    interface StockQuantity {
        Long getVariantId();
        BigDecimal getTotalQuantity();
    }

    // NEW: Method to get current stock for multiple items efficiently
    @Query("SELECT m.itemVariant.id AS variantId, SUM(m.quantity) AS totalQuantity " +
            "FROM StockMovement m WHERE m.itemVariant.id IN :itemVariantIds " +
            "GROUP BY m.itemVariant.id")
    List<StockQuantity> findTotalQuantitiesByItemVariantIds(@Param("itemVariantIds") List<Long> itemVariantIds);

    interface LastPurchasePrice {
        Long getVariantId();
        BigDecimal getPrice();
    }

    @Query(value = "WITH RankedMovements AS (" +
            "    SELECT " +
            "        sm.item_variant_id, " +
            "        sm.cost_per_unit, " +
            "        ROW_NUMBER() OVER(PARTITION BY sm.item_variant_id ORDER BY sm.timestamp DESC) as rn " +
            "    FROM stock_movement sm " +
            "    WHERE sm.item_variant_id IN :variantIds " +
            "    AND sm.movement_type = 'ADD' " +
            "    AND sm.cost_per_unit IS NOT NULL AND sm.cost_per_unit > 0" +
            ") " +
            "SELECT " +
            "    rm.item_variant_id as variantId, " +
            "    rm.cost_per_unit as price " +
            "FROM RankedMovements rm " +
            "WHERE rm.rn = 1", nativeQuery = true)
    List<LastPurchasePrice> findLastPurchasePricesByVariantIds(@Param("variantIds") List<Long> variantIds);

    @Query("SELECT SUM(sm.quantity * sm.costPerUnit) FROM StockMovement sm " +
            "WHERE sm.itemVariant.id = :variantId AND sm.movementType = 'ADD'")
    BigDecimal sumTotalCostForAddMovements(@Param("variantId") Long variantId);

    @Query("SELECT SUM(sm.quantity) FROM StockMovement sm " +
            "WHERE sm.itemVariant.id = :variantId AND sm.movementType = 'ADD'")
    BigDecimal sumTotalQuantityForAddMovements(@Param("variantId") Long variantId);

    // Optimized single-query WAC
    @Query("SELECT COALESCE(SUM(sm.quantity * sm.costPerUnit) / NULLIF(SUM(sm.quantity), 0), 0) " +
            "FROM StockMovement sm " +
            "WHERE sm.itemVariant.id = :variantId AND sm.movementType = 'ADD' AND sm.quantity > 0")
    BigDecimal getWeightedAverageCost(@Param("variantId") Long variantId);

    interface WacProjection {
        Long getVariantId();
        BigDecimal getWac();
    }

    @Query("SELECT sm.itemVariant.id AS variantId, " +
            "COALESCE(SUM(sm.quantity * sm.costPerUnit) / NULLIF(SUM(sm.quantity), 0), 0) AS wac " +
            "FROM StockMovement sm WHERE sm.itemVariant.id IN :variantIds " +
            "AND sm.movementType = 'ADD' GROUP BY sm.itemVariant.id")
    List<WacProjection> findWacByVariantIds(@Param("variantIds") List<Long> variantIds);

    /**
     * New method to support WAC calculation by fetching multiple types (ADD, ADJUSTMENT).
     * This ensures manual stock corrections are factored into the average cost.
     */
    List<StockMovement> findByItemVariantIdAndMovementTypeIn(Long itemVariantId, Collection<StockMovementType> types);

    /**
     * Projection for batch-wise stock query.
     * Returns the net quantity per (variantId, batch, expiryDate) combination.
     */
    interface BatchStockProjection {
        Long getVariantId();
        String getBatchNumber();
        LocalDate getExpiryDate();
        BigDecimal getTotalQuantity();
        BigDecimal getWacCost();
    }

    /**
     * Returns per-batch net stock for a list of variant IDs.
     * Only batches with a positive remaining quantity are returned.
     * Used by the pharmacy batch-wise stock view.
     */
    @Query("SELECT sm.itemVariant.id AS variantId, " +
            "sm.batch AS batchNumber, " +
            "sm.expiryDate AS expiryDate, " +
            "SUM(sm.quantity) AS totalQuantity, " +
            "COALESCE(SUM(CASE WHEN sm.movementType = 'ADD' AND sm.quantity > 0 THEN sm.quantity * sm.costPerUnit ELSE 0 END) " +
            "  / NULLIF(SUM(CASE WHEN sm.movementType = 'ADD' AND sm.quantity > 0 THEN sm.quantity ELSE 0 END), 0), 0) AS wacCost " +
            "FROM StockMovement sm WHERE sm.itemVariant.id IN :variantIds " +
            "GROUP BY sm.itemVariant.id, sm.batch, sm.expiryDate " +
            "HAVING SUM(sm.quantity) > 0")
    List<BatchStockProjection> findBatchWiseStockByVariantIds(@Param("variantIds") List<Long> variantIds);
}