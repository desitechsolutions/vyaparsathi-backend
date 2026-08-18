package com.desitech.vyaparsathi.customer.controller;

import com.desitech.vyaparsathi.customer.dto.CustomerNoteDto;
import com.desitech.vyaparsathi.customer.service.CustomerNoteService;
import com.desitech.vyaparsathi.rbac.annotation.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers/{customerId}/notes")
public class CustomerNoteController {

    private final CustomerNoteService service;

    public CustomerNoteController(CustomerNoteService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("CUSTOMER_VIEW")
    public List<CustomerNoteDto> list(@PathVariable Long customerId) {
        return service.listByCustomer(customerId);
    }

    @PostMapping
    @RequirePermission("CUSTOMER_EDIT")
    public CustomerNoteDto create(@PathVariable Long customerId, @Valid @RequestBody CustomerNoteDto dto) {
        return service.create(customerId, dto);
    }

    @PutMapping("/{noteId}")
    @RequirePermission("CUSTOMER_EDIT")
    public CustomerNoteDto update(@PathVariable Long customerId,
                                  @PathVariable Long noteId,
                                  @Valid @RequestBody CustomerNoteDto dto) {
        return service.update(noteId, dto);
    }

    @DeleteMapping("/{noteId}")
    @RequirePermission("CUSTOMER_EDIT")
    public void delete(@PathVariable Long customerId, @PathVariable Long noteId) {
        service.delete(noteId);
    }

    @PostMapping("/{noteId}/pin")
    @RequirePermission("CUSTOMER_EDIT")
    public CustomerNoteDto setPinned(@PathVariable Long customerId,
                                     @PathVariable Long noteId,
                                     @RequestBody(required = false) Map<String, Boolean> body) {
        boolean pinned = body == null || body.get("pinned") == null || Boolean.TRUE.equals(body.get("pinned"));
        return service.setPinned(noteId, pinned);
    }
}
