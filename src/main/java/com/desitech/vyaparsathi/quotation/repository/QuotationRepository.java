package com.desitech.vyaparsathi.quotation.repository;

import com.desitech.vyaparsathi.quotation.entity.Quotation;
import com.desitech.vyaparsathi.quotation.enums.QuotationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    Page<Quotation> findAllByShopId(Long shopId, Pageable pageable);

    Page<Quotation> findByShopIdAndStatus(Long shopId, QuotationStatus status, Pageable pageable);

    Page<Quotation> findByShopIdAndCustomer_Id(Long shopId, Long customerId, Pageable pageable);

    /**
     * Quotations that are past their expiry date and still in a non-terminal
     * pre-conversion status. Used by the scheduled expiry job.
     */
    @Query("SELECT q FROM Quotation q " +
           "WHERE q.expiryDate IS NOT NULL " +
           "  AND q.expiryDate < :today " +
           "  AND q.status IN ('DRAFT','SENT','ACCEPTED')")
    List<Quotation> findExpirable(@Param("today") LocalDate today);

    /**
     * Bulk-flip expired quotations. Returns the number of rows updated.
     * Runs as its own {@code @Modifying} query so no per-row entity load happens.
     */
    @Modifying
    @Query("UPDATE Quotation q SET q.status = 'EXPIRED' " +
           "WHERE q.expiryDate IS NOT NULL " +
           "  AND q.expiryDate < :today " +
           "  AND q.status IN ('DRAFT','SENT','ACCEPTED')")
    int expireStaleQuotations(@Param("today") LocalDate today);
}
