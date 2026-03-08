package com.desitech.vyaparsathi.subscriptions.repository;

import com.desitech.vyaparsathi.subscriptions.entity.Subscription;
import com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /**
     * Used by Shop Owners/Staff.
     * Note: Hibernate @Filter will automatically append "AND shop_id = ..."
     * if the filter is active in the current session.
     */
    Optional<Subscription> findByShopId(Long shopId);

    /**
     * ADMIN METHOD: Uses a custom JPQL query to bypass the Hibernate Filter.
     * Use this when an Admin is activating a UTR and needs to find the shop's record
     * even though the Admin isn't "logged into" that specific shop.
     */
    @Query(value = "SELECT s FROM Subscription s WHERE s.shop.id = :shopId")
    Optional<Subscription> findByShopIdUnfiltered(@Param("shopId") Long shopId);

    long countByStatus(SubscriptionStatus status);

    /**
     * Cron Job Method: Updates status to EXPIRED.
     * Added 'clearAutomatically = true' to ensure the Persistence Context stays in sync.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Subscription s SET s.status = com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus.EXPIRED " +
            "WHERE (s.status = com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus.TRIAL AND s.trialEndDate < :now) " +
            "OR (s.status = com.desitech.vyaparsathi.subscriptions.enums.SubscriptionStatus.ACTIVE AND s.endDate < :now)")
    int updateExpiredSubscriptions(@Param("now") LocalDateTime now);
}