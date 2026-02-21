package com.desitech.vyaparsathi.auth.service;

import com.desitech.vyaparsathi.auth.entity.RefreshToken;
import com.desitech.vyaparsathi.auth.repository.RefreshTokenRepository;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.common.exception.TokenExpiredException;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);
    @Value("${app.jwtRefreshExpirationMs:604800000}") // 7 days default
    private Long refreshTokenDurationMs;

    private final RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private ShopRepository shopRepository;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Creates a new refresh token for a given username.
     * If an existing token is present, deletes it (only one active per user).
     */
    @Transactional
    public RefreshToken createRefreshToken(String username) {
        refreshTokenRepository.deleteByUsername(username);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUsername(username);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));

        Long currentShopId = TenantContext.getCurrentShopId();

        if (currentShopId != null) {
            Shop shop = shopRepository.findById(currentShopId)
                    .orElseThrow(() -> new ApplicationException("Shop not found for id: " + currentShopId));
            refreshToken.setShop(shop);
            logger.debug("Refresh token created with shop id: {}", currentShopId);
        } else {
            logger.debug("Refresh token created without shop (normal for PENDING_OWNER / onboarding)");
            // refreshToken.setShop(null); // already default
        }

        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Finds a token by string value. Throws exception if expired.
     */
    @Transactional
    public RefreshToken validateAndGet(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (isExpired(refreshToken)) {
            refreshTokenRepository.delete(refreshToken);
            throw new TokenExpiredException("Refresh token expired. Please log in again.");
        }
        if (refreshToken.getShop() != null) {
            TenantContext.setCurrentShopId(refreshToken.getShop().getId());
        } else {
            TenantContext.clear();
        }
        return refreshToken;
    }

    /**
     * Checks if a refresh token has expired.
     */
    public boolean isExpired(RefreshToken token) {
        return token.getExpiryDate().isBefore(Instant.now());
    }

    /**
     * Deletes refresh token by username (e.g. on logout).
     */
    @Transactional
    public void deleteByUsername(String username) {
        refreshTokenRepository.deleteByUsername(username);
    }

    @Transactional
    public void delete(RefreshToken token) {
        refreshTokenRepository.delete(token);
    }

    @Transactional
    public void deleteByToken(String token) {
        refreshTokenRepository.findByToken(token)
                .ifPresent(refreshTokenRepository::delete);
    }

}
