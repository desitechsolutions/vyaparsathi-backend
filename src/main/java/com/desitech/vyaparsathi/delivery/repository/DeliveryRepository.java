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
     * All deliveries for a shop within a range — used by the metrics endpoint.
     * Not paginated (metrics need the full set to compute on-time %).
     */
    @EntityGraph(attributePaths = {"deliveryPerson"})
    @Query("SELECT d FROM Delivery d " +
            "WHERE d.createdAt >= :from AND d.createdAt <= :to")
    List<Delivery> findForMetrics(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
