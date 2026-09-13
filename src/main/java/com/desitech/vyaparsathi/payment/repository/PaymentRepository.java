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

    List<Payment> findBySupplierIdAndShopIdAndPaymentDateBetween(
            Long supplierId, Long shopId, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate);

    List<Payment> findBySupplierIdAndShopIdAndPaymentDateBefore(
            Long supplierId, Long shopId, java.time.LocalDateTime date);

    /**
     * Search payments by transaction ID or reference.
     * NOTE: Payment entity uses customerId (Long) — no customer JOIN available.
     */
    @Query("SELECT p FROM Payment p " +
            "WHERE (LOWER(p.transactionId) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(p.reference) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(p.notes) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "ORDER BY p.paymentDate DESC")
    Page<Payment> searchPayments(@Param("search") String search, Pageable pageable);

    /**
     * Aggregate payment volume by method for a shop's sale-side payments in a range.
     * Returns rows of {@code [PaymentMethod, sumAmount, txnCount]}.
     * Filters to {@link PaymentSourceType#SALE} — supplier/refund payments are excluded.
     */
    @Query("SELECT p.paymentMethod, COALESCE(SUM(p.amount), 0), COUNT(p) " +
            "FROM Payment p " +
            "WHERE p.shop.id = :shopId " +
            "AND p.sourceType = com.desitech.vyaparsathi.payment.enums.PaymentSourceType.SALE " +
            "AND p.paymentDate >= :start AND p.paymentDate <= :end " +
            "GROUP BY p.paymentMethod")
    List<Object[]> sumSalePaymentsByMethodForShop(
            @Param("shopId") Long shopId,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end
    );

    // ← ADDED: Get total payments by payment date range (not sale date)
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "WHERE p.paymentDate >= :start AND p.paymentDate <= :end")
    BigDecimal sumPaymentsByPaymentDateRange(
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end
    );
}