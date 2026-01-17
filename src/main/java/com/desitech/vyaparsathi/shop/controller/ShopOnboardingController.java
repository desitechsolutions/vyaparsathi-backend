
package com.desitech.vyaparsathi.shop.controller;
import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.security.CustomUserDetails;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.desitech.vyaparsathi.shop.dto.ShopDto;
import com.desitech.vyaparsathi.shop.service.ShopService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shop")
public class ShopOnboardingController {

    private static final Logger logger = LoggerFactory.getLogger(ShopOnboardingController.class);

    @Autowired
    private ShopService shopService;

    /**
     * Endpoint to set up the initial shop.
     * Accessible only to the 'OWNER' role.
     * @param dto The DTO containing shop details.
     * @return The created ShopDto.
     */
    @PostMapping("/onboarding")
    @PreAuthorize("hasRole('PENDING_OWNER')")
    public ResponseEntity<ShopDto> completeOnboarding(
            @Valid @RequestBody ShopDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

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

        // Delegate to service
        ShopDto createdShop = shopService.completeOnboarding(dto, currentUser);

        logger.info("Onboarding completed for user={}, shopId={}",
                currentUser.getUsername(), createdShop.getId());

        return ResponseEntity.ok(createdShop);
    }

    /**
     * Endpoint to update an existing shop's details.
     * Accessible only to the 'OWNER' role.
     * @param dto The DTO containing updated shop details.
     * @return The updated ShopDto.
     */
    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ShopDto> updateShop(@Valid @RequestBody ShopDto dto) {
        try {
            ShopDto result = shopService.updateShop(dto);
            logger.info("Updated shop with name={}", dto.getName());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error updating shop with name={}: {}", dto.getName(), e.getMessage(), e);
            throw new ApplicationException("Failed to update shop", e);
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
}