package com.desitech.vyaparsathi.refund.repository;

import com.desitech.vyaparsathi.refund.entity.RefundSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefundSequenceRepository extends JpaRepository<RefundSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM RefundSequence s WHERE s.shopId = :shopId AND s.prefix = :prefix AND s.fiscalYear = :year")
    Optional<RefundSequence> findByShopIdAndPrefixAndFiscalYearForUpdate(
            @Param("shopId") Long shopId,
            @Param("prefix") String prefix,
            @Param("year") short year);
}
