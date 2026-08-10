package com.desitech.vyaparsathi.sales.repository;

import com.desitech.vyaparsathi.payment.enums.PaymentStatus;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.enums.SaleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    @Query("SELECT s FROM Sale s JOIN FETCH s.customer WHERE s.customer.id = :customerId")
    Page<Sale> findByCustomerId(@Param("customerId") Long customerId, Pageable pageable);
    @Query("SELECT s FROM Sale s JOIN FETCH s.customer WHERE s.customer.id = :customerId")
    List<Sale> findAllByCustomerId(@Param("customerId") Long customerId);
    @EntityGraph(attributePaths = {"saleItems", "customer"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT s FROM Sale s WHERE (:startDate IS NULL OR s.date >= :startDate) AND (:endDate IS NULL OR s.date <= :endDate) ORDER BY s.date DESC")
    List<Sale> findByDateBetween(@Param("startDate") LocalDateTime start, @Param("endDate") LocalDateTime end);

    @EntityGraph(attributePaths = {"saleItems", "customer"}, type = EntityGraph.EntityGraphType.LOAD)
    List<Sale> findAll();

    @EntityGraph(attributePaths = {"saleItems", "customer"}, type = EntityGraph.EntityGraphType.LOAD)
    Sale findByInvoiceNo(String invoiceNo);

    /**
     * NEW: Finds the most recent sale for a given customer.
     * This is used by the AnalyticsService to predict customer churn.
     */
    Optional<Sale> findTopByCustomerIdOrderByDateDesc(Long customerId);

    @Query("SELECT s.customer.id, MAX(s.date), SUM(s.totalAmount) FROM Sale s GROUP BY s.customer.id")
    List<Object[]> getCustomerPurchaseSummaries();

    @Query("SELECT s.customer.id, MAX(s.date), SUM(s.totalAmount) FROM Sale s WHERE s.shop.id = :shopId GROUP BY s.customer.id")
    List<Object[]> getCustomerPurchaseSummariesByShop(@Param("shopId") Long shopId);

    @Query("SELECT s FROM Sale s WHERE s.date >= :start AND s.date <= :end")
    List<Sale> findSalesForAnalytics(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @EntityGraph(attributePaths = {"saleItems", "customer"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT s FROM Sale s WHERE s.shop.id = :shopId AND s.date >= :start AND s.date <= :end AND s.status <> 'CANCELLED'")
    List<Sale> findSalesForAnalyticsByShop(@Param("shopId") Long shopId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @EntityGraph(attributePaths = {"saleItems", "customer"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT s FROM Sale s WHERE s.shop.id = :shopId AND s.status <> 'CANCELLED'")
    List<Sale> findAllByShopId(@Param("shopId") Long shopId);

    @EntityGraph(attributePaths = {"saleItems", "customer"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT s FROM Sale s WHERE s.shop.id = :shopId AND s.status <> 'CANCELLED' ORDER BY s.date DESC")
    Page<Sale> findAllByShopId(@Param("shopId") Long shopId, Pageable pageable);

    @EntityGraph(attributePaths = {"saleItems", "customer"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT s FROM Sale s WHERE s.shop.id = :shopId AND s.date >= :start AND s.date <= :end AND s.status <> 'CANCELLED' ORDER BY s.date ASC")
    List<Sale> findAllByShopIdAndDateBetween(@Param("shopId") Long shopId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Sale> findByCustomerIdAndPaymentStatusInOrderByIdAsc(
            Long customerId,
            List<PaymentStatus> statuses
    );

    List<Sale> findByStatus(SaleStatus status);
    Page<Sale> findByCustomerIdAndStatus(Long customerId, SaleStatus status, Pageable pageable);

    @Query("SELECT s FROM Sale s WHERE s.invoiceNo LIKE %:q%")
    List<Sale> searchByInvoicePartial(@Param("q") String q);

    @Query("SELECT COUNT(s) FROM Sale s WHERE s.shop.id = :shopId AND s.date >= :startDate AND s.status <> com.desitech.vyaparsathi.sales.enums.SaleStatus.DRAFT")
    long countMonthlySalesByShop(@Param("shopId") Long shopId, @Param("startDate") LocalDateTime startDate);
}