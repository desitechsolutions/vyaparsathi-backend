package com.desitech.vyaparsathi.accounting.repository;

import com.desitech.vyaparsathi.accounting.entity.DebitNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DebitNoteRepository extends JpaRepository<DebitNote, Long> {
    Page<DebitNote> findAllByShopId(Long shopId, Pageable pageable);
    Page<DebitNote> findByShopIdAndSupplierId(Long shopId, Long supplierId, Pageable pageable);
}
