
package com.desitech.vyaparsathi.shop.controller;
import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.security.CustomUserDetails;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.desitech.vyaparsathi.auth.service.SessionService;
import com.desitech.vyaparsathi.shop.dto.ShopDto;
import com.desitech.vyaparsathi.shop.service.ShopService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/api/shop")
public class ShopOnboardingController {

    private static final Logger logger = LoggerFactory.getLogger(ShopOnboardingController.class);
    private static final String COOKIE_NAME = "refreshToken";
    private static final Duration REFRESH_COOKIE_TTL = Duration.ofDays(7);

    @Value("${app.auth.cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${app.auth.cookie.same-site:None}")
    private String cookieSameSite;

    @Value("${app.auth.cookie.path:/}")
    private String cookiePath;

    @Autowired
    private ShopService shopService;

    @Autowired
    private SessionService sessionService;

    /**
     * Endpoint to set up the initial shop.
     * Accessible only to the 'OWNER' role.
     * @param dto The DTO containing shop details.
     * @return The created ShopDto.
     */
    @PostMapping(value = "/onboarding", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PENDING_OWNER') or hasRole('OWNER')")
    public ResponseEntity<ShopDto> completeOnboarding(
            @Valid @ModelAttribute ShopDto dto, // ModelAttribute for form-data
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {

        User currentUser = userDetails.getUser();

        // Null-safe logging — avoid NPE
        String shopInfo = (currentUser.getShop() != null)
                ? "shopId=" + currentUser.getShop().getId()
                : "no shop yet";

        logger.info("current user from security context: {}, {}",
                currentUser.getUsername(), shopInfo);

        // This check is already good — prevents re-onboarding
        if (currentUser.getShop() != null) {
            throw new IllegalStateException("Shop already exists");
        }

        // Delegate to service. Build session metadata here so the fresh
        // OWNER session created post-onboarding has a proper device label
        // and shows up correctly in Active Sessions.
        var sessionMetadata = sessionService.newSessionMetadata(httpRequest);
        ShopDto createdShop = shopService.completeOnboarding(dto, currentUser, logo, sessionMetadata);

        logger.info("Onboarding completed for user={}, shopId={}",
                currentUser.getUsername(), createdShop.getId());

        // The refresh token is set as an HttpOnly cookie (never in the JSON body)
        // so that JavaScript cannot read it — this is the standard defence against
        // XSS-based session hijacking. The cookie attributes mirror AuthController
        // so the browser handles both the login and the onboarding cookie identically.
        String refreshToken = createdShop.getRefreshToken();
        if (refreshToken != null && !refreshToken.isBlank()) {
            ResponseCookie refreshCookie = ResponseCookie.from(COOKIE_NAME, refreshToken)
                    .httpOnly(true)
                    .secure(cookieSecure)
                    .path(cookiePath)
                    .sameSite(cookieSameSite)
                    .maxAge(REFRESH_COOKIE_TTL)
                    .build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                    .body(createdShop);
        }

        return ResponseEntity.ok(createdShop);
    }

    /**
     * Create a second (or subsequent) shop for an already-onboarded user.
     * MULTI-STORE-1 fix.
     *
     * <p>Requires OWNER role. The new shop is created with the caller as OWNER
     * and a membership row is added without changing their primary shop.
     * The response contains a JWT scoped to the new shop so the FE can
     * immediately switch to it.
     */
    @PostMapping(value = "/additional", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ShopDto> createAdditionalShop(
            @Valid @ModelAttribute ShopDto dto,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {
        User currentUser = userDetails.getUser();
        logger.info("Additional shop creation requested by user={}", currentUser.getUsername());

        var sessionMetadata = sessionService.newSessionMetadata(httpRequest);
        ShopDto createdShop = shopService.createAdditionalShop(dto, currentUser, logo, sessionMetadata);

        logger.info("Additional shop created: shopId={} for user={}", createdShop.getId(), currentUser.getUsername());

        String refreshToken = createdShop.getRefreshToken();
        if (refreshToken != null && !refreshToken.isBlank()) {
            ResponseCookie refreshCookie = ResponseCookie.from(COOKIE_NAME, refreshToken)
                    .httpOnly(true)
                    .secure(cookieSecure)
                    .path(cookiePath)
                    .sameSite(cookieSameSite)
                    .maxAge(REFRESH_COOKIE_TTL)
                    .build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                    .body(createdShop);
        }
        return ResponseEntity.ok(createdShop);
    }

    /**
     * Endpoint to update an existing shop's details including branding.
     * Accessible only to the 'OWNER' role.
     * @param dto The DTO containing updated shop details.
     * @return The updated ShopDto.
     */
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ShopDto> updateShop(
            @RequestPart("shop") @Valid ShopDto dto,
            @RequestPart(value = "logo", required = false) MultipartFile logo,
            @RequestPart(value = "signature", required = false) MultipartFile signature,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        try {
            User currentUser = userDetails.getUser();

            // Ensure the user actually has a shop to update
            if (currentUser.getShop() == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Delegate to service to handle DB updates and File Storage
            ShopDto result = shopService.updateShop(currentUser.getShop().getId(), dto, logo, signature);

            logger.info("Shop settings updated for shopId={} by user={}",
                    currentUser.getShop().getId(), currentUser.getUsername());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error updating shop settings: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to update shop settings", e);
        }
    }

    /**
     * Endpoint to retrieve the shop details.
     * Accessible to any authenticated user.
     * @return The ShopDto for the current shop.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ShopDto> getShop() {
        try {
            ShopDto result = shopService.getShop();

            // If service returns null → means no shop yet (onboarding case)
            if (result == null) {
                logger.debug("No shop found for current user – returning 204 No Content");
                return ResponseEntity.noContent().build();  // 204 No Content
            }

            logger.info("Fetched shop details");
            return ResponseEntity.ok(result);

        } catch (EntityNotFoundException e) {
            // If shop ID was set but not found (rare edge case)
            logger.warn("Shop not found in DB for current context", e);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Unexpected error fetching shop details", e);
            throw new ApplicationException("Failed to fetch shop details", e);
        }
    }

    /**
     * Checks if a shop code (slug) is already taken.
     * Used during onboarding to prevent duplicate shop URLs.
     * @param code The slug to check.
     * @return 200 OK if available, 409 Conflict if taken.
     */
    @GetMapping("/check-code")
    @PreAuthorize("hasRole('PENDING_OWNER') or hasRole('OWNER')")
    public ResponseEntity<Boolean> checkShopCode(@RequestParam("code") String code) {
        logger.debug("Checking availability for shop code: {}", code);

        boolean exists = shopService.existsByCode(code);

        if (exists) {
            logger.warn("Shop code collision detected: {}", code);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(true);
        }
        return ResponseEntity.ok(false);
    }
}