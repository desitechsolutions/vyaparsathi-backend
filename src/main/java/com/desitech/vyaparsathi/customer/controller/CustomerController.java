package com.desitech.vyaparsathi.customer.controller;

import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.customer.dto.CustomerDto;
import com.desitech.vyaparsathi.customer.service.CustomerService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
public class CustomerController {

    private static final Logger logger = LoggerFactory.getLogger(CustomerController.class);
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerDto> addCustomer(@RequestBody CustomerDto dto) {
        try {
            CustomerDto result = customerService.addCustomer(dto);
            logger.info("Added customer with name={}", dto.getName());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error adding customer with name={}: {}", dto.getName(), e.getMessage(), e);
            throw new ApplicationException("Failed to add customer", e);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerDto> updateCustomer(@PathVariable Long id, @RequestBody CustomerDto dto) {
        try {
            CustomerDto result = customerService.updateCustomer(id, dto);
            logger.info("Updated customer id={}", id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error updating customer id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to update customer", e);
        }
    }

    @GetMapping
    public ResponseEntity<List<CustomerDto>> listCustomers(@RequestParam(required = false) String name) {
        try {
            List<CustomerDto> result;
            if (name != null && !name.isEmpty()) {
                result = customerService.searchCustomers(name);
                logger.info("Searched customers by name={}", name);
            } else {
                result = customerService.listCustomers();
                logger.info("Listed all customers");
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error listing/searching customers: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to list/search customers", e);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerDto> getCustomer(@PathVariable Long id) {
        try {
            CustomerDto customer = customerService.getCustomer(id);
            logger.info("Fetched customer id={}", id);
            return ResponseEntity.ok(customer);
        } catch (EntityNotFoundException e) {
            logger.warn("Customer not found id={}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error fetching customer id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to fetch customer", e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        try {
            customerService.deleteCustomer(id);
            logger.info("Deleted customer id={}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting customer id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to delete customer", e);
        }
    }
}