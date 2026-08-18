package com.desitech.vyaparsathi.auth.service;

import com.desitech.vyaparsathi.auth.dto.AuthResponse;
import com.desitech.vyaparsathi.auth.dto.RegisterRequest;
import com.desitech.vyaparsathi.auth.entity.PasswordResetToken;
import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.entity.RefreshToken;
import com.desitech.vyaparsathi.auth.model.Role;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.common.annotations.LogAudit;
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

    @Autowired
    private PasswordHistoryService passwordHistoryService;

    /**
     * When true, users with unverified emails are blocked at login with a
     * clear message. Defaults to false so dev environments where SMTP is
     * unreachable don't lock everybody out — flip to true in staging/prod
     * once the verification email path is confirmed working.
     */
    @Value("${auth.require-email-verified:false}")
    private boolean requireEmailVerified;

    @Autowired
    private MfaService mfaService;

    /**
     * Two-value result from {@link #authenticateForMfaOrToken}: either an
     * access token (MFA not enabled) or a short-lived MFA challenge token
     * the caller must exchange for a real access token by verifying a
     * TOTP / backup code.
     */
    public static class LoginOutcome {
        private final boolean mfaRequired;
        private final String token;

        private LoginOutcome(boolean mfaRequired, String token) {
            this.mfaRequired = mfaRequired;
            this.token = token;
        }
        public static LoginOutcome accessToken(String token) { return new LoginOutcome(false, token); }
        public static LoginOutcome mfaChallenge(String challenge) { return new LoginOutcome(true, challenge); }
        public boolean isMfaRequired() { return mfaRequired; }
        public String getToken() { return token; }
    }

    /**
     * Authenticate user by PIN and generate access token.
     *
     * Enforces a soft account lockout: after {@value #MAX_FAILED_LOGIN_ATTEMPTS}
     * consecutive failed attempts, the account is locked for
     * {@value #LOCKOUT_DURATION_MINUTES} minutes. Successful auth resets the
     * counter. Records {@code lastLoginAt} on success and emits an audit event
     * on both success and failure (via {@link LogAudit}).
     */
    @LogAudit(action = "USER_LOGIN", entity = "USER")
    @Transactional
    public String authenticateAndGenerateToken(User user, String pin) {
        return authenticateAndGenerateToken(user, pin, null);
    }

    /**
     * Session-aware login: same rules as the legacy overload, but the
     * caller supplies a pre-generated {@code sessionId} that will be
     * embedded as the {@code sid} claim in the access token. Only
     * called from {@link #authenticateForLoginWithSession(User, String, String)}.
     */
    @LogAudit(action = "USER_LOGIN", entity = "USER")
    @Transactional
    public String authenticateAndGenerateToken(User user, String pin, String sessionId) {
        if (!user.isActive()) {
            throw new UserInactiveException("User account is not active");
        }

        // Email-verification gate — flag-controlled so dev boxes without SMTP
        // aren't broken by unverified accounts. SUPER_ADMIN/TECH_ADMIN/etc.
        // are exempt so platform staff can always reach the console.
        if (requireEmailVerified
                && !user.isEmailVerified()
                && user.getRole() != Role.SUPER_ADMIN
                && user.getRole() != Role.TECH_ADMIN
                && user.getEmail() != null
                && !user.getEmail().isBlank()) {
            throw new UserInactiveException(
                    "Please verify your email before signing in. Check your inbox for the verification link, or request a new one.");
        }

        if (isLocked(user)) {
            long secs = java.time.Duration.between(LocalDateTime.now(), user.getLockedUntil()).getSeconds();
            long mins = Math.max(1, secs / 60);
            throw new UserInactiveException(
                    "Account is temporarily locked due to too many failed sign-in attempts. " +
                    "Try again in " + mins + " minute" + (mins == 1 ? "" : "s") + ".");
        }

        if (!passwordEncoder.matches(pin, user.getPasswordHash())) {
            recordFailedLogin(user);
            throw new BadCredentialsException("Invalid username or password");
        }

        // Shop-level MFA policy — OWNER + ADMIN on a shop with the policy
        // ON must have MFA enabled. Enforced AFTER the password check so a
        // random attacker can't probe which admins have this on.
        if (user.getShop() != null
                && Boolean.TRUE.equals(user.getShop().getRequireMfaForAdmins())
                && (user.getRole() == Role.OWNER || user.getRole() == Role.ADMIN)
                && !mfaService.isEnabled(user.getId())) {
            String recovery = user.getRole() == Role.OWNER
                    // OWNER can recover themselves — password reset also wipes
                    // MFA, so they can start over.
                    ? "To recover: click \"Forgot password?\" on the sign-in screen — the password reset also clears MFA, so you can sign in fresh and enroll again."
                    // Other admins go through the OWNER (who can disable the
                    // policy from Settings or reset the ADMIN's password).
                    : "Ask the shop OWNER to either disable the policy in Settings, or send you a password reset link (that will also reset your MFA so you can re-enroll).";
            throw new UserInactiveException(
                    "This shop requires two-factor authentication for owners and admins, and your account doesn't have it enabled. " + recovery);
        }

        // Successful auth — clear counters + record login
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        return jwtUtil.generateAccessToken(
                user,
                user.getShop() != null ? user.getShop().getId() : null,
                sessionId
        );
    }

    /**
     * Two-phase login entrypoint: runs the password check via
     * {@link #authenticateAndGenerateToken(User, String)} and, if the user
     * has MFA enabled, returns an MFA challenge token instead of an
     * access token. The caller (AuthController) turns the challenge token
     * around in the response so the FE routes into the MFA screen.
     *
     * We deliberately re-issue the access token via the same code path so
     * lockout counters and lastLoginAt updates fire on the password step
     * regardless of the MFA outcome.
     */
    @Transactional
    public LoginOutcome authenticateForLogin(User user, String password) {
        return authenticateForLoginWithSession(user, password, null);
    }

    /**
     * Session-aware variant of {@link #authenticateForLogin(User, String)}
     * — embeds {@code sessionId} into the issued access token's {@code sid}
     * claim so it can be per-session revoked.
     */
    @Transactional
    public LoginOutcome authenticateForLoginWithSession(User user, String password, String sessionId) {
        String accessToken = authenticateAndGenerateToken(user, password, sessionId);
        if (mfaService.isEnabled(user.getId())) {
            // Password succeeded but MFA is required. Hand back the short-lived
            // challenge token instead — the caller must complete MFA to earn
            // a real access token via /api/auth/mfa/verify.
            return LoginOutcome.mfaChallenge(jwtUtil.generateMfaChallengeToken(user));
        }
        return LoginOutcome.accessToken(accessToken);
    }

    /**
     * Called by MfaController after a successful TOTP / backup-code
     * verification. Regenerates an access token for the user
     * identified by the challenge token.
     */
    @Transactional
    public String generateAccessTokenForVerifiedMfa(User user) {
        return generateAccessTokenForVerifiedMfa(user, null);
    }

    @Transactional
    public String generateAccessTokenForVerifiedMfa(User user, String sessionId) {
        return jwtUtil.generateAccessToken(
                user,
                user.getShop() != null ? user.getShop().getId() : null,
                sessionId
        );
    }

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 15;

    private boolean isLocked(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private void recordFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            // Exponential backoff on repeat lockouts: 15m, 30m, 60m, ...
            int overflow = attempts - MAX_FAILED_LOGIN_ATTEMPTS;
            long minutes = (long) LOCKOUT_DURATION_MINUTES * (1L << Math.min(overflow, 4));
            user.setLockedUntil(LocalDateTime.now().plusMinutes(minutes));
            logger.warn("Account {} locked for {} minutes after {} failed attempts",
                    user.getUsername(), minutes, attempts);
        }
        userRepository.save(user);
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
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setActive(true);

        if (TenantContext.getCurrentShopId() != null) {
            Shop shop = shopRepository.findById(TenantContext.getCurrentShopId())
                    .orElseThrow(() -> new ApplicationException("Shop not found"));
            user.setShop(shop);
        }

        User saved = userRepository.save(user);
        passwordHistoryService.recordHash(saved.getId(), saved.getPasswordHash());
    }

    /**
     * Change password when user is logged in. Rejects reuse of the last
     * {@link PasswordHistoryService#HISTORY_WINDOW} passwords.
     */
    @LogAudit(action = "USER_CHANGE_PASSWORD", entity = "USER")
    @Transactional
    public void changeUserPin(String username, String currentPassword, String newPassword) {

        User user = getUserByUsername(username);

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Incorrect current password.");
        }

        if (passwordHistoryService.matchesHistory(user, newPassword)) {
            throw new BadCredentialsException(
                    "New password must not match any of your last "
                            + PasswordHistoryService.HISTORY_WINDOW + " passwords.");
        }

        // Invalidate every session on password change — denylist first
        // so any still-valid access tokens die instantly, then hard-
        // delete the refresh token rows so a stolen cookie is also
        // useless.
        sessionService.revokeAllForUser(username, "password-change");
        refreshTokenService.deleteByUsername(username);

        String newHash = passwordEncoder.encode(newPassword);
        user.setPasswordHash(newHash);
        user.setLastPasswordChangeAt(LocalDateTime.now());
        userRepository.save(user);
        passwordHistoryService.recordHash(user.getId(), newHash);
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

    @LogAudit(action = "USER_FORGOT_PASSWORD", entity = "USER")
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

    @LogAudit(action = "USER_RESET_PASSWORD", entity = "USER")
    @Transactional
    public void resetPassword(String token, String newPassword) {

        PasswordResetToken resetToken =
                resetTokenService.findByRawTokenUnfiltered(token)
                        .orElseThrow(() ->
                                new BadCredentialsException("Invalid reset token"));

        TokenValidator.validateToken(resetToken);

        User user = resetToken.getUser();

        if (user.getShop() != null) {
            TenantContext.setCurrentShopId(user.getShop().getId());
        }

        try {
            if (passwordHistoryService.matchesHistory(user, newPassword)) {
                throw new BadCredentialsException(
                        "New password must not match any of your last "
                                + PasswordHistoryService.HISTORY_WINDOW + " passwords.");
            }

            String newHash = passwordEncoder.encode(newPassword);
            user.setPasswordHash(newHash);
            user.setLastPasswordChangeAt(LocalDateTime.now());
            // Successful reset also clears any lockout.
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
            passwordHistoryService.recordHash(user.getId(), newHash);

            resetTokenService.markTokenAsUsed(token);

            // Password-reset-via-email is proof of inbox ownership, at least
            // as strong as MFA. Wipe MFA state so a user who lost access to
            // their authenticator (or whose shop turned on the require-MFA
            // policy while their MFA was somehow off) has a self-service
            // recovery path: reset password → sign in → set up MFA again.
            // Without this, a shop OWNER who lost their authenticator would
            // need a DBA to fix them.
            boolean mfaWiped = mfaService.isEnabled(user.getId());
            if (mfaWiped) {
                mfaService.forceDisable(user.getId());
                logger.info("MFA cleared for user {} as part of password reset", user.getUsername());
            }

            // Invalidate every session — same denylist-then-delete
            // sequence as password change, so stolen access tokens
            // die within milliseconds and stolen refresh cookies find
            // no matching row to refresh.
            sessionService.revokeAllForUser(user.getUsername(), "password-reset");
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

    /**
     * Rotate refresh + access identity for the session bound to the
     * supplied refresh token. Session_id is preserved across rotations
     * so the Active Sessions UI sees one continuous session per device.
     */
    @Transactional
    public RefreshToken rotateRefreshToken(String oldTokenStr) {
        return rotateRefreshToken(oldTokenStr, null);
    }

    @Transactional
    public RefreshToken rotateRefreshToken(String oldTokenStr, RefreshTokenService.SessionMetadata refreshedMetadata) {
        RefreshToken oldToken = refreshTokenService.validateAndGet(oldTokenStr);
        User user = getUserByUsername(oldToken.getUsername());

        if (!user.isActive()) {
            throw new UserInactiveException("User account is not active");
        }

        return refreshTokenService.rotateWithSameSession(oldToken, refreshedMetadata);
    }

    /**
     * Log out the current session by invalidating the refresh token.
     * Also denylists the current session_id so the still-valid access
     * token dies within milliseconds instead of waiting for its TTL.
     * Wrapped so we can attach {@link LogAudit} and emit a USER_LOGOUT event.
     */
    @LogAudit(action = "USER_LOGOUT", entity = "USER")
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        // Look up the session first — we need the session_id for the
        // denylist, and once the row is deleted it's too late.
        var maybeRow = refreshTokenService.findByToken(refreshToken);
        maybeRow.ifPresent(row -> {
            // Route through SessionService so we get the denylist
            // update + refresh-row soft-revoke in one place.
            sessionService.revokeSession(row.getUsername(), row.getSessionId(), "logout");
            // The revoke leaves the row in place with revoked_at set;
            // hard-delete on top so a stolen refresh cookie can't be
            // replayed even in the tiny window before the sweeper runs.
            refreshTokenService.deleteByToken(refreshToken);
        });
    }

    @Autowired
    private com.desitech.vyaparsathi.auth.service.SessionService sessionService;
}
