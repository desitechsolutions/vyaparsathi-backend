package com.desitech.vyaparsathi.shop.controller;

import com.desitech.vyaparsathi.shop.dto.GlobalShopSummaryDTO;
import com.desitech.vyaparsathi.shop.service.GlobalShopManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/shops")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class GlobalShopManagementController {

    private final GlobalShopManagementService shopManagementService;

    @GetMapping("/summary")
    public ResponseEntity<Page<GlobalShopSummaryDTO>> getShopDashboard(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(shopManagementService.getAllShopsForDashboard(pageable));
    }

    @PatchMapping("/{shopId}/status")
    public ResponseEntity<String> updateShopStatus(@PathVariable Long shopId, @RequestParam boolean active) {
        shopManagementService.toggleShopStatus(shopId, active);
        return ResponseEntity.ok("Shop status updated successfully");
    }
}