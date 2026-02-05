
package com.desitech.vyaparsathi.inventory.controller;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.desitech.vyaparsathi.inventory.dto.SupplierDto;
import com.desitech.vyaparsathi.inventory.service.SupplierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF','ADMIN')")
public class SupplierController {

    private static final Logger logger = LoggerFactory.getLogger(SupplierController.class);

    @Autowired
    private SupplierService service;

    @PostMapping
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
}
