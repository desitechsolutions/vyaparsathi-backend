package com.desitech.vyaparsathi.accounting.repository;

import com.desitech.vyaparsathi.accounting.entity.CreditNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {
    Page<CreditNote> findAllByShopId(Long shopId, Pageable pageable);
    Page<CreditNote> findByShopIdAndCustomerId(Long shopId, Long customerId, Pageable pageable);

    /** All credit notes issued against a specific sale, most recent first. */
    List<CreditNote> findBySaleIdOrderByIdDesc(Long saleId);

    /** All credit notes for a shop dated within a range — used by GSTR-1 CDNR population. */
    List<CreditNote> findAllByShopIdAndCreditNoteDateBetween(Long shopId, java.time.LocalDate from, java.time.LocalDate to);
}
