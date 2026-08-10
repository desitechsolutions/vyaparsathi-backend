package com.desitech.vyaparsathi.platform.controller;

import com.desitech.vyaparsathi.platform.dto.PlatformDetailsDto;
import com.desitech.vyaparsathi.platform.service.PlatformDetailsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PlatformDetailsController {

    private final PlatformDetailsService platformDetailsService;

    /**
     * PUBLIC ENDPOINT: Fetch platform vendor details and bank info for tax invoice & public display.
     */
    @GetMapping("/api/v1/public/platform-info")
    public ResponseEntity<PlatformDetailsDto> getPublicPlatformInfo() {
        return ResponseEntity.ok(platformDetailsService.getPlatformDetails());
    }

    /**
     * SUPER_ADMIN ENDPOINT: Fetch platform configuration for SuperAdmin management console.
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/api/admin/platform")
    public ResponseEntity<PlatformDetailsDto> getAdminPlatformDetails() {
        return ResponseEntity.ok(platformDetailsService.getPlatformDetails());
    }

    /**
     * SUPER_ADMIN ENDPOINT: Update platform configuration.
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/api/admin/platform")
    public ResponseEntity<PlatformDetailsDto> updateAdminPlatformDetails(@Valid @RequestBody PlatformDetailsDto dto) {
        PlatformDetailsDto updated = platformDetailsService.updatePlatformDetails(dto);
        return ResponseEntity.ok(updated);
    }
}
