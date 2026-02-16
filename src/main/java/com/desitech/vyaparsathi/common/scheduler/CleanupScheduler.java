package com.desitech.vyaparsathi.common.scheduler;

import com.desitech.vyaparsathi.auth.repository.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class CleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(CleanupScheduler.class);

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    // Run daily at midnight
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        int deletedCount = tokenRepository.deleteAllByExpiryDateBeforeOrUsed(now, true);
        logger.info("Cleaned up {} expired or used password reset tokens", deletedCount);
    }
}