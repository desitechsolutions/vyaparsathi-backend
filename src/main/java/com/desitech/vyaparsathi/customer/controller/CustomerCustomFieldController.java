package com.desitech.vyaparsathi.customer.controller;

import com.desitech.vyaparsathi.customer.dto.CustomerCustomFieldValueDto;
import com.desitech.vyaparsathi.customer.service.CustomerCustomFieldValueService;
import com.desitech.vyaparsathi.rbac.annotation.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers/{customerId}/custom-fields")
public class CustomerCustomFieldController {

    private final CustomerCustomFieldValueService service;

    public CustomerCustomFieldController(CustomerCustomFieldValueService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("CUSTOMER_VIEW")
    public List<CustomerCustomFieldValueDto> list(@PathVariable Long customerId) {
        return service.listByCustomer(customerId);
    }

    @PutMapping("/{fieldKey}")
    @RequirePermission("CUSTOMER_EDIT")
    public CustomerCustomFieldValueDto upsert(
            @PathVariable Long customerId,
            @PathVariable String fieldKey,
            @Valid @RequestBody CustomerCustomFieldValueDto dto) {
        dto.setFieldKey(fieldKey);
        return service.upsert(customerId, dto);
    }

    @DeleteMapping("/{fieldKey}")
    @RequirePermission("CUSTOMER_EDIT")
    public void delete(@PathVariable Long customerId, @PathVariable String fieldKey) {
        service.deleteByKey(customerId, fieldKey);
    }

    /**
     * Bulk-save all values in a single request body — used by the
     * customer detail "Save custom fields" button so the FE doesn't
     * have to fan out one PUT per field.
     */
    @PutMapping
    @RequirePermission("CUSTOMER_EDIT")
    public List<CustomerCustomFieldValueDto> replaceAll(
            @PathVariable Long customerId,
            @RequestBody Map<String, String> values) {
        return service.replaceAll(customerId, values);
    }
}
