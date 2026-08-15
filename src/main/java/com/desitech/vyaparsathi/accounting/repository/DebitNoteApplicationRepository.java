package com.desitech.vyaparsathi.accounting.repository;

import com.desitech.vyaparsathi.accounting.entity.DebitNoteApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface DebitNoteApplicationRepository extends JpaRepository<DebitNoteApplication, Long> {

    List<DebitNoteApplication> findByDebitNoteIdOrderByAppliedAtDesc(Long debitNoteId);

    @Query("SELECT COALESCE(SUM(a.appliedAmount), 0) FROM DebitNoteApplication a " +
            "WHERE a.debitNoteId = :debitNoteId AND a.reversed = false")
    BigDecimal sumAppliedForDebitNote(@Param("debitNoteId") Long debitNoteId);
}
