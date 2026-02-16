package com.desitech.vyaparsathi.subscriptions.repository;

import com.desitech.vyaparsathi.subscriptions.entity.Subscription;
import com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /**
     * Primary method for VyaparSathi subscription checks.
     * All users (Owner/Staff) associated with this Shop ID
     * share the same subscription status.
     */
    Optional<Subscription> findByShopId(Long shopId);

    long countByStatus(SubscriptionStatus status);
}