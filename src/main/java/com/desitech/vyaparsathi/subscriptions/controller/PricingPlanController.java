package com.desitech.vyaparsathi.subscriptions.controller;

import com.desitech.vyaparsathi.subscriptions.dto.PricingPlanDTO;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import com.desitech.vyaparsathi.subscriptions.service.PricingPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pricing")
@RequiredArgsConstructor
public class PricingPlanController {

    private final PricingPlanService pricingPlanService;

    /**
     * Public endpoint: Fetches plans for the landing page pricing cards.
     * Uses optimized JOIN FETCH to prevent N+1 issues.
     */
    @GetMapping("/active")
    public ResponseEntity<List<PricingPlanDTO>> getActivePlans() {
        return ResponseEntity.ok(pricingPlanService.getActivePlans());
    }

    /**
     * Admin endpoint: Updates plan details like price, features, or discount.
     * Restricted to SUPER_ADMIN as per your security requirements.
     */
    @PutMapping("/admin/update")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PricingPlanDTO> updatePlan(@RequestBody PricingPlanDTO dto) {
        // Business logic and persistence are handled inside the service
        return ResponseEntity.ok(pricingPlanService.saveOrUpdatePlan(dto));
    }

    /**
     * Optional: Fetch a single plan's details for the Admin Edit form.
     */
    @GetMapping("/admin/{tier}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PricingPlanDTO> getPlanDetails(@PathVariable Tier tier) {
        // This ensures the admin sees all features and settings for a specific tier
        return ResponseEntity.ok(pricingPlanService.getPlanByTier(tier));
    }
}