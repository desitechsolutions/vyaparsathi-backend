package com.desitech.vyaparsathi.auth.service;

import com.desitech.vyaparsathi.auth.entity.PasswordResetToken;
import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.repository.PasswordResetTokenRepository;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetTokenService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetTokenService.class);

    @Value("${app.reset-token-expiration}")
    private long resetTokenExpiration;
    private final PasswordResetTokenRepository tokenRepository;
    @Autowired
    private ShopRepository shopRepository;

    public PasswordResetTokenService(PasswordResetTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    public String createResetToken(User user) {
        Shop userShop = user.getShop();
        if (userShop == null) {
            logger.warn("Password reset attempted for user {} with no associated shop", user.getUsername());
            throw new ApplicationException("User is not associated with any shop.");
        }
        TenantContext.setCurrentShopId(userShop.getId());
        try {
            PasswordResetToken token = new PasswordResetToken();
            token.setUser(user);
            token.setToken(UUID.randomUUID().toString());
            token.setExpiryDate(LocalDateTime.now().plusSeconds(resetTokenExpiration / 1000));
            token.setUsed(false);
            token.setCreatedAt(LocalDateTime.now());
            token.setShop(userShop);

            tokenRepository.save(token);
            return token.getToken();
        } finally {
            TenantContext.clear();
        }
    }

    public void markTokenAsUsed(String token) {
        PasswordResetToken resetToken = tokenRepository.findByTokenUnfiltered(token)
                .orElseThrow(() -> new BadCredentialsException("Invalid reset token"));

        resetToken.setUsed(true);
        resetToken.setUsedAt(LocalDateTime.now());
    }

    public Optional<PasswordResetToken> findByTokenUnfiltered(String token){
        return tokenRepository.findByTokenUnfiltered(token);
    }

    public boolean validateResetToken(String tokenStr) {
        logger.info("Validating reset token: {}", tokenStr);
        PasswordResetToken resetToken = tokenRepository.findByTokenUnfiltered(tokenStr).orElse(null);

        if (resetToken == null) {
            logger.warn("Reset token not found: {}", tokenStr);
            return false;
        }

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            logger.warn("Reset token expired: {}", tokenStr);
            try {
                TenantContext.setCurrentShopId(resetToken.getShop().getId());
                tokenRepository.delete(resetToken);
            } finally {
                TenantContext.clear();
            }
            return false;
        }

        return !resetToken.isUsed();
    }
}