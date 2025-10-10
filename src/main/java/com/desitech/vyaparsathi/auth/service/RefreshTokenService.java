package com.desitech.vyaparsathi.auth.service;

import com.desitech.vyaparsathi.auth.entity.RefreshToken;
import com.desitech.vyaparsathi.auth.repository.RefreshTokenRepository;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.common.exception.TokenExpiredException;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

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
        // Ensure only one token exists per user
        refreshTokenRepository.deleteByUsername(username);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUsername(username);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));
        if (TenantContext.getCurrentShopId() != null) {
            Shop shop = shopRepository.findById(TenantContext.getCurrentShopId())
                    .orElseThrow(() -> new ApplicationException("Shop not found"));
            refreshToken.setShop(shop);
        }

        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Finds a token by string value. Throws exception if expired.
     */
    public RefreshToken validateAndGet(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (isExpired(refreshToken)) {
            refreshTokenRepository.deleteByUsername(refreshToken.getUsername());
            throw new TokenExpiredException("Refresh token expired. Please log in again.");
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
    public void deleteByUsername(String username) {
        refreshTokenRepository.deleteByUsername(username);
    }
}
