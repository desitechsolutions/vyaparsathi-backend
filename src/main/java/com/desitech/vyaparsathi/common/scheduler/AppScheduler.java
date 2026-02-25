package com.desitech.vyaparsathi.common.scheduler;

import com.desitech.vyaparsathi.auth.repository.PasswordResetTokenRepository;
import com.desitech.vyaparsathi.subscriptions.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class AppScheduler {

    private static final Logger logger = LoggerFactory.getLogger(AppScheduler.class);

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    /**
     * Cleans up security tokens that are no longer valid.
     * Runs daily at midnight.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        int deletedCount = tokenRepository.deleteAllByExpiryDateBeforeOrUsed(now, true);
        if (deletedCount > 0) {
            logger.info("Security Cleanup: Deleted {} expired/used password tokens", deletedCount);
        }
    }

    /**
     * Checks for subscriptions that have passed their end date and marks them EXPIRED.
     * Runs daily at 1:00 AM.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void processSubscriptionExpirations() {
        LocalDateTime now = LocalDateTime.now();
        // This calls the @Modifying query we discussed earlier
        int updatedCount = subscriptionRepository.updateExpiredSubscriptions(now);

        if (updatedCount > 0) {
            logger.info("Subscription Job: {} shops moved to EXPIRED status", updatedCount);
        }
    }
}