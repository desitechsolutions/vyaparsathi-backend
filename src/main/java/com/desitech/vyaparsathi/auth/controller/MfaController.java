package com.desitech.vyaparsathi.auth.controller;

import com.desitech.vyaparsathi.auth.dto.AuthResponse;
import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.auth.service.AuthService;
import com.desitech.vyaparsathi.auth.service.MfaService;
import com.desitech.vyaparsathi.auth.service.RefreshTokenService;
import com.desitech.vyaparsathi.auth.service.SessionService;
import com.desitech.vyaparsathi.common.payload.ApiResponse;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MFA endpoints. Two flavours:
 *
 *   1. Enrollment / management (require an authenticated user):
 *        POST /api/auth/mfa/setup/init      — start enrollment, return QR + secret
 *        POST /api/auth/mfa/setup/confirm   — verify first code, return backup codes
 *        POST /api/auth/mfa/regenerate-codes— replace backup codes (needs current TOTP)
 *        POST /api/auth/mfa/disable         — turn MFA off (needs current TOTP)
 *        GET  /api/auth/mfa/status          — is MFA enabled + how many backup codes left
 *
 *   2. Challenge (invoked between password login and access token):
 *        POST /api/auth/mfa/verify          — exchange MFA challenge token + code
 *                                             for a real access token + refresh cookie
 */
@RestController
@RequestMapping("/api/auth/mfa")
@RequiredArgsConstructor
public class MfaController {

    private static final Logger log = LoggerFactory.getLogger(MfaController.class);

    private static final String COOKIE_NAME = "refreshToken";
    private static final String COOKIE_SAME_SITE = "None";
    private static final Duration REFRESH_COOKIE_TTL = Duration.ofDays(7);

    private final MfaService mfaService;
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final SessionService sessionService;

    // ─── Enrollment ──────────────────────────────────────────────────────

    @PostMapping("/setup/init")
    public ResponseEntity<?> initSetup(Authentication auth) {
        User user = currentUser(auth);
        MfaService.EnrollmentInit init = mfaService.startEnrollment(user);
        Map<String, String> body = new HashMap<>();
        body.put("secret", init.getSecret());
        body.put("otpAuthUri", init.getOtpAuthUri());
        body.put("qrDataUrl", init.getQrDataUrl());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/setup/confirm")
    public ResponseEntity<?> confirmSetup(@RequestBody Map<String, String> body, Authentication auth) {
        User user = currentUser(auth);
        String code = body != null ? body.get("code") : null;
        MfaService.BackupCodeBundle bundle;
        try {
            bundle = mfaService.confirmEnrollment(user, code);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>("error", e.getMessage(), null));
        }
        Map<String, Object> out = new HashMap<>();
        out.put("enabled", true);
        out.put("backupCodes", bundle.getCodes());
        return ResponseEntity.ok(out);
    }

    @PostMapping("/regenerate-codes")
    public ResponseEntity<?> regenerateCodes(@RequestBody Map<String, String> body, Authentication auth) {
        User user = currentUser(auth);
        String code = body != null ? body.get("code") : null;
        // Require the user to re-verify TOTP before we reveal a fresh batch.
        if (!mfaService.verify(user, code)) {
            return ResponseEntity.badRequest().body(new ApiResponse<>("error",
                    "The code you entered is incorrect. Try again.", null));
        }
        MfaService.BackupCodeBundle bundle = mfaService.regenerateBackupCodes(user);
        Map<String, Object> out = new HashMap<>();
        out.put("backupCodes", bundle.getCodes());
        return ResponseEntity.ok(out);
    }

    @PostMapping("/disable")
    public ResponseEntity<?> disable(@RequestBody Map<String, String> body, Authentication auth) {
        User user = currentUser(auth);
        String code = body != null ? body.get("code") : null;
        try {
            mfaService.disable(user, code);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>("error", e.getMessage(), null));
        }
        return ResponseEntity.ok(new ApiResponse<>("success", "MFA disabled.", null));
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(Authentication auth) {
        User user = currentUser(auth);
        Map<String, Object> body = new HashMap<>();
        body.put("enabled", mfaService.isEnabled(user.getId()));
        body.put("remainingBackupCodes", mfaService.remainingBackupCodes(user.getId()));
        return ResponseEntity.ok(body);
    }

    // ─── Challenge (during login) ────────────────────────────────────────

    /**
     * Exchange an MFA challenge token + TOTP/backup code for a real access
     * token + refresh cookie. Public endpoint — the challenge token itself
     * is the authentication signal.
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verifyChallenge(@RequestBody Map<String, String> body, HttpServletRequest httpRequest) {
        String challenge = body != null ? body.get("challengeToken") : null;
        String code = body != null ? body.get("code") : null;
        if (challenge == null || challenge.isBlank()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>("error",
                    "MFA challenge token is missing.", null));
        }

        Long userId;
        try {
            userId = jwtUtil.validateMfaChallengeToken(challenge);
        } catch (JwtException e) {
            return ResponseEntity.status(401).body(new ApiResponse<>("error",
                    "MFA challenge is invalid or expired. Sign in again.", null));
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isActive()) {
            return ResponseEntity.status(401).body(new ApiResponse<>("error",
                    "Account is not available.", null));
        }

        boolean ok = mfaService.verify(user, code);
        if (!ok) {
            return ResponseEntity.badRequest().body(new ApiResponse<>("error",
                    "The code you entered is incorrect. Try again.", null));
        }

        // MFA passed — open the actual session now. session_id begins
        // at MFA-verify (not at password step), so the challenge token
        // never carries a real sid and can't itself be denylisted.
        RefreshTokenService.SessionMetadata sessionMetadata = sessionService.newSessionMetadata(httpRequest);
        String accessToken = authService.generateAccessTokenForVerifiedMfa(user, sessionMetadata.getSessionId());
        String refreshToken = refreshTokenService.createRefreshToken(user.getUsername(), sessionMetadata).getToken();
        log.info("MFA verified for user {} (session={})", user.getUsername(), sessionMetadata.getSessionId());

        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite(COOKIE_SAME_SITE)
                .maxAge(REFRESH_COOKIE_TTL)
                .build();

        AuthResponse resp = new AuthResponse();
        resp.setAccessToken(accessToken);
        resp.setRole(user.getRole().name());
        resp.setOnboardingRequired(Boolean.FALSE);
        resp.setMfaRequired(Boolean.FALSE);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(resp);
    }

    // ─── Utils ───────────────────────────────────────────────────────────

    private User currentUser(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new IllegalStateException("Not authenticated.");
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new IllegalStateException("User not found."));
    }
}
