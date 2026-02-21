package com.desitech.vyaparsathi.auth.service;

import com.desitech.vyaparsathi.auth.dto.AuthResponse;
import com.desitech.vyaparsathi.auth.dto.RegisterRequest;
import com.desitech.vyaparsathi.auth.entity.PasswordResetToken;
import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.entity.RefreshToken;
import com.desitech.vyaparsathi.auth.model.Role;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.common.exception.UserInactiveException;
import com.desitech.vyaparsathi.common.util.TemplateUtil;
import com.desitech.vyaparsathi.common.validators.TokenValidator;
import com.desitech.vyaparsathi.notification.service.EmailService;
import com.desitech.vyaparsathi.notification.service.NotificationService;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AuthService {

    @Value("${app.reset-password-url}")
    private String resetPasswordUrl;

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordResetTokenService resetTokenService;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private NotificationService notificationService;

    /**
     * Authenticate user by PIN and generate access token
     */
    public String authenticateAndGenerateToken(User user, String pin) {
        if (!user.isActive()) {
            throw new UserInactiveException("User account is not active");
        }

        if (!passwordEncoder.matches(pin, user.getPinHash())) {
            throw new BadCredentialsException("Invalid username or PIN");
        }

        return jwtUtil.generateAccessToken(
                user,
                user.getShop() != null ? user.getShop().getId() : null
        );
    }

    /**
     * Register new user in the current tenant (shop)
     */
    public void registerNewUser(RegisterRequest request) {

        if (userRepository.findByUsernameAndShop_Id(
                request.getUsername(),
                TenantContext.getCurrentShopId()).isPresent()) {

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
     * Change PIN when user is logged in
     */
    @Transactional
    public void changeUserPin(String username, String currentPin, String newPin) {

        User user = getUserByUsername(username);

        if (!passwordEncoder.matches(currentPin, user.getPinHash())) {
            throw new BadCredentialsException("Incorrect current PIN provided.");
        }

        // Invalidate all refresh tokens
        refreshTokenService.deleteByUsername(username);

        user.setPinHash(passwordEncoder.encode(newPin));
        user.setLastPasswordChangeAt(LocalDateTime.now());
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
    @Transactional
    public AuthResponse refreshAccessToken(String refreshToken) {

        RefreshToken token = refreshTokenService.validateAndGet(refreshToken);

        User user = getUserByUsername(token.getUsername());

        if (!user.isActive()) {
            throw new UserInactiveException("User account is not active");
        }

        refreshTokenService.delete(token);
        refreshTokenService.createRefreshToken(user.getUsername());

        String newAccessToken = jwtUtil.generateAccessToken(
                user,
                user.getShop() != null ? user.getShop().getId() : null
        );

        return new AuthResponse(
                newAccessToken,
                user.getRole().name(),
                false
        );
    }

    public User getUserByUsername(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("Invalid username or password"));

        if (user.getRole() == Role.SUPER_ADMIN) {
            logger.info("Platform Admin login detected: {}", username);
            return user;
        }

        if (user.getShop() == null) {
            if (user.getRole() != Role.PENDING_OWNER) {
                throw new ApplicationException("User is not assigned to any shop");
            }

            logger.info("Login allowed for PENDING_OWNER without shop: {}", username);
        }

        if (user.getShop() != null) {
            TenantContext.setCurrentShopId(user.getShop().getId());
        }

        return user;
    }

    @Transactional
    public void processForgotPassword(String email) throws MessagingException {

        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            logger.info("Password reset requested for non-existent email: {}", email);
            return;
        }

        User user = userOptional.get();

        String token = resetTokenService.createResetToken(user);

        Map<String, String> variables = new HashMap<>();
        variables.put("name", user.getFirstName());
        variables.put("resetLink", resetPasswordUrl + "?token=" + token);
        variables.put("currentYear",
                String.valueOf(LocalDateTime.now().getYear()));

        String htmlContent =
                TemplateUtil.loadTemplate("templates/reset-password.html", variables);

        emailService.sendEmail(email, "Password Reset Request", htmlContent);

        notificationService.sendDirectMessage(
                email,
                "A password reset link has been sent to your email: " + email
        );
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {

        PasswordResetToken resetToken =
                resetTokenService.findByTokenUnfiltered(token)
                        .orElseThrow(() ->
                                new BadCredentialsException("Invalid reset token"));

        TokenValidator.validateToken(resetToken);

        User user = resetToken.getUser();

        if (user.getShop() != null) {
            TenantContext.setCurrentShopId(user.getShop().getId());
        }

        try {
            user.setPinHash(passwordEncoder.encode(newPassword));
            user.setLastPasswordChangeAt(LocalDateTime.now());
            userRepository.save(user);

            resetTokenService.markTokenAsUsed(token);

            // Invalidate all sessions
            refreshTokenService.deleteByUsername(user.getUsername());

            Map<String, String> variables = new HashMap<>();
            variables.put("name", user.getFirstName());
            variables.put("resetDateTime",
                    LocalDateTime.now().format(
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            String htmlContent =
                    TemplateUtil.loadTemplate(
                            "templates/reset-password-confirmation.html",
                            variables);

            emailService.sendEmail(
                    user.getEmail(),
                    "Password Reset Confirmation",
                    htmlContent
            );

        } catch (MessagingException e) {
            logger.error("Failed to send confirmation email to: {}",
                    user.getEmail(), e);
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public RefreshToken rotateRefreshToken(String oldTokenStr) {
        // 1. Validate and get old token
        RefreshToken oldToken = refreshTokenService.validateAndGet(oldTokenStr);
        User user = getUserByUsername(oldToken.getUsername());

        if (!user.isActive()) {
            throw new UserInactiveException("User account is not active");
        }

        // 2. Delete old token (Rotation)
        refreshTokenService.delete(oldToken);

        // 3. Create and return new token entity
        return refreshTokenService.createRefreshToken(user.getUsername());
    }
}
