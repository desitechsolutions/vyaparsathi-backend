package com.desitech.vyaparsathi.receiving.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.receiving.dto.ReceivingQtySummary;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReceivingRepository extends BaseRepository<Receiving, Long> {
    boolean existsByPurchaseOrder(PurchaseOrder po);

    @Query("SELECT COUNT(r) FROM Receiving r WHERE r.purchaseOrder.supplier.id = :supplierId AND r.shop.id = :shopId")
    long countBySupplierIdAndShopId(@Param("supplierId") Long supplierId, @Param("shopId") Long shopId);

    @Query("SELECT new com.desitech.vyaparsathi.receiving.dto.ReceivingQtySummary(" +
            "COALESCE(SUM(ri.receivedQty),0), " +
            "COALESCE(SUM(ri.damagedQty),0), " +
            "COALESCE(SUM(ri.rejectedQty),0)) " +
            "FROM Receiving r JOIN r.items ri " +
            "WHERE ri.purchaseOrderItem.id = :poItemId AND r.shop.id = :shopId")
    ReceivingQtySummary getQtySummaryForPOItem(@Param("poItemId") Long poItemId, @Param("shopId") Long shopId);
   /* @Query("SELECT COALESCE(SUM(ri.receivedQty),0) FROM Receiving r JOIN r.items ri WHERE ri.purchaseOrderItem.id = :poItemId")
    int sumReceivedQtyForPOItem(Long poItemId);

    @Query("SELECT COALESCE(SUM(ri.damagedQty),0) FROM Receiving r JOIN r.items ri WHERE ri.purchaseOrderItem.id = :poItemId")
    int sumDamagedQtyForPOItem(Long poItemId);

    @Query("SELECT COALESCE(SUM(ri.rejectedQty),0) FROM Receiving r JOIN r.items ri WHERE ri.purchaseOrderItem.id = :poItemId")
    int sumRejectedQtyForPOItem(Long poItemId);
*/
    // Added for pagination in service/controller
    @EntityGraph(attributePaths = {
            "purchaseOrder",
            "purchaseOrder.supplier",
            "items",
            "items.purchaseOrderItem"
    })
    Page<Receiving> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {
            "purchaseOrder",
            "purchaseOrder.supplier",
            "items",
            "items.purchaseOrderItem"
    })
    @Query("SELECT r FROM Receiving r WHERE r.shop.id = :shopId ORDER BY r.receivedAt DESC")
    List<Receiving> findAllByShopId(@Param("shopId") Long shopId);

    @EntityGraph(attributePaths = {
            "purchaseOrder",
            "purchaseOrder.supplier",
            "items",
            "items.purchaseOrderItem"
    })
    List<Receiving> findAllByPurchaseOrderId(Long poItemId);

    @EntityGraph(attributePaths = {
            "purchaseOrder",
            "purchaseOrder.supplier",
            "items",
            "items.purchaseOrderItem"
    })
    @Query("SELECT r FROM Receiving r WHERE r.purchaseOrder.poNumber = :poNumber AND r.shop.id = :shopId")
    List<Receiving> findAllByPoNumber(@Param("poNumber") String poNumber, @Param("shopId") Long shopId);

    @EntityGraph(attributePaths = {
            "purchaseOrder",
            "purchaseOrder.supplier",
            "items",
            "items.purchaseOrderItem",
            "items.purchaseOrderItem.itemVariant",
            "items.purchaseOrderItem.itemVariant.item"
    })
    @Query("SELECT r FROM Receiving r WHERE r.receivedAt >= :start AND r.receivedAt <= :end ORDER BY r.receivedAt ASC")
    List<Receiving> findByReceivedAtBetween(
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end
    );
}