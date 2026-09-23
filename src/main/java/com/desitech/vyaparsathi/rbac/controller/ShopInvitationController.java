package com.desitech.vyaparsathi.rbac.controller;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.payload.ApiResponse;
import com.desitech.vyaparsathi.rbac.annotation.RequirePermission;
import com.desitech.vyaparsathi.rbac.dto.ShopInvitationDto;
import com.desitech.vyaparsathi.rbac.entity.ShopInvitation;
import com.desitech.vyaparsathi.rbac.service.ShopInvitationService;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
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
import java.util.Optional;

/**
 * Shop-scoped staff invitation endpoints.
 *
 *   Owner / admin (needs TEAM_INVITE):
 *     POST   /api/shop/invitations                — invite by email
 *     GET    /api/shop/invitations                — list every invitation for the current shop
 *     DELETE /api/shop/invitations/{id}           — revoke a pending invite
 *
 *   Anyone (needs the token in the URL):
 *     GET    /api/shop/invitations/lookup?token=  — preview (shop name / role / inviter) before accepting
 *     POST   /api/shop/invitations/accept         — accept + auto-login
 */
@RestController
@RequestMapping("/api/shop/invitations")
@RequiredArgsConstructor
public class ShopInvitationController {

    private static final Logger log = LoggerFactory.getLogger(ShopInvitationController.class);

    private static final String COOKIE_NAME = "refreshToken";
    private static final String COOKIE_SAME_SITE = "None";
    private static final Duration REFRESH_COOKIE_TTL = Duration.ofDays(7);

    private final ShopInvitationService invitationService;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final JwtUtil jwtUtil;
    private final com.desitech.vyaparsathi.auth.service.RefreshTokenService refreshTokenService;
    private final com.desitech.vyaparsathi.auth.service.SessionService sessionService;

    // ─── Admin endpoints ─────────────────────────────────────────────────

    @PostMapping
    @RequirePermission("TEAM_INVITE")
    public ShopInvitationDto invite(@org.springframework.web.bind.annotation.RequestBody InviteRequest req,
                                    Authentication auth) {
        Long shopId = TenantContext.getCurrentShopId();
        if (shopId == null) throw new IllegalStateException("No active shop context.");
        User inviter = currentUser(auth);
        // createInvitation now returns the persisted entity so we skip the
        // secondary list+filter round-trip (removes the RBAC-2 race window).
        ShopInvitation created = invitationService.createInvitation(
                shopId, req.getEmail(), req.getPhone(), req.getRoleName(), req.getMessage(), inviter);
        return toDto(created);
    }

    @GetMapping
    @RequirePermission("TEAM_VIEW")
    public List<ShopInvitationDto> list() {
        Long shopId = TenantContext.getCurrentShopId();
        if (shopId == null) throw new IllegalStateException("No active shop context.");
        return invitationService.listForShop(shopId).stream().map(this::toDto).toList();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("TEAM_INVITE")
    public ApiResponse<Void> revoke(@PathVariable Long id, Authentication auth) {
        Long shopId = TenantContext.getCurrentShopId();
        if (shopId == null) throw new IllegalStateException("No active shop context.");
        User actor = currentUser(auth);
        invitationService.revoke(id, shopId, actor);
        return new ApiResponse<>("success", "Invitation revoked.", null);
    }

    // ─── Public endpoints (invite recipient) ─────────────────────────────

    @GetMapping("/lookup")
    public ResponseEntity<?> lookup(@RequestParam("token") String rawToken) {
        Optional<ShopInvitation> maybe = invitationService.findByRawToken(rawToken);
        if (maybe.isEmpty()) {
            return ResponseEntity.status(404).body(new ApiResponse<>("error",
                    "This invitation link is invalid.", null));
        }
        ShopInvitation inv = maybe.get();
        if (inv.getStatus() != ShopInvitation.Status.PENDING || inv.isExpired()) {
            return ResponseEntity.status(410).body(new ApiResponse<>("error",
                    "This invitation link is no longer valid.", null));
        }
        Map<String, Object> body = new HashMap<>();
        body.put("email", inv.getEmail());
        body.put("roleName", inv.getRoleName());
        body.put("inviterName", inv.getInviterName());
        body.put("message", inv.getMessage());
        body.put("expiresAt", inv.getExpiresAt());
        Shop shop = shopRepository.findById(inv.getShopId()).orElse(null);
        body.put("shopName", shop != null ? shop.getName() : null);
        body.put("shopCode", shop != null ? shop.getCode() : null);
        // If the email already has an account, tell the FE so it can skip the
        // password field and show "just click to join" UX.
        body.put("userExists", userRepository.findByEmail(inv.getEmail()).isPresent());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/accept")
    public ResponseEntity<?> accept(@org.springframework.web.bind.annotation.RequestBody AcceptRequest req,
                                    jakarta.servlet.http.HttpServletRequest httpRequest) {
        try {
            // acceptInvitation now returns both the user AND the shopId from the
            // invitation (not user.getShop()) so the JWT is correct for existing
            // users joining a second shop (RBAC-4 fix).
            ShopInvitationService.AcceptResult result = invitationService.acceptInvitation(
                    req.getToken(), req.getFirstName(), req.getLastName(), req.getPassword());
            User user = result.user();
            Long acceptedShopId = result.shopId();

            // Auto-login: hand back an access token + refresh cookie so the FE
            // can drop straight into the app once the invite is accepted.
            var sessionMetadata = sessionService.newSessionMetadata(httpRequest);
            String accessToken = jwtUtil.generateAccessToken(user, acceptedShopId,
                    sessionMetadata.getSessionId());
            String refreshToken = refreshTokenService.createRefreshToken(user.getUsername(), sessionMetadata).getToken();

            ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, refreshToken)
                    .httpOnly(true).secure(true).path("/")
                    .sameSite(COOKIE_SAME_SITE).maxAge(REFRESH_COOKIE_TTL)
                    .build();

            Map<String, Object> body = new HashMap<>();
            body.put("accessToken", accessToken);
            body.put("role", user.getRole() != null ? user.getRole().name() : null);
            body.put("username", user.getUsername());
            body.put("shopId", acceptedShopId);
            log.info("Shop invitation accepted by {} for shop {}", user.getUsername(), acceptedShopId);
            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(body);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>("error", e.getMessage(), null));
        }
    }

    // ─── Internals ───────────────────────────────────────────────────────

    private User currentUser(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new IllegalStateException("Not authenticated.");
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new IllegalStateException("User not found."));
    }

    private ShopInvitationDto toDto(ShopInvitation inv) {
        Shop shop = shopRepository.findById(inv.getShopId()).orElse(null);
        return ShopInvitationDto.builder()
                .id(inv.getId())
                .shopId(inv.getShopId())
                .shopName(shop != null ? shop.getName() : null)
                .email(inv.getEmail())
                .phone(inv.getPhone())
                .roleName(inv.getRoleName())
                .status(inv.getStatus().name())
                .inviterName(inv.getInviterName())
                .createdAt(inv.getCreatedAt())
                .expiresAt(inv.getExpiresAt())
                .acceptedAt(inv.getAcceptedAt())
                .revokedAt(inv.getRevokedAt())
                .message(inv.getMessage())
                .build();
    }

    // ─── Wire DTOs ───────────────────────────────────────────────────────

    @Data
    public static class InviteRequest {
        @NotBlank @Email
        private String email;
        private String phone;
        @NotBlank
        private String roleName;
        private String message;
    }

    @Data
    public static class AcceptRequest {
        @NotBlank
        private String token;
        private String firstName;
        private String lastName;
        /** Only required when the invitee's email is not yet a user. */
        private String password;
    }
}
