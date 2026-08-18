package com.desitech.vyaparsathi.auth.service;

import com.desitech.vyaparsathi.auth.entity.PasswordResetToken;
import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.repository.PasswordResetTokenRepository;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.shop.entity.Shop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetTokenService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetTokenService.class);

    @Value("${app.reset-token-expiration}")
    private long resetTokenExpiration;

    private final PasswordResetTokenRepository tokenRepository;

    public PasswordResetTokenService(PasswordResetTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * Creates a reset token for the given user. Returns the RAW token to be
     * emailed to the user; only the SHA-256 hash is persisted, so a DB leak
     * cannot yield usable tokens.
     *
     * PENDING_OWNER users have no shop; the token is saved with a null shop_id
     * (shopless password recovery must be supported so users can complete
     * onboarding after a lost-PIN scenario).
     */
    public String createResetToken(User user) {
        Shop userShop = user.getShop();
        Long previousShopId = TenantContext.getCurrentShopId();
        if (userShop != null) {
            TenantContext.setCurrentShopId(userShop.getId());
        }
        try {
            String rawToken = UUID.randomUUID().toString();
            String tokenHash = hashToken(rawToken);

            PasswordResetToken token = new PasswordResetToken();
            token.setUser(user);
            token.setTokenHash(tokenHash);
            token.setExpiryDate(LocalDateTime.now().plusSeconds(resetTokenExpiration / 1000));
            token.setUsed(false);
            token.setCreatedAt(LocalDateTime.now());
            token.setShop(userShop); // may be null for PENDING_OWNER users

            tokenRepository.save(token);
            return rawToken;
        } finally {
            if (previousShopId != null) {
                TenantContext.setCurrentShopId(previousShopId);
            } else {
                TenantContext.clear();
            }
        }
    }

    public void markTokenAsUsed(String rawToken) {
        String tokenHash = hashToken(rawToken);
        PasswordResetToken resetToken = tokenRepository.findByTokenHashUnfiltered(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid reset token"));

        resetToken.setUsed(true);
        resetToken.setUsedAt(LocalDateTime.now());
    }

    public Optional<PasswordResetToken> findByRawTokenUnfiltered(String rawToken) {
        return tokenRepository.findByTokenHashUnfiltered(hashToken(rawToken));
    }

    public boolean validateResetToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        String tokenHash = hashToken(rawToken);
        logger.info("Validating reset token (hash prefix): {}", tokenHash.substring(0, 8));
        PasswordResetToken resetToken = tokenRepository.findByTokenHashUnfiltered(tokenHash).orElse(null);

        if (resetToken == null) {
            logger.warn("Reset token not found (hash prefix): {}", tokenHash.substring(0, 8));
            return false;
        }

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            logger.warn("Reset token expired (hash prefix): {}", tokenHash.substring(0, 8));
            Long previousShopId = TenantContext.getCurrentShopId();
            try {
                if (resetToken.getShop() != null) {
                    TenantContext.setCurrentShopId(resetToken.getShop().getId());
                }
                tokenRepository.delete(resetToken);
            } finally {
                if (previousShopId != null) {
                    TenantContext.setCurrentShopId(previousShopId);
                } else {
                    TenantContext.clear();
                }
            }
            return false;
        }

        return !resetToken.isUsed();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }
}
