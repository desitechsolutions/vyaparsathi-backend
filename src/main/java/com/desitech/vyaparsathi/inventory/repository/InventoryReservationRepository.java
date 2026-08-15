package com.desitech.vyaparsathi.inventory.repository;

import com.desitech.vyaparsathi.inventory.entity.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    List<InventoryReservation> findByItemVariantIdAndStatus(Long itemVariantId, String status);

    List<InventoryReservation> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    @Query("SELECT COALESCE(SUM(r.quantity), 0) FROM InventoryReservation r " +
            "WHERE r.itemVariantId = :variantId AND r.status = 'ACTIVE'")
    BigDecimal sumActiveReservations(@Param("variantId") Long variantId);

    List<InventoryReservation> findByStatusAndExpiresAtBefore(String status, LocalDateTime cutoff);
}
