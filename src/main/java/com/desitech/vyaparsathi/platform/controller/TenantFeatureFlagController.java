package com.desitech.vyaparsathi.platform.controller;

import com.desitech.vyaparsathi.auth.security.CustomUserDetails;
import com.desitech.vyaparsathi.platform.entity.PlatformFeatureFlag;
import com.desitech.vyaparsathi.platform.entity.TenantFeatureFlag;
import com.desitech.vyaparsathi.platform.service.PlatformEntitlementService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/feature-flags")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TECH_ADMIN')")
public class TenantFeatureFlagController {

    private final PlatformEntitlementService entitlementService;

    @GetMapping("/platform")
    public ResponseEntity<List<PlatformFeatureFlag>> getPlatformFlags() {
        return ResponseEntity.ok(entitlementService.getAllPlatformFeatureFlags());
    }

    @GetMapping("/shops/{shopId}")
    public ResponseEntity<List<TenantFeatureFlag>> getShopFlags(@PathVariable Long shopId) {
        return ResponseEntity.ok(entitlementService.getTenantFeatureOverrides(shopId));
    }

    @PostMapping("/shops/{shopId}/override")
    public ResponseEntity<String> setFeatureOverride(
            @PathVariable Long shopId,
            @RequestBody FeatureOverrideRequest request,
            @AuthenticationPrincipal CustomUserDetails admin) {

        entitlementService.setTenantFeatureOverride(
                shopId,
                request.getFeatureKey(),
                request.isEnabled(),
                admin.getId(),
                admin.getUsername()
        );
        return ResponseEntity.ok("Feature flag override saved successfully.");
    }

    @Data
    public static class FeatureOverrideRequest {
        private String featureKey;
        private boolean enabled;
    }
}
