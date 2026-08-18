package com.desitech.vyaparsathi.accounting.repository;

import com.desitech.vyaparsathi.accounting.entity.CreditNoteAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditNoteAllocationRepository extends JpaRepository<CreditNoteAllocation, Long> {

    List<CreditNoteAllocation> findByCreditNoteIdOrderByAllocatedAtDesc(Long creditNoteId);

    List<CreditNoteAllocation> findByCreditNoteIdAndReversedFalse(Long creditNoteId);
}
