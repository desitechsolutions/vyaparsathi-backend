package com.desitech.vyaparsathi.subscriptions.repository;

import com.desitech.vyaparsathi.subscriptions.enums.PaymentVerificationStatus;
import com.desitech.vyaparsathi.subscriptions.entity.PaymentVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionPayRepository extends JpaRepository<PaymentVerification, Long> {

    /**
     * Used by Super Admin to see all pending payments
     * across the entire platform to verify against HDFC bank statements.
     */
    List<PaymentVerification> findByStatusOrderBySubmittedAtDesc(PaymentVerificationStatus status);

    /**
     * Prevents duplicate UTR submissions.
     * Check this before saving a new verification to ensure a user isn't
     * reusing a 12-digit code.
     */
    Optional<PaymentVerification> findByUtrNumber(String utrNumber);

    /**
     * Allows you to see the payment history for a specific business.
     */
    List<PaymentVerification> findByShopIdOrderBySubmittedAtDesc(Long shopId);

    long countByStatus(PaymentVerificationStatus status);

    @Query("SELECT SUM(p.amount) FROM PaymentVerification p WHERE p.status = 'APPROVED'")
    Double sumApprovedPayments();
}