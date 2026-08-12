package com.desitech.vyaparsathi.delivery.controller;

import com.desitech.vyaparsathi.delivery.dto.DeliveryPersonDTO;
import com.desitech.vyaparsathi.delivery.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/delivery-persons")
@Tag(name = "Delivery Persons", description = "CRUD for delivery agents (name, phone, vehicle, license, active flag).")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STAFF')")
public class DeliveryPersonController {
    private final DeliveryService service;

    public DeliveryPersonController(DeliveryService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Create a delivery person")
    public ResponseEntity<DeliveryPersonDTO> create(@RequestBody DeliveryPersonDTO dp) {
        return ResponseEntity.ok(service.createPerson(dp));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "Update a delivery person")
    public ResponseEntity<DeliveryPersonDTO> update(@PathVariable Long id, @RequestBody DeliveryPersonDTO dp) {
        return ResponseEntity.ok(service.updatePerson(id, dp));
    }

    @GetMapping
    @Operation(summary = "List delivery persons")
    public List<DeliveryPersonDTO> list() {
        return service.listPersons();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a delivery person")
    public ResponseEntity<DeliveryPersonDTO> get(@PathVariable Long id) {
        return service.getPerson(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Delete a delivery person (owner only)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deletePerson(id);
        return ResponseEntity.noContent().build();
    }
}
