package com.desitech.vyaparsathi.inventory.controller;

import com.desitech.vyaparsathi.auth.security.CustomUserDetails;
import com.desitech.vyaparsathi.inventory.entity.*;
import com.desitech.vyaparsathi.inventory.service.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Bundles, UOM, supplier-rate-card, alert-snooze, saved-views, barcode labels
 * — every enterprise gap from the final audit pass, grouped in a single
 * controller so the FE has one place to look. Each surface is small enough
 * that a dedicated controller per feature would just be noise.
 */
@RestController
@RequestMapping("/api/inventory-ent")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
public class InventoryEnterpriseController {

    private final ProductBundleService bundleService;
    private final UomConversionService uomService;
    private final SupplierRateCardService rateCardService;
    private final AlertSnoozeService snoozeService;
    private final SavedViewService savedViewService;
    private final BarcodeLabelService labelService;
    private final LowStockAlertNotificationScheduler lowStockScheduler;
    private final com.desitech.vyaparsathi.shop.repository.ShopRepository shopRepository;

    public InventoryEnterpriseController(ProductBundleService bundleService,
                                         UomConversionService uomService,
                                         SupplierRateCardService rateCardService,
                                         AlertSnoozeService snoozeService,
                                         SavedViewService savedViewService,
                                         BarcodeLabelService labelService,
                                         LowStockAlertNotificationScheduler lowStockScheduler,
                                         com.desitech.vyaparsathi.shop.repository.ShopRepository shopRepository) {
        this.bundleService = bundleService;
        this.uomService = uomService;
        this.rateCardService = rateCardService;
        this.snoozeService = snoozeService;
        this.savedViewService = savedViewService;
        this.labelService = labelService;
        this.lowStockScheduler = lowStockScheduler;
        this.shopRepository = shopRepository;
    }

    // ── Product bundles ────────────────────────────────────────────────

    @GetMapping("/bundles")
    public ResponseEntity<List<ProductBundle>> listBundles() {
        return ResponseEntity.ok(bundleService.listAll());
    }

    @PostMapping("/bundles")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<ProductBundle> createBundle(@RequestBody Map<String, Object> body) {
        Long variantId = Long.valueOf(body.get("bundleVariantId").toString());
        String name = String.valueOf(body.getOrDefault("bundleName", ""));
        String notes = body.get("notes") != null ? body.get("notes").toString() : null;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) body.getOrDefault("components", List.of());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bundleService.create(variantId, name, components, notes));
    }

    @PostMapping("/bundles/{id}/deactivate")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<ProductBundle> deactivateBundle(@PathVariable Long id) {
        return ResponseEntity.ok(bundleService.deactivate(id));
    }

    // ── UOM conversion ─────────────────────────────────────────────────

    @GetMapping("/uom")
    public ResponseEntity<List<UomConversion>> listUom() {
        return ResponseEntity.ok(uomService.listAll());
    }

    @PostMapping("/uom")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<UomConversion> saveUom(@RequestBody UomConversion payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(uomService.save(payload));
    }

    @GetMapping("/uom/convert")
    public ResponseEntity<Map<String, Object>> convert(@RequestParam BigDecimal qty,
                                                       @RequestParam String from,
                                                       @RequestParam String to) {
        BigDecimal result = uomService.convert(qty, from, to);
        return ResponseEntity.ok(Map.of("input", qty, "fromUnit", from, "toUnit", to, "result", result));
    }

    // ── Supplier rate card ─────────────────────────────────────────────

    @GetMapping("/rate-card/supplier/{supplierId}")
    public ResponseEntity<List<SupplierRateCard>> listRateCards(@PathVariable Long supplierId) {
        return ResponseEntity.ok(rateCardService.listBySupplier(supplierId));
    }

    @PostMapping("/rate-card")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<SupplierRateCard> saveRateCard(@RequestBody SupplierRateCard payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rateCardService.save(payload));
    }

    // ── Alert snooze ───────────────────────────────────────────────────

    @PostMapping("/snooze")
    public ResponseEntity<AlertSnooze> snooze(@RequestBody Map<String, Object> body,
                                              @AuthenticationPrincipal CustomUserDetails principal) {
        Long userId = principal != null ? principal.getId() : null;
        String type = body.get("alertType").toString();
        String key = body.get("alertKey").toString();
        LocalDateTime until = LocalDateTime.parse(body.get("snoozedUntil").toString());
        return ResponseEntity.ok(snoozeService.snooze(userId, type, key, until));
    }

    @GetMapping("/snooze")
    public ResponseEntity<List<AlertSnooze>> listSnoozes(@RequestParam String alertType,
                                                         @AuthenticationPrincipal CustomUserDetails principal) {
        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(snoozeService.listActive(userId, alertType));
    }

    // ── Saved views ────────────────────────────────────────────────────

    @GetMapping("/saved-views")
    public ResponseEntity<List<SavedView>> listSavedViews(@RequestParam String surface,
                                                          @AuthenticationPrincipal CustomUserDetails principal) {
        Long userId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(savedViewService.list(userId, surface));
    }

    @PostMapping("/saved-views")
    public ResponseEntity<SavedView> saveView(@RequestBody SavedView payload,
                                              @AuthenticationPrincipal CustomUserDetails principal) {
        if (payload.getUserId() == null && principal != null) payload.setUserId(principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(savedViewService.save(payload));
    }

    @DeleteMapping("/saved-views/{id}")
    public ResponseEntity<Void> deleteView(@PathVariable Long id) {
        savedViewService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Barcode / QR labels ────────────────────────────────────────────

    @PostMapping(value = "/labels/print", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> printLabels(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) body.getOrDefault("variantIds", List.of());
        List<Long> ids = raw.stream().map(v -> Long.valueOf(v.toString())).toList();
        int copies = body.get("copies") != null ? Integer.parseInt(body.get("copies").toString()) : 1;
        byte[] pdf = labelService.renderLabels(ids, copies);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"labels.pdf\"");
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    // ── Low-stock email digest on-demand trigger ───────────────────────

    @PostMapping("/alerts/low-stock/test-email")
    public ResponseEntity<Map<String, Object>> triggerTestLowStockEmail() {
        try {
            Long shopId = com.desitech.vyaparsathi.common.configs.TenantContext.getCurrentShopId();
            if (shopId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No shop context found"));
            }
            com.desitech.vyaparsathi.shop.entity.Shop shop = shopRepository.findById(shopId)
                    .orElseThrow(() -> new IllegalArgumentException("Shop not found with id: " + shopId));
            boolean sent = lowStockScheduler.notifyShop(shop);
            return ResponseEntity.ok(Map.of(
                    "success", sent,
                    "message", sent ? "Low-stock alert email sent successfully to " + shop.getEmail() : "No low-stock items due for notification."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to send email: " + e.getMessage()
            ));
        }
    }
}
