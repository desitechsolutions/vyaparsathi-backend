package com.desitech.vyaparsathi.purchaseorder.controller;

import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderDto;
import com.desitech.vyaparsathi.purchaseorder.service.PurchaseOrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {
    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderController.class);
    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PostMapping
    public ResponseEntity<PurchaseOrderDto> create(@Valid @RequestBody PurchaseOrderDto dto) {
        return ResponseEntity.ok(purchaseOrderService.createPurchaseOrder(dto));
    }

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @GetMapping
    public ResponseEntity<List<PurchaseOrderDto>> getAll() {
        return ResponseEntity.ok(purchaseOrderService.findAllPurchaseOrders());
    }

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrderDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.findPurchaseOrderById(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PutMapping("/{id}")
    public ResponseEntity<PurchaseOrderDto> update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderDto dto) {
        return ResponseEntity.ok(purchaseOrderService.updatePurchaseOrder(id, dto));
    }

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @PostMapping("/{id}/submit")
    public ResponseEntity<PurchaseOrderDto> submit(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.submitPurchaseOrder(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        purchaseOrderService.deletePurchaseOrder(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/pending")
    public List<PurchaseOrderDto> getPendingPurchaseOrders() {
        return purchaseOrderService.getPendingPurchaseOrders();
    }
}