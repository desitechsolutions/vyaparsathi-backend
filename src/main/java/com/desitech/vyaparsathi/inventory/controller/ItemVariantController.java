
package com.desitech.vyaparsathi.inventory.controller;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.desitech.vyaparsathi.inventory.dto.BulkVariantPatchDto;
import com.desitech.vyaparsathi.inventory.dto.ItemVariantDto;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.service.ItemVariantService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/item-variants")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF','ADMIN')")
public class ItemVariantController {

    private static final Logger logger = LoggerFactory.getLogger(ItemVariantController.class);

    @Autowired
    private ItemVariantService service;

    @PostMapping
    public ResponseEntity<ItemVariantDto> create(@jakarta.validation.Valid @RequestBody ItemVariantDto dto) {
        try {
            ItemVariantDto result = service.create(dto);
            logger.info("Created item variant with name={}", dto.getItemName());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error creating item variant with name={}: {}", dto.getItemName(), e.getMessage(), e);
            throw new ApplicationException("Failed to create item variant", e);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ItemVariantDto> update(@PathVariable Long id, @jakarta.validation.Valid @RequestBody ItemVariantDto dto) {
        try {
            ItemVariantDto result = service.update(id, dto);
            logger.info("Updated item variant id={}", id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error updating item variant id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to update item variant", e);
        }
    }

    @GetMapping
    public ResponseEntity<Page<ItemVariantDto>> list(Pageable pageable) {
        try {
            Page<ItemVariantDto> result = service.list(pageable);
            logger.info("Listed item variants");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error listing item variants: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to list item variants", e);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ItemVariantDto> get(@PathVariable Long id) {
        try {
            ItemVariantDto result = service.get(id);
            logger.info("Fetched item variant id={}", id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching item variant id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to fetch item variant", e);
        }
    }

    @GetMapping("/filter")
    public ResponseEntity<List<ItemVariantDto>> searchItemVariants(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String style,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String attribute1,
            @RequestParam(required = false) String attribute2,
            // Legacy aliases — CLOTHING clients still send ?fabric / ?season.
            // Kept optional so old dashboards do not break; new callers should
            // send the canonical attribute1 / attribute2 query params.
            @RequestParam(required = false) String fabric,
            @RequestParam(required = false) String season,
            @RequestParam(required = false) String fit,
            @RequestParam(required = false) String specifications,
            @RequestParam(required = false) String composition
    ) {
        try {
            String specs = specifications != null ? specifications : composition;
            String attr1 = attribute1 != null ? attribute1 : fabric;
            String attr2 = attribute2 != null ? attribute2 : season;
            List<ItemVariantDto> dtos = service.searchItemVariants(name, category, color, size, style, sku, attr1, attr2, fit, specs);
            logger.info("Searched item variants with filters: name={}, category={}, color={}, size={}, style={}, sku={}, attr1={}, attr2={}, specifications={}",
                    name, category, color, size, style, sku, attr1, attr2, specs);
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            logger.error("Error searching item variants: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to search item variants", e);
        }
    }

    /**
     * Bulk-patch endpoint (V79). Applies a partial update (any subset of
     * threshold / reorder-point / reorder-qty / safety-stock / max-stock /
     * lead-time / preferred-supplier) to every variant listed in the body.
     * Owner / Admin only — same guard as single-variant updates.
     *
     * <p>Used by the LowStockAlerts "Bulk edit" action so a shop owner can
     * push a new threshold or supplier across a whole selection in one call.
     */
    @PostMapping("/bulk-patch")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<Map<String, Integer>> bulkPatch(
            @jakarta.validation.Valid @RequestBody BulkVariantPatchDto patch) {
        try {
            int updated = service.bulkPatch(patch);
            logger.info("Bulk-patched {} item variants", updated);
            return ResponseEntity.ok(Map.of("updated", updated));
        } catch (Exception e) {
            logger.error("Bulk patch failed: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to bulk-patch variants", e);
        }
    }

    /**
     * POS Barcode / QR scanner lookup.
     * Used by POS terminals to resolve a scanned barcode to the full item details + live stock.
     *
     * GET /api/item-variants/barcode/{code}
     */
    @GetMapping("/barcode/{code}")
    public ResponseEntity<ItemVariantDto> lookupByBarcode(@PathVariable String code) {
        try {
            ItemVariantDto result = service.lookupByBarcode(code);
            logger.info("Barcode lookup success for code={}", code);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Barcode lookup failed for code={}: {}", code, e.getMessage(), e);
            throw new ApplicationException("Item not found for barcode: " + code, e);
        }
    }
}