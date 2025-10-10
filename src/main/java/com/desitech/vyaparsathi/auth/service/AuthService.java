package com.desitech.vyaparsathi.auth.service;

import com.desitech.vyaparsathi.auth.dto.AuthResponse;
import com.desitech.vyaparsathi.auth.dto.RegisterRequest;
import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.entity.RefreshToken;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ResetTokenService resetTokenService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private ShopRepository shopRepository;

    /**
     * Authenticate user by PIN and generate access token
     */
    public String authenticateAndGenerateToken(User user, String pin) {
        if (!user.isActive()) {
            throw new UsernameNotFoundException("User is inactive");
        }
        if (!passwordEncoder.matches(pin, user.getPinHash())) {
            throw new BadCredentialsException("Invalid username or PIN");
        }
        return jwtUtil.generateAccessToken(user, user.getShop() != null ? user.getShop().getId() : null);
    }

    /**
     * Register new user in the current tenant (shop)
     */
    public void registerNewUser(RegisterRequest request) {
        if (userRepository.findByUsernameAndShop_Id(request.getUsername(), TenantContext.getCurrentShopId()).isPresent()) {
            throw new IllegalArgumentException("Username already exists in this shop");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPinHash(passwordEncoder.encode(request.getPin()));
        user.setRole(request.getRole());
        user.setActive(true);

        if (TenantContext.getCurrentShopId() != null) {
            Shop shop = shopRepository.findById(TenantContext.getCurrentShopId())
                    .orElseThrow(() -> new ApplicationException("Shop not found"));
            user.setShop(shop);
        }

        userRepository.save(user);
    }

    /**
     * Reset PIN using a reset token
     */
    @Transactional
    public void resetUserPin(String username, String token, String newPin) {
        if (!resetTokenService.validateToken(token)) {
            throw new IllegalArgumentException("Invalid or expired token");
        }
        User user = getUserByUsername(username);

        user.setPinHash(passwordEncoder.encode(newPin));
        userRepository.save(user);

        resetTokenService.deleteToken(token);
    }

    /**
     * Change PIN when user is logged in
     */
    @Transactional
    public void changeUserPin(String username, String currentPin, String newPin) {
        User user = getUserByUsername(username);

        if (!passwordEncoder.matches(currentPin, user.getPinHash())) {
            throw new BadCredentialsException("Incorrect current PIN provided.");
        }

        user.setPinHash(passwordEncoder.encode(newPin));
        userRepository.save(user);
    }

    /**
     * Issue a new refresh token on login
     */
    public String createRefreshToken(String username) {
        return refreshTokenService.createRefreshToken(username).getToken();
    }

    /**
     * Validate refresh token and issue a new access token
     */
    public AuthResponse refreshAccessToken(String refreshToken) {
        RefreshToken token = refreshTokenService.validateAndGet(refreshToken);

        User user = getUserByUsername(token.getUsername());
        String newAccessToken = jwtUtil.generateAccessToken(user, user.getShop() != null ? user.getShop().getId() : null);

        return new AuthResponse(newAccessToken, refreshToken, user.getRole().name());
    }

    public User getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApplicationException("User not found"));

        if (user.getShop() == null) {
            throw new ApplicationException("User is not assigned to any shop");
        }

        // Set shopId in TenantContext for multi-tenancy
        TenantContext.setCurrentShopId(user.getShop().getId());

        return user;
    }

}
