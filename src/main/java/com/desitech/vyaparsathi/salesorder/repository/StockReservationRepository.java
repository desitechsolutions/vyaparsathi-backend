package com.desitech.vyaparsathi.salesorder.repository;

import com.desitech.vyaparsathi.salesorder.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    List<StockReservation> findBySalesOrder_Id(Long salesOrderId);

    List<StockReservation> findBySalesOrderItem_Id(Long salesOrderItemId);

    /**
     * Total quantity currently reserved for a given item variant across all
     * active sales orders in the given shop. Consumers use this to project
     * {@code available_stock = current_stock - Σ reserved_qty}.
     */
    @Query("SELECT COALESCE(SUM(r.reservedQty), 0) FROM StockReservation r " +
           "WHERE r.itemVariant.id = :itemVariantId AND r.shop.id = :shopId")
    BigDecimal sumReservedByVariant(@Param("itemVariantId") Long itemVariantId,
                                     @Param("shopId") Long shopId);

    void deleteBySalesOrder_Id(Long salesOrderId);
}
