package com.desitech.vyaparsathi.auth.service;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.common.util.TemplateUtil;
import com.desitech.vyaparsathi.notification.service.EmailService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Email verification for new users.
 *
 * Flow:
 *   1. New user created → generate raw token, store SHA-256 hash on the user
 *      row (email_verification_token_hash + email_verification_expiry).
 *   2. Email the raw token as a URL to the user.
 *   3. User clicks link → POST /api/auth/verify-email/{token}.
 *   4. Service hashes the incoming token, matches against the user row,
 *      checks expiry, sets email_verified = true, wipes the token columns.
 *
 * Tokens are single-use and expire after {@link #TOKEN_TTL_HOURS} hours.
 * A fresh token replaces any existing pending one (used by /resend-verification).
 *
 * Existing users pre-Phase-2 are backfilled to email_verified = true by
 * migration V106, so they aren't affected.
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    public static final int TOKEN_TTL_HOURS = 24;

    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.verify-email-url:http://localhost:3000/auth/verify-email}")
    private String verifyEmailUrl;

    /**
     * Issues (or re-issues) a verification token for the user. Persists the
     * hash on the user row and emails the raw token. Callers may swallow
     * mail failures — the token still stands, and the user can request
     * another via /resend-verification.
     */
    @Transactional
    public void issueVerificationToken(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.debug("Skipping verification token — user has no email address");
            return;
        }
        if (user.isEmailVerified()) {
            log.debug("Skipping verification token — user {} already verified", user.getUsername());
            return;
        }

        String rawToken = UUID.randomUUID().toString();
        user.setEmailVerificationTokenHash(sha256Hex(rawToken));
        user.setEmailVerificationExpiry(LocalDateTime.now().plusHours(TOKEN_TTL_HOURS));
        userRepository.save(user);

        try {
            sendVerificationEmail(user, rawToken);
        } catch (MessagingException ex) {
            // Non-fatal: the token is stored; the user can resend from the app.
            log.error("Failed to send verification email to {}: {}", user.getEmail(), ex.getMessage());
        }
    }

    /**
     * Verifies the token, marks the user's email as verified, and clears the
     * verification columns. Returns the affected user for callers that want
     * to log them in immediately after verification.
     */
    @Transactional
    public User verifyToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Verification token is missing.");
        }
        String hash = sha256Hex(rawToken);
        User user = userRepository.findByEmailVerificationTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Verification link is invalid or has already been used."));

        if (user.isEmailVerified()) {
            // Idempotent success — the user probably clicked the link twice.
            return user;
        }
        if (user.getEmailVerificationExpiry() == null
                || user.getEmailVerificationExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("This verification link has expired. Request a new one from the app.");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationTokenHash(null);
        user.setEmailVerificationExpiry(null);
        userRepository.save(user);
        log.info("Email verified for user {}", user.getUsername());
        return user;
    }

    /**
     * Look the user up by email (case-insensitively via repository) and
     * re-issue a token. Response is deliberately generic so the endpoint
     * never leaks whether the email exists.
     */
    @Transactional
    public void resendForEmail(String email) {
        if (email == null || email.isBlank()) return;
        userRepository.findByEmail(email).ifPresent(user -> {
            if (!user.isEmailVerified()) {
                issueVerificationToken(user);
            }
        });
    }

    private void sendVerificationEmail(User user, String rawToken) throws MessagingException {
        String link = verifyEmailUrl + "?token=" + rawToken;
        Map<String, String> vars = new HashMap<>();
        vars.put("name", user.getFirstName() != null && !user.getFirstName().isBlank()
                ? user.getFirstName()
                : user.getUsername());
        vars.put("verifyLink", link);
        vars.put("expiryHours", String.valueOf(TOKEN_TTL_HOURS));
        vars.put("currentYear", String.valueOf(LocalDateTime.now().getYear()));
        String html = TemplateUtil.loadTemplate("templates/verify-email.html", vars);
        emailService.sendEmail(user.getEmail(), "Verify your VyaparSathi email address", html);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }
}
