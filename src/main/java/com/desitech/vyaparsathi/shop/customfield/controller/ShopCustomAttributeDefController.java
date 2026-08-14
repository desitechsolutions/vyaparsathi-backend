package com.desitech.vyaparsathi.shop.customfield.controller;

import com.desitech.vyaparsathi.shop.customfield.dto.ShopCustomAttributeDefDto;
import com.desitech.vyaparsathi.shop.customfield.service.ShopCustomAttributeDefService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * CRUD for per-shop custom attribute definitions.
 *
 * <p>Owner/Admin only for mutations — a shop's employee can read the
 * definitions (needed to render the form) but cannot change them.
 */
@RestController
@RequestMapping("/api/custom-attributes")
@RequiredArgsConstructor
public class ShopCustomAttributeDefController {

    private final ShopCustomAttributeDefService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STAFF')")
    public ResponseEntity<List<ShopCustomAttributeDefDto>> list() {
        return ResponseEntity.ok(service.listForCurrentShop());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<ShopCustomAttributeDefDto> create(
            @Valid @RequestBody ShopCustomAttributeDefDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<ShopCustomAttributeDefDto> update(
            @PathVariable Long id,
            @Valid @RequestBody ShopCustomAttributeDefDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reorder — accepts {@code {ids: [1, 5, 3, ...]}} in the order the
     * shop owner wants them rendered on the form.
     */
    @PostMapping("/reorder")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<List<ShopCustomAttributeDefDto>> reorder(
            @RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.getOrDefault("ids", List.of());
        return ResponseEntity.ok(service.reorder(ids));
    }
}
