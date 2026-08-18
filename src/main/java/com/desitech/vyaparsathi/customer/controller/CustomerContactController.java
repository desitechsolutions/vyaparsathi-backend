package com.desitech.vyaparsathi.customer.controller;

import com.desitech.vyaparsathi.customer.dto.CustomerContactDto;
import com.desitech.vyaparsathi.customer.service.CustomerContactService;
import com.desitech.vyaparsathi.rbac.annotation.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{customerId}/contacts")
public class CustomerContactController {

    private final CustomerContactService service;

    public CustomerContactController(CustomerContactService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("CUSTOMER_VIEW")
    public List<CustomerContactDto> list(@PathVariable Long customerId) {
        return service.listByCustomer(customerId);
    }

    @PostMapping
    @RequirePermission("CUSTOMER_EDIT")
    public CustomerContactDto create(@PathVariable Long customerId, @Valid @RequestBody CustomerContactDto dto) {
        return service.create(customerId, dto);
    }

    @PutMapping("/{contactId}")
    @RequirePermission("CUSTOMER_EDIT")
    public CustomerContactDto update(@PathVariable Long customerId,
                                     @PathVariable Long contactId,
                                     @Valid @RequestBody CustomerContactDto dto) {
        return service.update(contactId, dto);
    }

    @DeleteMapping("/{contactId}")
    @RequirePermission("CUSTOMER_EDIT")
    public void delete(@PathVariable Long customerId, @PathVariable Long contactId) {
        service.delete(contactId);
    }

    @PostMapping("/{contactId}/set-primary")
    @RequirePermission("CUSTOMER_EDIT")
    public CustomerContactDto setPrimary(@PathVariable Long customerId, @PathVariable Long contactId) {
        return service.setPrimary(contactId);
    }
}
