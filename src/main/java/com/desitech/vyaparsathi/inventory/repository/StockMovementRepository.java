package com.desitech.vyaparsathi.inventory.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.inventory.StockMovementType;
import com.desitech.vyaparsathi.inventory.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

}