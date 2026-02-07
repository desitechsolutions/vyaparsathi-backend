
package com.desitech.vyaparsathi.customer.controller;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.desitech.vyaparsathi.customer.dto.CustomerLedgerDto;
import com.desitech.vyaparsathi.customer.service.CustomerLedgerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/customers/{customerId}/ledger")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
public class CustomerLedgerController {

    private static final Logger logger = LoggerFactory.getLogger(CustomerLedgerController.class);
    @Autowired
    private CustomerLedgerService ledgerService;

    @PostMapping
    public ResponseEntity<CustomerLedgerDto> addLedgerEntry(
            @PathVariable Long customerId,
            @Valid @RequestBody CustomerLedgerDto dto) {
        try {
            CustomerLedgerDto result = ledgerService.addEntry(customerId, dto);
            logger.info("Added ledger entry for customerId={}", customerId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error adding ledger entry for customerId={}: {}", customerId, e.getMessage(), e);
            throw new ApplicationException("Failed to add ledger entry", e);
        }
    }

    @PutMapping("/{ledgerId}")
    public ResponseEntity<CustomerLedgerDto> updateLedgerEntry(
            @PathVariable Long ledgerId,
            @Valid @RequestBody CustomerLedgerDto dto) {
        try {
            CustomerLedgerDto result = ledgerService.updateEntry(ledgerId, dto);
            logger.info("Updated ledger entry id={}", ledgerId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error updating ledger entry id={}: {}", ledgerId, e.getMessage(), e);
            throw new ApplicationException("Failed to update ledger entry", e);
        }
    }

    @GetMapping
    public ResponseEntity<List<CustomerLedgerDto>> getLedger(
            @PathVariable Long customerId,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {
        try {
            List<CustomerLedgerDto> result = ledgerService.getLedger(customerId, startDate, endDate);
            logger.info("Fetched ledger for customerId={}", customerId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching ledger for customerId={}: {}", customerId, e.getMessage(), e);
            throw new ApplicationException("Failed to fetch ledger", e);
        }
    }

    @DeleteMapping("/{ledgerId}")
    public ResponseEntity<Void> deleteLedgerEntry(@PathVariable Long ledgerId) {
        try {
            ledgerService.deleteEntry(ledgerId);
            logger.info("Deleted ledger entry id={}", ledgerId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting ledger entry id={}: {}", ledgerId, e.getMessage(), e);
            throw new ApplicationException("Failed to delete ledger entry", e);
        }
    }
}