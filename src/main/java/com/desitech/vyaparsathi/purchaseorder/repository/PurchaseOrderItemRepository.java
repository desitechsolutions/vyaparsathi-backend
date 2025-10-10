package com.desitech.vyaparsathi.purchaseorder.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;

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
     * Calculates the total quantity of items that are part of a pending purchase order
     * (i.e., not yet 'RECEIVED' or 'CANCELLED') for a given list of item variant IDs.
     *
     * @param variantIds A list of item variant IDs to check.
     * @return A list of OnOrderQuantity projections.
     */
    @Query("SELECT poi.itemVariant.id as variantId, SUM(poi.quantity) as totalOnOrder " +
            "FROM PurchaseOrderItem poi " +
            "WHERE poi.itemVariant.id IN :variantIds " +
            "AND poi.purchaseOrder.status NOT IN ('RECEIVED', 'CANCELLED') " +
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

}