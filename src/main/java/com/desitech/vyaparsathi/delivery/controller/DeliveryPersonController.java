package com.desitech.vyaparsathi.delivery.controller;

import com.desitech.vyaparsathi.delivery.dto.DeliveryPersonDTO;
import com.desitech.vyaparsathi.delivery.entity.DeliveryPerson;
import com.desitech.vyaparsathi.delivery.service.DeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/delivery-persons")
public class DeliveryPersonController {
    private final DeliveryService service;

    public DeliveryPersonController(DeliveryService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DeliveryPersonDTO> create(@RequestBody DeliveryPersonDTO dp) {
        return ResponseEntity.ok(service.createPerson(dp));
    }

    @GetMapping
    public List<DeliveryPersonDTO> list() {
        return service.listPersons();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeliveryPersonDTO> get(@PathVariable Long id) {
        return service.getPerson(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deletePerson(id);
        return ResponseEntity.noContent().build();
    }
}