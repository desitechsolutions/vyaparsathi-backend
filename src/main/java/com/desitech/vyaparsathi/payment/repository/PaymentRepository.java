package com.desitech.vyaparsathi.payment.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.payment.entity.Payment;
import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import com.desitech.vyaparsathi.payment.enums.PaymentSourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Repository
public interface PaymentRepository extends BaseRepository<Payment, Long> {
    Page<Payment> findBySourceTypeAndSourceId(PaymentSourceType sourceType, Long sourceId, Pageable pageable);
    Page<Payment> findBySupplierId(Long supplierId, Pageable pageable);
    Page<Payment> findByCustomerId(Long customerId, Pageable pageable);

    // ADDED: Finds unallocated payments (Advances) ordered by date (First-In, First-Out)
    @Query("SELECT p FROM Payment p WHERE p.customerId = :customerId AND p.sourceId IS NULL AND p.amount > 0 ORDER BY p.paymentDate ASC")
    List<Payment> findAvailableAdvances(@Param("customerId") Long customerId);

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
    @Query("SELECT p.paymentMethod FROM Payment p WHERE p.sourceType = :type AND p.sourceId = :id")
    Set<PaymentMethod> findPaymentMethodsBySource(@Param("type") PaymentSourceType type, @Param("id") Long id);
}