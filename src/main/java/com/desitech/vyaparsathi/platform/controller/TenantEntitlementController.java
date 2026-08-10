package com.desitech.vyaparsathi.platform.controller;

import com.desitech.vyaparsathi.auth.security.CustomUserDetails;
import com.desitech.vyaparsathi.platform.entity.TenantLimitOverride;
import com.desitech.vyaparsathi.platform.service.PlatformEntitlementService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/entitlements")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TECH_ADMIN')")
public class TenantEntitlementController {

    private final PlatformEntitlementService entitlementService;

    @GetMapping("/shops/{shopId}")
    public ResponseEntity<List<TenantLimitOverride>> getShopLimitOverrides(@PathVariable Long shopId) {
        return ResponseEntity.ok(entitlementService.getTenantLimitOverrides(shopId));
    }

    @PostMapping("/shops/{shopId}/override")
    public ResponseEntity<String> addLimitOverride(
            @PathVariable Long shopId,
            @RequestBody LimitOverrideRequest request,
            @AuthenticationPrincipal CustomUserDetails admin) {

        entitlementService.addLimitOverride(
                shopId,
                request.getResourceKey(),
                request.getOverrideLimit(),
                request.getEndDate(),
                request.getReason(),
                admin.getId(),
                admin.getUsername()
        );
        return ResponseEntity.ok("Entitlement limit override applied successfully.");
    }

    @Data
    public static class LimitOverrideRequest {
        private String resourceKey;
        private int overrideLimit;
        private LocalDateTime endDate;
        private String reason;
    }
}
