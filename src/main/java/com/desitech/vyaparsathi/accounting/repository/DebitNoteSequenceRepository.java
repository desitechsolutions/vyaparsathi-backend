package com.desitech.vyaparsathi.accounting.repository;

import com.desitech.vyaparsathi.accounting.entity.DebitNoteSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DebitNoteSequenceRepository extends JpaRepository<DebitNoteSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DebitNoteSequence s WHERE s.shopId = :shopId AND s.prefix = :prefix AND s.fiscalYear = :year")
    Optional<DebitNoteSequence> findByShopIdAndPrefixAndFiscalYearForUpdate(
            @Param("shopId") Long shopId,
            @Param("prefix") String prefix,
            @Param("year") short year);
}
