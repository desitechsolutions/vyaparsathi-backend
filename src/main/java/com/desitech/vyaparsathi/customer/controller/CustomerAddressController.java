package com.desitech.vyaparsathi.customer.controller;

import com.desitech.vyaparsathi.customer.dto.CustomerAddressDto;
import com.desitech.vyaparsathi.customer.service.CustomerAddressService;
import com.desitech.vyaparsathi.rbac.annotation.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{customerId}/addresses")
public class CustomerAddressController {

    private final CustomerAddressService service;

    public CustomerAddressController(CustomerAddressService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("CUSTOMER_VIEW")
    public List<CustomerAddressDto> list(@PathVariable Long customerId) {
        return service.listByCustomer(customerId);
    }

    @PostMapping
    @RequirePermission("CUSTOMER_EDIT")
    public CustomerAddressDto create(@PathVariable Long customerId, @Valid @RequestBody CustomerAddressDto dto) {
        return service.create(customerId, dto);
    }

    @PutMapping("/{addressId}")
    @RequirePermission("CUSTOMER_EDIT")
    public CustomerAddressDto update(@PathVariable Long customerId,
                                     @PathVariable Long addressId,
                                     @Valid @RequestBody CustomerAddressDto dto) {
        return service.update(addressId, dto);
    }

    @DeleteMapping("/{addressId}")
    @RequirePermission("CUSTOMER_EDIT")
    public void delete(@PathVariable Long customerId, @PathVariable Long addressId) {
        service.delete(addressId);
    }

    @PostMapping("/{addressId}/set-default-billing")
    @RequirePermission("CUSTOMER_EDIT")
    public CustomerAddressDto setDefaultBilling(@PathVariable Long customerId, @PathVariable Long addressId) {
        return service.setDefaultBilling(addressId);
    }

    @PostMapping("/{addressId}/set-default-shipping")
    @RequirePermission("CUSTOMER_EDIT")
    public CustomerAddressDto setDefaultShipping(@PathVariable Long customerId, @PathVariable Long addressId) {
        return service.setDefaultShipping(addressId);
    }
}
