
package com.desitech.vyaparsathi.supplier.controller;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.desitech.vyaparsathi.supplier.dto.SupplierDto;
import com.desitech.vyaparsathi.supplier.service.SupplierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
// Class-level gate replaced by per-method @RequirePermission below.
public class SupplierController {

    private static final Logger logger = LoggerFactory.getLogger(SupplierController.class);

    @Autowired
    private SupplierService service;

    @PostMapping
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SUPPLIER_CREATE")
    public ResponseEntity<SupplierDto> createSupplier(@RequestBody SupplierDto dto) {
        try {
            SupplierDto result = service.createSupplier(dto);
            logger.info("Created supplier with name={}", dto.getName());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error creating supplier with name={}: {}", dto.getName(), e.getMessage(), e);
            throw new ApplicationException("Failed to create supplier", e);
        }
    }

    @GetMapping
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SUPPLIER_VIEW")
    public ResponseEntity<List<SupplierDto>> getAllSuppliers() {
        try {
            List<SupplierDto> result = service.findAllSuppliers();
            logger.info("Fetched all suppliers");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching all suppliers: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to fetch suppliers", e);
        }
    }

    @GetMapping("/{id}")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SUPPLIER_VIEW")
    public ResponseEntity<SupplierDto> getSupplierById(@PathVariable Long id) {
        try {
            SupplierDto result = service.findSupplierById(id);
            logger.info("Fetched supplier with id={}", id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching supplier with id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to fetch supplier", e);
        }
    }

    @PutMapping("/{id}")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SUPPLIER_EDIT")
    public ResponseEntity<SupplierDto> updateSupplier(@PathVariable Long id, @RequestBody SupplierDto dto) {
        try {
            SupplierDto result = service.updateSupplier(id, dto);
            logger.info("Updated supplier with id={}", id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error updating supplier with id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to update supplier", e);
        }
    }

    @DeleteMapping("/{id}")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SUPPLIER_DELETE")
    public ResponseEntity<Void> deleteSupplier(@PathVariable Long id) {
        try {
            service.deleteSupplier(id);
            logger.info("Deleted supplier with id={}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting supplier with id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to delete supplier", e);
        }
    }

    // ── V103 enterprise endpoints — stats + active toggle ─────────────

    @org.springframework.beans.factory.annotation.Autowired
    private com.desitech.vyaparsathi.supplier.service.SupplierStatsService statsService;

    /** Aggregate stats for the supplier detail KPI strip. */
    @GetMapping("/{id}/stats")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SUPPLIER_VIEW")
    public ResponseEntity<com.desitech.vyaparsathi.supplier.dto.SupplierStatsDto> getStats(@PathVariable Long id) {
        return ResponseEntity.ok(statsService.compute(id));
    }

    /**
     * Soft-toggle the supplier's active flag. Suppliers with historical
     * transactions should never be hard-deleted — use this instead.
     */
    @PostMapping("/{id}/toggle-active")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("SUPPLIER_EDIT")
    public ResponseEntity<com.desitech.vyaparsathi.supplier.dto.SupplierDto> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(service.toggleActive(id));
    }
}
