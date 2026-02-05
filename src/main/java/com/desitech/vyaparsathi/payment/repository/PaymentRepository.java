package com.desitech.vyaparsathi.payment.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.payment.entity.Payment;
import com.desitech.vyaparsathi.payment.enums.PaymentSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Repository
public interface PaymentRepository extends BaseRepository<Payment, Long> {
    List<Payment> findBySourceTypeAndSourceId(PaymentSourceType sourceType, Long sourceId);
    List<Payment> findBySupplierId(Long supplierId);
    List<Payment> findByCustomerId(Long customerId);

    // Bulk sum query (optimized)
    @Query("SELECT p.sourceId, SUM(p.amount) " +
            "FROM Payment p " +
            "WHERE p.sourceType = :sourceType AND p.sourceId IN :saleIds " +
            "GROUP BY p.sourceId")
    List<Object[]> sumPaymentsBySaleIds(
            @Param("saleIds") Set<Long> saleIds,
            @Param("sourceType") PaymentSourceType sourceType
    );

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.customerId = :customerId AND p.sourceId IS NULL")
    BigDecimal getUnallocatedCreditByCustomerId(@Param("customerId") Long customerId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "WHERE p.sourceType = :sourceType AND p.sourceId = :sourceId")
    BigDecimal sumPaymentsBySource(
            @Param("sourceType") PaymentSourceType sourceType,
            @Param("sourceId") Long sourceId
    );
}