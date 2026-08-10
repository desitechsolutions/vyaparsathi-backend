package com.desitech.vyaparsathi.platform.controller;

import com.desitech.vyaparsathi.auth.security.CustomUserDetails;
import com.desitech.vyaparsathi.platform.dto.ExecutiveMetricsDto;
import com.desitech.vyaparsathi.platform.dto.ImpersonationResponseDto;
import com.desitech.vyaparsathi.platform.dto.Shop360Dto;
import com.desitech.vyaparsathi.platform.service.ImpersonationService;
import com.desitech.vyaparsathi.platform.service.SuperAdminOperationsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/operations")
@RequiredArgsConstructor
public class SuperAdminOperationsController {

    private final SuperAdminOperationsService operationsService;
    private final ImpersonationService impersonationService;

    @GetMapping("/metrics")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TECH_ADMIN', 'BILLING_ADMIN')")
    public ResponseEntity<ExecutiveMetricsDto> getExecutiveMetrics() {
        return ResponseEntity.ok(operationsService.getExecutiveMetrics());
    }

    @GetMapping("/shops/{shopId}/360")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TECH_ADMIN', 'SUPPORT_AGENT')")
    public ResponseEntity<Shop360Dto> getShop360(@PathVariable Long shopId) {
        return ResponseEntity.ok(operationsService.getShop360(shopId));
    }

    @PostMapping("/shops/{shopId}/lifecycle")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TECH_ADMIN')")
    public ResponseEntity<String> updateShopLifecycle(
            @PathVariable Long shopId,
            @RequestBody LifecycleRequest request,
            @AuthenticationPrincipal CustomUserDetails admin) {

        operationsService.updateShopLifecycleStatus(
                shopId,
                request.isActive(),
                request.getReason(),
                admin.getId(),
                admin.getUsername()
        );
        return ResponseEntity.ok("Shop lifecycle status updated successfully.");
    }

    @PostMapping("/impersonate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TECH_ADMIN', 'SUPPORT_AGENT')")
    public ResponseEntity<ImpersonationResponseDto> startImpersonation(
            @RequestBody ImpersonationRequest request,
            @AuthenticationPrincipal CustomUserDetails admin,
            HttpServletRequest servletRequest) {

        String ip = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(impersonationService.startImpersonation(
                request.getTargetShopId(),
                request.getTargetUserId(),
                request.getReason(),
                admin.getId(),
                admin.getUsername(),
                ip
        ));
    }

    @PostMapping("/impersonate/exit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TECH_ADMIN', 'SUPPORT_AGENT')")
    public ResponseEntity<String> exitImpersonation(
            @RequestParam String sessionUuid,
            @AuthenticationPrincipal CustomUserDetails admin) {

        impersonationService.exitImpersonation(sessionUuid, admin.getId(), admin.getUsername());
        return ResponseEntity.ok("Impersonation session exited successfully.");
    }

    @Data
    public static class LifecycleRequest {
        private boolean active;
        private String reason;
    }

    @Data
    public static class ImpersonationRequest {
        private Long targetShopId;
        private Long targetUserId;
        private String reason;
    }
}
