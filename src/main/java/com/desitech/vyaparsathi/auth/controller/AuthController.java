
package com.desitech.vyaparsathi.auth.controller;
import com.desitech.vyaparsathi.auth.dto.*;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:3000")
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
        return ResponseEntity.ok(new AuthResponse(token, refreshToken, user.getRole().name()));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        try {
            // 1. Create user (no shop yet)
            User user = userManagementService.createInitialUser(request); // new method, see below

            // 2. Auto-login (limited token - no tenant context yet)
            String accessToken = jwtUtil.generateAccessToken(user, null); // no shopId
            String refreshToken = refreshTokenService.createRefreshToken(user.getUsername()).getToken();
            logger.info("New user registered: username={}",
                    request.getUsername());

            // 3. Return token + flag that onboarding is pending
            AuthResponse response = new AuthResponse(accessToken, refreshToken, user.getRole().name());
            response.setOnboardingRequired(true);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            throw new ApplicationException("Registration failed", e);
        }
    }
    @PostMapping("/change-pin")
    public ResponseEntity<String> changePin(@Valid @RequestBody ChangePinRequest request, Authentication authentication) {
        try {
            // Get the username from the current security context to ensure users can only change their own PIN.
            String username = authentication.getName();
            authService.changeUserPin(username, request.getCurrentPin(), request.getNewPin());
            logger.info("PIN changed successfully for user {}", username);
            return ResponseEntity.ok("PIN changed successfully. Please log in again.");
        } catch (Exception e) {
            logger.error("Failed to change PIN: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to change PIN", e);
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody Map<String, String> body) {
        try {
            String refreshToken = body.get("refreshToken");
            AuthResponse response = authService.refreshAccessToken(refreshToken);
            logger.info("Refreshed access token using refresh token");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to refresh access token: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to refresh access token", e);
        }
    }

    @PostMapping("/forget-password")
    public ResponseEntity<ApiResponse<String>> requestPasswordReset(@RequestBody ForgotPasswordRequest forgotPasswordRequest) {
        try {
            authService.processForgotPassword(forgotPasswordRequest.getEmail());
            ApiResponse<String> response = new ApiResponse<>(
                    "success",
                    "If the email is registered, a password reset link has been sent.",
                    null
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error requesting password reset for email: {}", forgotPasswordRequest.getEmail(), e);
            return ResponseEntity.badRequest().body(new ApiResponse<>("error", e.getMessage(), null));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@RequestBody ResetPasswordRequest resetPasswordRequest) {
        try {
            authService.resetPassword(resetPasswordRequest.getToken(), resetPasswordRequest.getNewPassword());
            ApiResponse<String> response = new ApiResponse<>(
                    "success",
                    "Password reset successfully.",
                    null
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error resetting password with token: {}", resetPasswordRequest.getToken(), e);
            return ResponseEntity.badRequest().body(new ApiResponse<>("error", e.getMessage(), null));
        }
    }

    @PostMapping("/validate-reset-token")
    public ResponseEntity<Map<String, Boolean>> validateResetToken(@RequestBody ResetTokenRequest resetTokenRequest) {
        logger.info("Received validation request for token: {}", resetTokenRequest != null ? resetTokenRequest.getToken() : "NULL REQUEST");
        try {
            boolean isValid = resetTokenService.validateResetToken(resetTokenRequest.getToken());
            Map<String, Boolean> response = new HashMap<>();
            response.put("valid", isValid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error validating reset token: {}", resetTokenRequest.getToken(), e);
            Map<String, Boolean> response = new HashMap<>();
            response.put("valid", false);
            return ResponseEntity.badRequest().body(response);
        }
    }
}