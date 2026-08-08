package com.desitech.vyaparsathi.purchasereturn.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.purchasereturn.entity.PurchaseReturn;
import com.desitech.vyaparsathi.purchasereturn.enums.PurchaseReturnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PurchaseReturnRepository extends BaseRepository<PurchaseReturn, Long> {

    @EntityGraph(attributePaths = {"supplier", "purchaseOrder", "receiving", "items", "items.itemVariant"})
    Page<PurchaseReturn> findByShopId(Long shopId, Pageable pageable);

    @EntityGraph(attributePaths = {"supplier", "purchaseOrder", "receiving", "items", "items.itemVariant"})
    Page<PurchaseReturn> findBySupplierIdAndShopId(Long supplierId, Long shopId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(pri.quantity), 0) FROM PurchaseReturn pr JOIN pr.items pri " +
           "WHERE pr.receiving.id = :receivingId AND pri.itemVariant.id = :variantId " +
           "AND (:batchNumber IS NULL OR pri.batchNumber = :batchNumber) " +
           "AND pr.status = 'APPROVED' AND pr.shop.id = :shopId")
    int sumAlreadyReturnedQty(@Param("receivingId") Long receivingId,
                              @Param("variantId") Long variantId,
                              @Param("batchNumber") String batchNumber,
                              @Param("shopId") Long shopId);

    List<PurchaseReturn> findBySupplierIdAndStatusAndShopIdAndReturnDateBetween(
            Long supplierId, PurchaseReturnStatus status, Long shopId, LocalDateTime startDate, LocalDateTime endDate);

    List<PurchaseReturn> findBySupplierIdAndStatusAndShopIdAndReturnDateBefore(
            Long supplierId, PurchaseReturnStatus status, Long shopId, LocalDateTime date);
}
