package com.desitech.vyaparsathi.rbac.service;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.model.Role;
import com.desitech.vyaparsathi.common.annotations.CheckSubscriptionLimit;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.auth.service.PasswordHistoryService;
import com.desitech.vyaparsathi.common.util.TemplateUtil;
import com.desitech.vyaparsathi.notification.service.EmailService;
import com.desitech.vyaparsathi.rbac.entity.ShopInvitation;
import com.desitech.vyaparsathi.rbac.repository.RoleRepository;
import com.desitech.vyaparsathi.rbac.repository.ShopInvitationRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * End-to-end lifecycle for shop-scoped staff invitations.
 *
 * Flow:
 *  1. {@link #createInvitation} — inviter (shop OWNER/ADMIN with TEAM_INVITE)
 *     picks an email + role + optional message. We generate a UUID token,
 *     store its SHA-256 hash, and email the raw token as a link.
 *  2. {@link #findByRawToken} — accept-invite FE loads the invitation to
 *     show shop name / role / inviter name before the user commits.
 *  3. {@link #acceptInvitation} — user submits (raw token, firstName,
 *     lastName, password). If a user already exists for the invited email
 *     we just add a membership; otherwise we create the user then add
 *     the membership. Idempotent — second click on the same link is
 *     rejected.
 *  4. {@link #revoke} — inviter cancels a pending invite.
 */
@Service
@RequiredArgsConstructor
public class ShopInvitationService {

    private static final Logger log = LoggerFactory.getLogger(ShopInvitationService.class);
    private static final int TTL_HOURS = 72;

    private final ShopInvitationRepository repository;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final RoleRepository roleRepository;
    private final MembershipService membershipService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordHistoryService passwordHistoryService;

    @Value("${app.accept-shop-invite-url:http://localhost:3000/accept-shop-invite}")
    private String acceptInviteUrl;

    // ─── Create ──────────────────────────────────────────────────────────

    /**
     * Creates an invitation and emails it. Returns the raw token only
     * (never persisted). Called by the shop-invitation controller.
     */
    @CheckSubscriptionLimit("STAFF")
    @Transactional
    public String createInvitation(Long shopId, String email, String phone, String roleName,
                                   String message, User inviter) {
        if (shopId == null) throw new IllegalArgumentException("shopId is required.");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email is required.");
        if (roleName == null || roleName.isBlank()) throw new IllegalArgumentException("Role is required.");

        // Role must exist for this shop (system-preset or custom).
        if (roleRepository.findByShopIdAndName(shopId, roleName).isEmpty()) {
            throw new IllegalArgumentException("Role '" + roleName + "' does not exist for this shop.");
        }

        // Reject a duplicate PENDING invite to the same email.
        if (repository.existsByShopIdAndEmailAndStatus(shopId, email, ShopInvitation.Status.PENDING)) {
            throw new IllegalStateException("An invitation is already pending for " + email + ".");
        }

        String rawToken = UUID.randomUUID().toString();
        ShopInvitation invite = new ShopInvitation();
        invite.setShopId(shopId);
        invite.setEmail(email.trim().toLowerCase());
        invite.setPhone(phone);
        invite.setRoleName(roleName);
        invite.setInvitedBy(inviter.getId());
        invite.setInviterName(buildInviterName(inviter));
        invite.setTokenHash(sha256Hex(rawToken));
        invite.setStatus(ShopInvitation.Status.PENDING);
        invite.setMessage(message);
        invite.setExpiresAt(LocalDateTime.now().plusHours(TTL_HOURS));
        repository.save(invite);

        try {
            sendInvitationEmail(invite, rawToken, shopId);
        } catch (MessagingException e) {
            // Non-fatal: the token still stands, and the caller can trigger a resend.
            log.error("Failed to send shop invitation email to {}: {}", email, e.getMessage());
        }

        return rawToken;
    }

    // ─── Lookup ──────────────────────────────────────────────────────────

    /**
     * Load an invitation by the RAW token (which is hashed inside).
     * Returns empty if not found. Callers must additionally check status +
     * expiry.
     */
    public Optional<ShopInvitation> findByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        return repository.findByTokenHash(sha256Hex(rawToken));
    }

    public List<ShopInvitation> listForShop(Long shopId) {
        return repository.findByShopIdOrderByCreatedAtDesc(shopId);
    }

    public List<ShopInvitation> listPendingForShop(Long shopId) {
        return repository.findByShopIdAndStatus(shopId, ShopInvitation.Status.PENDING);
    }

    // ─── Accept ──────────────────────────────────────────────────────────

    /**
     * Consume the invitation:
     *   - If the invitee's email matches an existing user, just add the
     *     membership (password field is ignored — they're already signed
     *     up somewhere).
     *   - Otherwise, create a fresh user (with the supplied password),
     *     mark the email verified (proof-of-inbox came via clicking the
     *     invite link), and add the membership.
     *
     * Returns the resulting user so the controller can hand back an
     * access token for immediate sign-in.
     */
    @Transactional
    public User acceptInvitation(String rawToken, String firstName, String lastName, String password) {
        ShopInvitation invite = findByRawToken(rawToken)
                .orElseThrow(() -> new IllegalArgumentException("This invitation link is invalid."));
        assertAcceptable(invite);

        // Existing user path — link an existing account to the new shop.
        Optional<User> existing = userRepository.findByEmail(invite.getEmail());
        User user;
        if (existing.isPresent()) {
            user = existing.get();
            log.info("Shop invitation accept: linking existing user {} to shop {}", user.getUsername(), invite.getShopId());
        } else {
            // New user path — email + password combo required.
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException("A password is required to complete sign-up.");
            }
            user = new User();
            user.setEmail(invite.getEmail());
            // Username defaults to the email for invitee-created accounts; can be
            // renamed later from profile settings.
            user.setUsername(invite.getEmail());
            user.setFirstName(firstName != null ? firstName : "");
            user.setLastName(lastName != null ? lastName : "");
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setRole(mapLegacyRoleForCompatibility(invite.getRoleName()));
            user.setActive(true);
            user.setEmailVerified(true); // clicking the invite link proves ownership
            user = userRepository.save(user);
            passwordHistoryService.recordHash(user.getId(), user.getPasswordHash());
            log.info("Shop invitation accept: created user {} and linked to shop {}", user.getUsername(), invite.getShopId());
        }

        // Attach the membership. Not the default shop unless the user has no shop yet.
        boolean makeDefault = user.getShop() == null;
        membershipService.addOrReactivate(user, invite.getShopId(), invite.getRoleName(), invite.getInvitedBy(), makeDefault);

        // If this became their default (no shop before), also point users.shop_id
        // at it so legacy code that reads user.getShop() picks it up.
        if (makeDefault) {
            shopRepository.findById(invite.getShopId()).ifPresent(user::setShop);
            userRepository.save(user);
        }

        invite.setStatus(ShopInvitation.Status.ACCEPTED);
        invite.setAcceptedAt(LocalDateTime.now());
        invite.setAcceptedByUserId(user.getId());
        repository.save(invite);

        return user;
    }

    // ─── Revoke ──────────────────────────────────────────────────────────

    @Transactional
    public void revoke(Long invitationId, User actor) {
        ShopInvitation invite = repository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found."));
        if (invite.getStatus() != ShopInvitation.Status.PENDING) {
            throw new IllegalStateException("Only pending invitations can be revoked.");
        }
        invite.setStatus(ShopInvitation.Status.REVOKED);
        invite.setRevokedAt(LocalDateTime.now());
        invite.setRevokedBy(actor.getId());
        repository.save(invite);
    }

    // ─── Internals ───────────────────────────────────────────────────────

    private void assertAcceptable(ShopInvitation invite) {
        if (invite.getStatus() == ShopInvitation.Status.ACCEPTED) {
            throw new IllegalStateException("This invitation has already been used.");
        }
        if (invite.getStatus() == ShopInvitation.Status.REVOKED) {
            throw new IllegalStateException("This invitation has been revoked by the inviter.");
        }
        if (invite.isExpired()) {
            invite.setStatus(ShopInvitation.Status.EXPIRED);
            repository.save(invite);
            throw new IllegalStateException("This invitation has expired. Ask the inviter to send a new one.");
        }
    }

    private void sendInvitationEmail(ShopInvitation invite, String rawToken, Long shopId) throws MessagingException {
        Shop shop = shopRepository.findById(shopId).orElse(null);
        Map<String, String> vars = new HashMap<>();
        vars.put("shopName", shop != null && shop.getName() != null ? shop.getName() : "your team");
        vars.put("inviterName", invite.getInviterName() != null ? invite.getInviterName() : "The team owner");
        vars.put("roleName", invite.getRoleName());
        vars.put("acceptLink", acceptInviteUrl + "?token=" + rawToken);
        vars.put("message", invite.getMessage() != null ? invite.getMessage() : "");
        vars.put("expiryHours", String.valueOf(TTL_HOURS));
        vars.put("currentYear", String.valueOf(LocalDateTime.now().getYear()));
        String html = TemplateUtil.loadTemplate("templates/shop-invitation.html", vars);
        emailService.sendEmail(invite.getEmail(),
                "You've been invited to " + (shop != null ? shop.getName() : "a VyaparSathi shop"), html);
    }

    private String buildInviterName(User inviter) {
        String first = inviter.getFirstName() != null ? inviter.getFirstName().trim() : "";
        String last = inviter.getLastName() != null ? inviter.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? inviter.getUsername() : full;
    }

    /**
     * Legacy User.role enum needs a value. Map the RBAC role name back to
     * the closest legacy enum so existing @PreAuthorize checks still fire
     * correctly until every controller is migrated to @RequirePermission.
     */
    private Role mapLegacyRoleForCompatibility(String rbacRoleName) {
        if (rbacRoleName == null) return Role.STAFF;
        return switch (rbacRoleName.toUpperCase()) {
            case "OWNER" -> Role.OWNER;
            case "ADMIN" -> Role.ADMIN;
            case "MANAGER", "ACCOUNTANT", "CASHIER", "VIEWER", "STAFF" -> Role.STAFF;
            default -> Role.STAFF;
        };
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
