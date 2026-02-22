package com.desitech.vyaparsathi.auth.controller;

import com.desitech.vyaparsathi.auth.dto.*;
import com.desitech.vyaparsathi.auth.entity.RefreshToken;
import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.auth.service.PasswordResetTokenService;
import com.desitech.vyaparsathi.auth.service.RefreshTokenService;
import com.desitech.vyaparsathi.auth.service.UserManagementService;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.common.payload.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.desitech.vyaparsathi.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordResetTokenService resetTokenService;

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        User user = authService.getUserByUsername(request.getUsername());
        String token = authService.authenticateAndGenerateToken(user, request.getPin());
        String refreshToken = authService.createRefreshToken(user.getUsername());

        logger.info("User login successful: {}", request.getUsername());

        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("Strict")
                .maxAge(Duration.ofDays(7))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse(token, user.getRole().name(), false));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        try {
            User user = userManagementService.createInitialUser(request);

            String accessToken = jwtUtil.generateAccessToken(user, null);
            logger.info("New user registered: username={}", request.getUsername());

            String refreshToken = refreshTokenService
                    .createRefreshToken(user.getUsername())
                    .getToken();

            ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .sameSite("Strict")
                    .maxAge(Duration.ofDays(7))
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(new AuthResponse(accessToken, user.getRole().name(), true));

        } catch (Exception e) {
            throw new ApplicationException(e.getMessage());
        }
    }

    @PostMapping("/change-pin")
    public ResponseEntity<String> changePin(
            @Valid @RequestBody ChangePinRequest request,
            Authentication authentication) {

        try {
            String username = authentication.getName();

            authService.changeUserPin(
                    username,
                    request.getCurrentPin(),
                    request.getNewPin()
            );

            // 1️⃣ Invalidate all refresh tokens
            refreshTokenService.deleteByUsername(username);

            // 2️⃣ Delete cookie
            ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .sameSite("None")
                    .maxAge(0) // expire immediately
                    .build();

            logger.info("PIN changed successfully for user {}", username);

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                    .body("PIN changed successfully. Please log in again.");

        } catch (Exception e) {
            logger.error("Failed to change PIN: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to change PIN", e);
        }
    }


    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {

        try {
            if (refreshToken == null) {
                throw new ApplicationException("Refresh token missing");
            }

            // 1. Rotate the token in the DB and get the new Entity
            RefreshToken newTokenEntity = authService.rotateRefreshToken(refreshToken);
            User user = authService.getUserByUsername(newTokenEntity.getUsername());

            // 2. Generate new Access Token
            String newAccessToken = jwtUtil.generateAccessToken(
                    user,
                    user.getShop() != null ? user.getShop().getId() : null
            );

            // 3. Create the new Secure Cookie
            ResponseCookie cookie = ResponseCookie.from("refreshToken", newTokenEntity.getToken())
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .sameSite("None")
                    .maxAge(Duration.ofDays(7))
                    .build();

            logger.info("Token rotation successful for user: {}", user.getUsername());

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(new AuthResponse(newAccessToken, user.getRole().name(), false));

        } catch (Exception e) {
            logger.error("Refresh failed: {}", e.getMessage());

            // Clear cookie on failure to stop the frontend from retrying a bad token
            ResponseCookie clearCookie = ResponseCookie.from("refreshToken", "")
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .sameSite("None")
                    .maxAge(0)
                    .build();

            return ResponseEntity.status(401)
                    .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                    .build();
        }
    }

    @PostMapping("/forget-password")
    public ResponseEntity<ApiResponse<String>> requestPasswordReset(
            @RequestBody ForgotPasswordRequest forgotPasswordRequest) {
        try {
            authService.processForgotPassword(forgotPasswordRequest.getEmail());

            ApiResponse<String> response = new ApiResponse<>(
                    "success",
                    "If the email is registered, a password reset link has been sent.",
                    null
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error requesting password reset for email: {}",
                    forgotPasswordRequest.getEmail(), e);

            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>("error", e.getMessage(), null));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(
            @RequestBody ResetPasswordRequest resetPasswordRequest) {
        try {
            authService.resetPassword(
                    resetPasswordRequest.getToken(),
                    resetPasswordRequest.getNewPassword()
            );

            ApiResponse<String> response = new ApiResponse<>(
                    "success",
                    "Password reset successfully.",
                    null
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error resetting password with token: {}",
                    resetPasswordRequest.getToken(), e);

            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>("error", e.getMessage(), null));
        }
    }

    @PostMapping("/validate-reset-token")
    public ResponseEntity<Map<String, Boolean>> validateResetToken(
            @RequestBody ResetTokenRequest resetTokenRequest) {

        logger.info("Received validation request for token: {}",
                resetTokenRequest != null ? resetTokenRequest.getToken() : "NULL REQUEST");

        try {
            assert resetTokenRequest != null;
            boolean isValid = resetTokenService
                    .validateResetToken(resetTokenRequest.getToken());

            Map<String, Boolean> response = new HashMap<>();
            response.put("valid", isValid);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error validating reset token: {}",
                    resetTokenRequest.getToken(), e);

            Map<String, Boolean> response = new HashMap<>();
            response.put("valid", false);

            return ResponseEntity.badRequest().body(response);
        }
    }
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {

        if (refreshToken != null) {
            refreshTokenService.deleteByToken(refreshToken);
        }

        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("None")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body("Logged out successfully");
    }

}
