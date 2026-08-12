package com.desitech.vyaparsathi.receipt.repository;

import com.desitech.vyaparsathi.receipt.entity.PaymentReceiptSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentReceiptSequenceRepository extends JpaRepository<PaymentReceiptSequence, Long> {

    /**
     * Acquires a pessimistic write lock on the sequence row for the given
     * shop / prefix / fiscal year. Ensures no two concurrent transactions can
     * receive the same receipt number.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PaymentReceiptSequence s WHERE s.shopId = :shopId AND s.prefix = :prefix AND s.fiscalYear = :year")
    Optional<PaymentReceiptSequence> findByShopIdAndPrefixAndFiscalYearForUpdate(
            @Param("shopId") Long shopId,
            @Param("prefix") String prefix,
            @Param("year") short year);
}
