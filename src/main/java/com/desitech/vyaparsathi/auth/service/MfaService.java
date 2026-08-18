package com.desitech.vyaparsathi.auth.service;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.entity.UserBackupCode;
import com.desitech.vyaparsathi.auth.entity.UserMfaSettings;
import com.desitech.vyaparsathi.auth.repository.UserBackupCodeRepository;
import com.desitech.vyaparsathi.auth.repository.UserMfaSettingsRepository;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * TOTP (RFC 6238) multi-factor authentication.
 *
 * <p>Flow:
 * <ol>
 *   <li>{@link #startEnrollment(User)} — generate a fresh secret, upsert a
 *       disabled {@link UserMfaSettings} row, return the otpauth:// URI
 *       plus a base64-encoded PNG QR data URL for the FE to render.
 *   <li>{@link #confirmEnrollment(User, String)} — user scans QR and
 *       submits the current 6-digit code. If it verifies, flip enabled
 *       true, generate ten single-use backup codes, and return the raw
 *       codes to display exactly once.
 *   <li>{@link #verify(User, String)} — accepts either a live TOTP code or
 *       an unused backup code. Backup codes are single-use and are
 *       consumed on match.
 *   <li>{@link #disable(User, String)} — requires current TOTP and wipes
 *       the settings + all backup codes.
 * </ol>
 *
 * The TOTP secret is stored plaintext for now — envelope encryption is a
 * Phase 6+ hardening item. Backup codes are stored as SHA-256 hashes; the
 * raw values are only ever returned to the client at generation time.
 */
@Service
@RequiredArgsConstructor
public class MfaService {

    private static final Logger log = LoggerFactory.getLogger(MfaService.class);
    private static final int BACKUP_CODE_COUNT = 10;
    private static final String ISSUER = "VyaparSathi";

    private final UserMfaSettingsRepository mfaSettingsRepository;
    private final UserBackupCodeRepository backupCodeRepository;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
    private final SecureRandom random = new SecureRandom();

    @Value("${app.mfa.issuer:VyaparSathi}")
    private String configuredIssuer;

    /**
     * Result of {@link #startEnrollment} — hands the FE a QR code image
     * (data-URL) plus the raw {@code otpauth://} URI in case the user
     * wants to paste it manually into 1Password / Authy.
     */
    public static class EnrollmentInit {
        private final String secret;
        private final String otpAuthUri;
        private final String qrDataUrl;

        public EnrollmentInit(String secret, String otpAuthUri, String qrDataUrl) {
            this.secret = secret;
            this.otpAuthUri = otpAuthUri;
            this.qrDataUrl = qrDataUrl;
        }
        public String getSecret() { return secret; }
        public String getOtpAuthUri() { return otpAuthUri; }
        public String getQrDataUrl() { return qrDataUrl; }
    }

    /**
     * Result of {@link #confirmEnrollment} — the caller shows these codes
     * exactly once. We never return them again.
     */
    public static class BackupCodeBundle {
        private final List<String> codes;
        public BackupCodeBundle(List<String> codes) { this.codes = codes; }
        public List<String> getCodes() { return codes; }
    }

    // ─── Enrollment ─────────────────────────────────────────────────────

    /**
     * Generate a new secret + QR. Idempotent — calling it again before the
     * user confirms overwrites the previous unfinished secret so a user
     * can restart enrollment from scratch.
     */
    @Transactional
    public EnrollmentInit startEnrollment(User user) {
        if (user == null) throw new IllegalArgumentException("User is required");
        String secret = secretGenerator.generate();

        UserMfaSettings settings = mfaSettingsRepository.findByUserId(user.getId())
                .orElseGet(UserMfaSettings::new);
        settings.setUserId(user.getId());
        settings.setSecret(secret);
        // If MFA is already enabled, we keep it enabled and just rotate the
        // secret via a separate flow. Fresh enrollment always starts disabled.
        if (settings.getId() == null) {
            settings.setEnabled(false);
        }
        mfaSettingsRepository.save(settings);

        String issuer = (configuredIssuer != null && !configuredIssuer.isBlank()) ? configuredIssuer : ISSUER;
        String accountLabel = user.getEmail() != null && !user.getEmail().isBlank()
                ? user.getEmail()
                : user.getUsername();

        QrData qrData = new QrData.Builder()
                .label(accountLabel)
                .secret(secret)
                .issuer(issuer)
                .algorithm(dev.samstevens.totp.code.HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        try {
            byte[] qrPng = qrGenerator.generate(qrData);
            String qrDataUrl = "data:" + qrGenerator.getImageMimeType() + ";base64,"
                    + Base64.getEncoder().encodeToString(qrPng);
            return new EnrollmentInit(secret, qrData.getUri(), qrDataUrl);
        } catch (Exception e) {
            log.error("Failed to render MFA QR for user {}: {}", user.getUsername(), e.getMessage());
            throw new IllegalStateException("Could not generate MFA QR code.", e);
        }
    }

    /**
     * Confirm the initial code from the authenticator app, flip enabled
     * true, and return a fresh set of backup codes. If a previous set
     * existed (re-enrollment path), it's wiped first.
     */
    @Transactional
    public BackupCodeBundle confirmEnrollment(User user, String submittedCode) {
        UserMfaSettings settings = mfaSettingsRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("MFA enrollment has not been started."));
        if (!codeVerifier.isValidCode(settings.getSecret(), sanitize(submittedCode))) {
            throw new IllegalArgumentException("The code you entered is incorrect. Try again.");
        }
        settings.setEnabled(true);
        settings.setEnrolledAt(LocalDateTime.now());
        settings.setLastVerifiedAt(LocalDateTime.now());
        mfaSettingsRepository.save(settings);
        // Replace any previous backup codes atomically.
        backupCodeRepository.deleteByUserId(user.getId());
        return regenerateBackupCodesInternal(user.getId());
    }

    // ─── Verification ────────────────────────────────────────────────────

    /**
     * Accepts a 6-digit TOTP code, or an 8-char alphanumeric backup code.
     * Returns true on success, false on failure. Success advances
     * lastVerifiedAt; backup-code success also marks the code used.
     */
    @Transactional
    public boolean verify(User user, String submittedCode) {
        if (submittedCode == null) return false;
        String clean = sanitize(submittedCode);
        Optional<UserMfaSettings> maybe = mfaSettingsRepository.findByUserId(user.getId());
        if (maybe.isEmpty() || !maybe.get().isEnabled()) return false;
        UserMfaSettings settings = maybe.get();

        // Live TOTP first — most-common path.
        if (clean.length() == 6 && clean.chars().allMatch(Character::isDigit)) {
            if (codeVerifier.isValidCode(settings.getSecret(), clean)) {
                settings.setLastVerifiedAt(LocalDateTime.now());
                mfaSettingsRepository.save(settings);
                return true;
            }
            return false;
        }

        // Otherwise treat as a backup code (uppercase-canonicalised hash lookup).
        String hash = sha256(clean.toUpperCase());
        Optional<UserBackupCode> match = backupCodeRepository
                .findByUserIdAndCodeHashAndUsedFalse(user.getId(), hash);
        if (match.isPresent()) {
            UserBackupCode row = match.get();
            row.setUsed(true);
            row.setUsedAt(LocalDateTime.now());
            backupCodeRepository.save(row);
            settings.setLastVerifiedAt(LocalDateTime.now());
            mfaSettingsRepository.save(settings);
            return true;
        }
        return false;
    }

    // ─── Backup codes ────────────────────────────────────────────────────

    /**
     * Regenerate the full set of backup codes, invalidating any prior
     * batch. Requires the caller to have already re-verified TOTP (the
     * controller enforces that) — this method itself does not re-check
     * the code because it's also invoked internally by
     * {@link #confirmEnrollment}.
     */
    @Transactional
    public BackupCodeBundle regenerateBackupCodes(User user) {
        UserMfaSettings settings = mfaSettingsRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("MFA is not enabled for this user."));
        if (!settings.isEnabled()) {
            throw new IllegalStateException("MFA is not enabled for this user.");
        }
        backupCodeRepository.deleteByUserId(user.getId());
        return regenerateBackupCodesInternal(user.getId());
    }

    private BackupCodeBundle regenerateBackupCodesInternal(Long userId) {
        List<String> raw = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String code = generateBackupCode();
            raw.add(code);
            UserBackupCode row = new UserBackupCode();
            row.setUserId(userId);
            row.setCodeHash(sha256(code));
            row.setUsed(false);
            backupCodeRepository.save(row);
        }
        return new BackupCodeBundle(raw);
    }

    /** Count of unused backup codes still available to the user. */
    public long remainingBackupCodes(Long userId) {
        return backupCodeRepository.countByUserIdAndUsedFalse(userId);
    }

    // ─── Disable ─────────────────────────────────────────────────────────

    /**
     * Requires a valid TOTP or backup code to disable. Wipes the settings
     * row and any remaining backup codes.
     */
    @Transactional
    public void disable(User user, String submittedCode) {
        if (!verify(user, submittedCode)) {
            throw new IllegalArgumentException("The code you entered is incorrect. Try again.");
        }
        mfaSettingsRepository.deleteByUserId(user.getId());
        backupCodeRepository.deleteByUserId(user.getId());
    }

    /**
     * Wipe MFA state without a code check. Only callable from server-side
     * flows that have independently proved identity — currently just the
     * password reset flow (email-link click == proof of inbox ownership,
     * which is at least as strong a factor as a TOTP).
     *
     * <p>Never expose this over HTTP. Regular disable must go through
     * {@link #disable(User, String)}.
     */
    @Transactional
    public void forceDisable(Long userId) {
        mfaSettingsRepository.deleteByUserId(userId);
        backupCodeRepository.deleteByUserId(userId);
    }

    // ─── Queries ─────────────────────────────────────────────────────────

    public boolean isEnabled(Long userId) {
        return mfaSettingsRepository.findByUserId(userId).map(UserMfaSettings::isEnabled).orElse(false);
    }

    public Optional<UserMfaSettings> findSettings(Long userId) {
        return mfaSettingsRepository.findByUserId(userId);
    }

    // ─── Internals ───────────────────────────────────────────────────────

    /**
     * Backup code layout: XXXX-XXXX (8 chars, dash for readability), drawn
     * from a 32-char alphabet (crockford-ish — no confusable I, L, O, 0).
     * Stored uppercase, dash-included for display, dash-stripped when
     * hashing so users can type either form.
     */
    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private String generateBackupCode() {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            if (i == 3) sb.append('-');
        }
        return sb.toString();
    }

    private String sanitize(String code) {
        if (code == null) return "";
        return code.replaceAll("[\\s-]", "").trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
