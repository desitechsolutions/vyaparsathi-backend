package com.desitech.vyaparsathi.receipt.repository;

import com.desitech.vyaparsathi.receipt.entity.PaymentReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {

    Optional<PaymentReceipt> findByPaymentId(Long paymentId);

    Optional<PaymentReceipt> findByReceiptNumber(String receiptNumber);

    @Query("SELECT r FROM PaymentReceipt r WHERE r.customerId = :customerId ORDER BY r.receiptDate DESC")
    Page<PaymentReceipt> findByCustomerId(@Param("customerId") Long customerId, Pageable pageable);
}
