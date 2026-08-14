package com.desitech.vyaparsathi.purchaseorder.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderItemRepository extends BaseRepository<PurchaseOrderItem, Long> {

    // This is your existing method
    @Query("SELECT poi FROM PurchaseOrderItem poi WHERE poi.itemVariant.id = :itemVariantId")
    List<PurchaseOrderItem> findByItemVariantId(@Param("itemVariantId") Long itemVariantId);


    // --- START: ADD THIS NEW CODE ---

    /**
     * A projection interface to efficiently fetch only the variant ID and the total quantity on order.
     */
    interface OnOrderQuantity {
        Long getVariantId();
        BigDecimal getTotalOnOrder();
    }

    /**
     * Truly on-order quantity — ordered minus already-received — for POs
     * that are not yet closed (RECEIVED) or aborted (CANCELLED).
     *
     * <p>V81 bug fix: the previous version summed {@code poi.quantity} for
     * all non-terminal POs, which double-counted the already-received
     * portion of a PARTIALLY_RECEIVED PO (that quantity was in stock AND
     * still counted as on-order). We now subtract {@code received_quantity},
     * clamped at zero so a receiving overage doesn't turn on-order negative.
     * A missing DB value (older rows before the migration ran) reads as 0
     * via COALESCE so the calculation stays safe.
     *
     * <p>Legacy DRAFT rows are also excluded — a draft isn't a commitment.
     */
    @Query("SELECT poi.itemVariant.id as variantId, " +
            "SUM(CASE WHEN poi.quantity - COALESCE(poi.receivedQuantity, 0) > 0 " +
            "         THEN poi.quantity - COALESCE(poi.receivedQuantity, 0) " +
            "         ELSE 0 END) as totalOnOrder " +
            "FROM PurchaseOrderItem poi " +
            "WHERE poi.itemVariant.id IN :variantIds " +
            "AND poi.purchaseOrder.status NOT IN ('RECEIVED', 'CANCELLED', 'DRAFT') " +
            "GROUP BY poi.itemVariant.id")
    List<OnOrderQuantity> findOnOrderQuantitiesByItemVariantIds(@Param("variantIds") List<Long> variantIds);

    interface LastSupplierInfo {
        Long getVariantId();
        String getSupplierName();
        Long getSupplierId();
    }

    @Query(value = "WITH RankedOrders AS (" +
            "    SELECT " +
            "        poi.item_variant_id, " +
            "        s.name as supplier_name, " +
            "        s.id as supplier_id, " +
            "        ROW_NUMBER() OVER(PARTITION BY poi.item_variant_id ORDER BY po.order_date DESC) as rn " +
            "    FROM purchase_order_item poi " +
            "    JOIN purchase_order po ON poi.purchase_order_id = po.id " +
            "    JOIN supplier s ON po.supplier_id = s.id " +
            "    WHERE poi.item_variant_id IN :variantIds" +
            ") " +
            "SELECT " +
            "    ro.item_variant_id as variantId, " +
            "    ro.supplier_name as supplierName, " +
            "    ro.supplier_id as supplierId " +
            "FROM RankedOrders ro " +
            "WHERE ro.rn = 1", nativeQuery = true)
    List<LastSupplierInfo> findLastSuppliersByVariantIds(@Param("variantIds") List<Long> variantIds);
    Optional<PurchaseOrderItem> findTopByItemVariantId(Long itemVariantId);
}