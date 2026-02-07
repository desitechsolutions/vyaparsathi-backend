package com.desitech.vyaparsathi.delivery.controller;

import com.desitech.vyaparsathi.delivery.dto.DeliveryDTO;
import com.desitech.vyaparsathi.delivery.dto.DeliveryStatusHistoryDTO;
import com.desitech.vyaparsathi.delivery.enums.DeliveryStatus;
import com.desitech.vyaparsathi.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

    private final DeliveryService service;

    public DeliveryController(DeliveryService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DeliveryDTO> create(@Valid @RequestBody DeliveryDTO delivery) {
        DeliveryDTO created = service.createDelivery(delivery);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeliveryDTO> get(@PathVariable Long id) {
        return service.getDelivery(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<DeliveryDTO> list(@RequestParam(required = false) Long saleId) {
        if (saleId != null) {
            return service.getDeliveriesBySaleId(saleId);
        }
        return service.getAllDeliveries();
    }

    /**
     * PATCH: Update only delivery details (address, charge, notes, paidBy)
     */
    @PatchMapping("/{id}/details")
    public ResponseEntity<DeliveryDTO> updateDetails(@PathVariable Long id, @RequestBody DeliveryDTO delivery) {
        try {
            return ResponseEntity.ok(service.updateDeliveryDetails(id, delivery));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * PATCH: Assign or change delivery person
     */
    @PatchMapping("/{id}/person")
    public ResponseEntity<DeliveryDTO> assignPerson(
            @PathVariable Long id,
            @RequestBody DeliveryDTO delivery // expects just deliveryPerson filled
    ) {
        try {
            return ResponseEntity.ok(service.assignDeliveryPerson(id, delivery.getDeliveryPerson()));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * PATCH: Update delivery status
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<DeliveryDTO> updateStatus(
            @PathVariable Long id,
            @RequestParam DeliveryStatus status,
            @RequestParam String changedBy
    ) {
        try {
            return ResponseEntity.ok(service.updateStatus(id, status, changedBy));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * View full status history
     */
    @GetMapping("/{id}/history")
    public List<DeliveryStatusHistoryDTO> history(@PathVariable Long id) {
        return service.getStatusHistory(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteDelivery(id);
        return ResponseEntity.noContent().build();
    }
}