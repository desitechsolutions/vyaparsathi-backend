package com.desitech.vyaparsathi.accounting.repository;

import com.desitech.vyaparsathi.accounting.entity.CreditNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {
    Page<CreditNote> findAllByShopId(Long shopId, Pageable pageable);
    Page<CreditNote> findByShopIdAndCustomerId(Long shopId, Long customerId, Pageable pageable);
}
