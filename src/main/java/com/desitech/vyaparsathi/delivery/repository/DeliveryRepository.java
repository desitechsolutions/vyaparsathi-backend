package com.desitech.vyaparsathi.delivery.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.delivery.entity.Delivery;
import com.desitech.vyaparsathi.delivery.enums.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DeliveryRepository extends BaseRepository<Delivery, Long> {

    List<Delivery> findBySaleId(Long saleId);

    List<Delivery> findBySaleIdOrderByCreatedAtDesc(Long saleId);

    /**
     * All delivery challans for a given customer. Delivery has no
     * direct FK to Customer — it links via Sale — so we walk through
     * the Sale.customer relationship. Shop scope is enforced by the
     * ShopFilterAspect against both entities.
     */
    @Query("SELECT d FROM Delivery d JOIN d.sale s WHERE s.customer.id = :customerId " +
            "ORDER BY d.createdAt DESC")
    Page<Delivery> findByCustomerIdViaSale(@Param("customerId") Long customerId, Pageable pageable);

    /**
     * Paginated, filtered list. Eager-loads {@code deliveryPerson} and
     * {@code statusHistory} via an entity graph to kill the N+1 that the
     * previous {@code findAll()} + lazy-mapper flow triggered.
     *
     * All filter params are optional — null means "any". String {@code q}
     * matches customerName / deliveryAddress / trackingNumber (case-insensitive
     * substring).
     */
    @EntityGraph(attributePaths = {"deliveryPerson", "statusHistory"})
    @Query("SELECT d FROM Delivery d " +
            "WHERE (:status IS NULL OR d.deliveryStatus = :status) " +
            "AND (:personId IS NULL OR d.deliveryPerson.id = :personId) " +
            "AND (:saleId IS NULL OR d.sale.id = :saleId) " +
            "AND (:from IS NULL OR d.createdAt >= :from) " +
            "AND (:to IS NULL OR d.createdAt <= :to) " +
            "AND (:q IS NULL OR LOWER(d.customerName) LIKE LOWER(CONCAT('%', :q, '%')) " +
                            "OR LOWER(d.deliveryAddress) LIKE LOWER(CONCAT('%', :q, '%')) " +
                            "OR LOWER(COALESCE(d.trackingNumber, '')) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Delivery> search(
            @Param("status") DeliveryStatus status,
            @Param("personId") Long personId,
            @Param("saleId") Long saleId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("q") String q,
            Pageable pageable
    );

    /**
     * All deliveries whose activity intersects the given range — used by the
     * metrics endpoint. Not paginated (metrics need the full set to compute
     * on-time %).
     *
     * Includes deliveries created in the range OR whose COD was collected in
     * the range (so a delivery created earlier but paid inside the window
     * still contributes to the COD total). Per-metric range filtering happens
     * in the service (e.g. COD sum only counts collections in-range).
     */
    @EntityGraph(attributePaths = {"deliveryPerson", "sale"})
    @Query("SELECT DISTINCT d FROM Delivery d " +
            "WHERE (d.createdAt >= :from AND d.createdAt <= :to) " +
            "   OR (d.codCollectedAt IS NOT NULL AND d.codCollectedAt >= :from AND d.codCollectedAt <= :to)")
    List<Delivery> findForMetrics(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
