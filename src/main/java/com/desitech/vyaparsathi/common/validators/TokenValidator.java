package com.desitech.vyaparsathi.common.validators;

import com.desitech.vyaparsathi.auth.entity.PasswordResetToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.LocalDateTime;

public class TokenValidator {

    private static final Logger logger = LoggerFactory.getLogger(TokenValidator.class);

    public static void validateToken(PasswordResetToken token) {
        if (token == null) {
            logger.error("Token validation failed: Token is null");
            throw new BadCredentialsException("Invalid reset token");
        }

        String hashPrefix = token.getTokenHash() != null && token.getTokenHash().length() >= 8
                ? token.getTokenHash().substring(0, 8)
                : "unknown";

        if (token.isUsed()) {
            logger.warn("Token validation failed: token already used (hash prefix: {})", hashPrefix);
            throw new BadCredentialsException("This token has already been used");
        }

        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            logger.warn("Token validation failed: token expired (hash prefix: {})", hashPrefix);
            throw new BadCredentialsException("This token has expired");
        }

        logger.info("Token validated successfully (hash prefix: {})", hashPrefix);
    }
}
