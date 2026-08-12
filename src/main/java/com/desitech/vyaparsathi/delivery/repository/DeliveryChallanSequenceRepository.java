package com.desitech.vyaparsathi.delivery.repository;

import com.desitech.vyaparsathi.delivery.entity.DeliveryChallanSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeliveryChallanSequenceRepository extends JpaRepository<DeliveryChallanSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DeliveryChallanSequence s WHERE s.shopId = :shopId AND s.prefix = :prefix AND s.fiscalYear = :year")
    Optional<DeliveryChallanSequence> findByShopIdAndPrefixAndFiscalYearForUpdate(
            @Param("shopId") Long shopId,
            @Param("prefix") String prefix,
            @Param("year") short year);
}
