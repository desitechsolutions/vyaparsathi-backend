package com.desitech.vyaparsathi.subscriptions.razorpay.repository;

import com.desitech.vyaparsathi.subscriptions.razorpay.entity.RazorpaySubscriptionOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RazorpaySubscriptionOrderRepository extends JpaRepository<RazorpaySubscriptionOrder, Long> {

    Optional<RazorpaySubscriptionOrder> findByRazorpaySubscriptionId(String razorpaySubscriptionId);

    Optional<RazorpaySubscriptionOrder> findTopByShopIdOrderByCreatedAtDesc(Long shopId);

    Optional<RazorpaySubscriptionOrder> findTopByShopIdAndStatusOrderByCreatedAtDesc(Long shopId, String status);

    /**
     * Pessimistic-write locked fetch used by pause / resume / cancel to prevent
     * concurrent state mutations from racing each other and issuing duplicate
     * Razorpay API calls for the same subscription.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RazorpaySubscriptionOrder r WHERE r.shopId = :shopId ORDER BY r.createdAt DESC LIMIT 1")
    Optional<RazorpaySubscriptionOrder> findTopByShopIdOrderByCreatedAtDescForUpdate(@Param("shopId") Long shopId);

    /**
     * Returns {@code true} when the shop has any subscription in a non-terminal
     * active state (CREATED | AUTHENTICATED | ACTIVE | PENDING | PAUSED).
     * Used in {@code createSubscriptionOrder} to block parallel subscriptions and
     * prevent double-charging.
     */
    @Query("SELECT COUNT(r) > 0 FROM RazorpaySubscriptionOrder r " +
           "WHERE r.shopId = :shopId " +
           "AND r.status IN ('CREATED', 'AUTHENTICATED', 'ACTIVE', 'PENDING', 'PAUSED')")
    boolean hasActiveSubscriptionForShop(@Param("shopId") Long shopId);
}
