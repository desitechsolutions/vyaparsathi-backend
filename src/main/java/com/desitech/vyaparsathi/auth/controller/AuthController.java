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
import com.desitech.vyaparsathi.auth.service.RefreshTokenService;
import com.desitech.vyaparsathi.auth.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
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

    /**
     * All refresh-token cookies use SameSite=None so cross-origin
     * withCredentials calls from the SPA work in every browser. `Secure` is
     * required by browsers when SameSite=None is set. When we introduce
     * same-origin deployment (Phase 6+), switch to Lax and add a config flag.
     */
    private static final String COOKIE_SAME_SITE = "None";
    private static final String COOKIE_NAME = "refreshToken";
    private static final Duration REFRESH_COOKIE_TTL = Duration.ofDays(7);

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

    @Autowired
    private com.desitech.vyaparsathi.auth.service.EmailVerificationService emailVerificationService;

    @Autowired
    private SessionService sessionService;

    private ResponseCookie buildRefreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value == null ? "" : value)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite(COOKIE_SAME_SITE)
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return buildRefreshCookie("", Duration.ZERO);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request,
                                              HttpServletRequest httpRequest) {
        User user = authService.getUserByUsername(request.getUsername());

        // Pre-generate the session identity BEFORE minting the access
        // token so the `sid` claim points at the same session_id we
        // then persist on the refresh_token row. This is the join key
        // between JWT and DB for per-session revocation.
        RefreshTokenService.SessionMetadata sessionMetadata = sessionService.newSessionMetadata(httpRequest);

        AuthService.LoginOutcome outcome = authService.authenticateForLoginWithSession(
                user, request.getPassword(), sessionMetadata.getSessionId());

        if (outcome.isMfaRequired()) {
            // Password checked out, but MFA is enabled — hand the FE a
            // short-lived challenge token and skip the refresh cookie until
            // MFA is verified. NOTE: intentionally no refresh cookie here
            // so a stolen challenge alone can't keep a session alive. The
            // real session is opened inside MfaController.verify() once
            // the second factor succeeds.
            logger.info("Password OK, MFA challenge issued for {}", request.getUsername());
            return ResponseEntity.ok(AuthResponse.mfaChallenge(outcome.getToken(), user.getRole().name()));
        }

        String refreshToken = refreshTokenService
                .createRefreshToken(user.getUsername(), sessionMetadata)
                .getToken();
        logger.info("User login successful: {} (session={})", request.getUsername(), sessionMetadata.getSessionId());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(refreshToken, REFRESH_COOKIE_TTL).toString())
                .body(new AuthResponse(outcome.getToken(), user.getRole().name(), false));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest) {
        try {
            User user = userManagementService.createInitialUser(request);

            RefreshTokenService.SessionMetadata sessionMetadata = sessionService.newSessionMetadata(httpRequest);
            String accessToken = jwtUtil.generateAccessToken(user, null, sessionMetadata.getSessionId());
            logger.info("New user registered: username={}", request.getUsername());

            String refreshToken = refreshTokenService
                    .createRefreshToken(user.getUsername(), sessionMetadata)
                    .getToken();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(refreshToken, REFRESH_COOKIE_TTL).toString())
                    .body(new AuthResponse(accessToken, user.getRole().name(), true));

        } catch (Exception e) {
            throw new ApplicationException(e.getMessage());
        }
    }

    @PostMapping({"/change-pin", "/change-password"})
    public ResponseEntity<String> changePassword(
            @Valid @RequestBody ChangePinRequest request,
            Authentication authentication) {

        try {
            String username = authentication.getName();

            authService.changeUserPin(
                    username,
                    request.getCurrentPassword(),
                    request.getNewPassword()
            );

            logger.info("Password changed successfully for user {}", username);

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                    .body("Password changed successfully. Please log in again.");

        } catch (Exception e) {
            logger.error("Failed to change password: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to change password", e);
        }
    }


    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @CookieValue(name = COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest) {

        try {
            if (refreshToken == null) {
                throw new ApplicationException("Refresh token missing");
            }

            // Peek at the outgoing session_id so we can pass it into
            // both the rotation (preserve identity) and the new access
            // token (sid claim). rotateRefreshToken preserves session_id
            // internally regardless, so this is just for the JWT claim.
            String sessionIdForNewJwt = refreshTokenService.findByToken(refreshToken)
                    .map(rt -> rt.getSessionId()).orElse(null);

            RefreshTokenService.SessionMetadata refreshedMeta =
                    sessionService.refreshedMetadata(httpRequest, sessionIdForNewJwt);

            RefreshToken newTokenEntity = authService.rotateRefreshToken(refreshToken, refreshedMeta);
            User user = authService.getUserByUsername(newTokenEntity.getUsername());

            String newAccessToken = jwtUtil.generateAccessToken(
                    user,
                    user.getShop() != null ? user.getShop().getId() : null,
                    newTokenEntity.getSessionId()
            );

            logger.info("Token rotation successful for user: {} (session={})",
                    user.getUsername(), newTokenEntity.getSessionId());

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(newTokenEntity.getToken(), REFRESH_COOKIE_TTL).toString())
                    .body(new AuthResponse(newAccessToken, user.getRole().name(), false));

        } catch (Exception e) {
            logger.error("Refresh failed: {}", e.getMessage());

            return ResponseEntity.status(401)
                    .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
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
            logger.error("Error resetting password", e);

            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>("error", e.getMessage(), null));
        }
    }

    @PostMapping("/validate-reset-token")
    public ResponseEntity<Map<String, Boolean>> validateResetToken(
            @RequestBody ResetTokenRequest resetTokenRequest) {

        logger.info("Received validation request for reset token");

        try {
            assert resetTokenRequest != null;
            boolean isValid = resetTokenService
                    .validateResetToken(resetTokenRequest.getToken());

            Map<String, Boolean> response = new HashMap<>();
            response.put("valid", isValid);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error validating reset token", e);

            Map<String, Boolean> response = new HashMap<>();
            response.put("valid", false);

            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @CookieValue(name = COOKIE_NAME, required = false) String refreshToken) {

        authService.logout(refreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .body("Logged out successfully");
    }

    /**
     * Confirms an email-verification token emailed at registration time.
     * Response is idempotent — a second visit still returns success once
     * the account has been verified.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<String>> verifyEmail(@RequestBody Map<String, String> body) {
        String token = body != null ? body.get("token") : null;
        try {
            emailVerificationService.verifyToken(token);
            return ResponseEntity.ok(new ApiResponse<>("success", "Email verified successfully.", null));
        } catch (Exception e) {
            logger.warn("Email verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>("error", e.getMessage(), null));
        }
    }

    /**
     * Re-sends a verification email for the given address. The response is
     * intentionally identical whether or not the email exists, to avoid
     * enumerating users.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<String>> resendVerification(@RequestBody Map<String, String> body) {
        String email = body != null ? body.get("email") : null;
        try {
            emailVerificationService.resendForEmail(email);
        } catch (Exception e) {
            logger.warn("Resend verification error (silently ignored to avoid enumeration): {}", e.getMessage());
        }
        return ResponseEntity.ok(new ApiResponse<>(
                "success",
                "If this email is registered and unverified, a new verification link is on its way.",
                null));
    }

}
