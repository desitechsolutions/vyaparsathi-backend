package com.desitech.vyaparsathi.refund.repository;

import com.desitech.vyaparsathi.refund.entity.Refund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    /**
     * Sum of already-refunded amounts against a given Payment. Used to reject
     * over-refunds before we create a new Refund row.
     */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.originalPaymentId = :paymentId AND r.status <> 'FAILED'")
    BigDecimal sumRefundedByPaymentId(@Param("paymentId") Long paymentId);

    List<Refund> findByOriginalPaymentIdOrderByIdDesc(Long paymentId);

    Page<Refund> findAllByCustomerId(Long customerId, Pageable pageable);
}
