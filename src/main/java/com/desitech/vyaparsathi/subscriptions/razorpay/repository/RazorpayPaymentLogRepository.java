package com.desitech.vyaparsathi.subscriptions.razorpay.repository;

import com.desitech.vyaparsathi.subscriptions.razorpay.entity.RazorpayPaymentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RazorpayPaymentLogRepository extends JpaRepository<RazorpayPaymentLog, Long> {

    /**
     * Idempotency check — used before inserting a payment log to ensure a given
     * {@code razorpay_payment_id} is never recorded twice even if Razorpay
     * delivers the same webhook event multiple times.
     */
    Optional<RazorpayPaymentLog> findByRazorpayPaymentId(String razorpayPaymentId);

    List<RazorpayPaymentLog> findByShopIdOrderByCreatedAtDesc(Long shopId);
}
