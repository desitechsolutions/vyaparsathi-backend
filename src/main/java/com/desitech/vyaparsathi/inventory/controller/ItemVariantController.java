
package com.desitech.vyaparsathi.inventory.controller;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.desitech.vyaparsathi.inventory.dto.ItemVariantDto;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.service.ItemVariantService;
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
    public ResponseEntity<ItemVariantDto> create(@RequestBody ItemVariantDto dto) {
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
    public ResponseEntity<ItemVariantDto> update(@PathVariable Long id, @RequestBody ItemVariantDto dto) {
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
            @RequestParam(required = false) String fabric,
            @RequestParam(required = false) String season,
            @RequestParam(required = false) String fit,
            @RequestParam(required = false) String specifications,
            @RequestParam(required = false) String composition
    ) {
        try {
            String specs = specifications != null ? specifications : composition;
            List<ItemVariantDto> dtos = service.searchItemVariants(name, category, color, size, style, sku, fabric, season, fit, specs);
            logger.info("Searched item variants with filters: name={}, category={}, color={}, size={}, style={}, sku={}, specifications={}", name, category, color, size, style, sku, specs);
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            logger.error("Error searching item variants: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to search item variants", e);
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