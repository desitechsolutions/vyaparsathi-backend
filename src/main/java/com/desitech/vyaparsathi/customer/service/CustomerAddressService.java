package com.desitech.vyaparsathi.customer.service;

import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.customer.dto.CustomerAddressDto;
import com.desitech.vyaparsathi.customer.entity.CustomerAddress;
import com.desitech.vyaparsathi.customer.mapper.CustomerAddressMapper;
import com.desitech.vyaparsathi.customer.repository.CustomerAddressRepository;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD + default-billing / default-shipping invariants for
 * {@link CustomerAddress}. Rules:
 * <ul>
 *   <li>At most one address per customer is default billing.</li>
 *   <li>At most one address per customer is default shipping.</li>
 *   <li>One row can hold both flags (typical for retail customers).</li>
 * </ul>
 * Enforced at the service layer since MySQL 8 has no partial-unique.
 */
@Service
public class CustomerAddressService {

    private final CustomerAddressRepository repo;
    private final CustomerRepository customerRepo;
    private final CustomerAddressMapper mapper;
    private final CustomerAuditService auditService;

    public CustomerAddressService(CustomerAddressRepository repo,
                                  CustomerRepository customerRepo,
                                  CustomerAddressMapper mapper,
                                  CustomerAuditService auditService) {
        this.repo = repo;
        this.customerRepo = customerRepo;
        this.mapper = mapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CustomerAddressDto> listByCustomer(Long customerId) {
        ensureCustomerExists(customerId);
        return mapper.toDtoList(repo.findByCustomerIdOrderByIdAsc(customerId));
    }

    @Transactional
    public CustomerAddressDto create(Long customerId, CustomerAddressDto dto) {
        ensureCustomerExists(customerId);
        CustomerAddress entity = mapper.toEntity(dto);
        entity.setId(null);
        entity.setCustomerId(customerId);
        applyDefaultInvariants(customerId, entity, /*existingId*/ null);
        // Auto-set defaults on first address so a customer never has
        // "some addresses but none is default" state.
        boolean isFirst = repo.findByCustomerIdOrderByIdAsc(customerId).isEmpty();
        if (isFirst) {
            if (!Boolean.TRUE.equals(entity.getIsDefaultBilling())) entity.setIsDefaultBilling(true);
            if (!Boolean.TRUE.equals(entity.getIsDefaultShipping())) entity.setIsDefaultShipping(true);
        }
        CustomerAddress saved = repo.save(entity);
        auditService.recordAction(customerId, "ADDRESS_ADDED",
                "Added address: " + shortLabel(saved), null);
        return mapper.toDto(saved);
    }

    @Transactional
    public CustomerAddressDto update(Long addressId, CustomerAddressDto dto) {
        CustomerAddress existing = repo.findById(addressId)
                .orElseThrow(() -> new EntityNotFoundException("Address not found: " + addressId));
        mapper.updateEntityFromDto(dto, existing);
        applyDefaultInvariants(existing.getCustomerId(), existing, existing.getId());
        CustomerAddress saved = repo.save(existing);
        auditService.recordAction(existing.getCustomerId(), "ADDRESS_UPDATED",
                "Updated address: " + shortLabel(saved), null);
        return mapper.toDto(saved);
    }

    @Transactional
    public void delete(Long addressId) {
        CustomerAddress existing = repo.findById(addressId)
                .orElseThrow(() -> new EntityNotFoundException("Address not found: " + addressId));
        Long customerId = existing.getCustomerId();
        boolean wasDefaultBilling = Boolean.TRUE.equals(existing.getIsDefaultBilling());
        boolean wasDefaultShipping = Boolean.TRUE.equals(existing.getIsDefaultShipping());
        repo.delete(existing);
        // If we removed a default, promote the oldest surviving address
        // so the customer keeps sensible defaults for invoice creation.
        List<CustomerAddress> remaining = repo.findByCustomerIdOrderByIdAsc(customerId);
        if (!remaining.isEmpty()) {
            CustomerAddress promote = remaining.get(0);
            boolean touched = false;
            if (wasDefaultBilling && !Boolean.TRUE.equals(promote.getIsDefaultBilling())) {
                promote.setIsDefaultBilling(true);
                touched = true;
            }
            if (wasDefaultShipping && !Boolean.TRUE.equals(promote.getIsDefaultShipping())) {
                promote.setIsDefaultShipping(true);
                touched = true;
            }
            if (touched) repo.save(promote);
        }
        auditService.recordAction(customerId, "ADDRESS_REMOVED",
                "Removed address: " + shortLabel(existing), null);
    }

    @Transactional
    public CustomerAddressDto setDefaultBilling(Long addressId) {
        CustomerAddress target = repo.findById(addressId)
                .orElseThrow(() -> new EntityNotFoundException("Address not found: " + addressId));
        if (!Boolean.TRUE.equals(target.getIsDefaultBilling())) {
            repo.findByCustomerIdAndIsDefaultBillingTrue(target.getCustomerId()).ifPresent(existing -> {
                existing.setIsDefaultBilling(false);
                repo.save(existing);
            });
            target.setIsDefaultBilling(true);
            repo.save(target);
            auditService.recordAction(target.getCustomerId(), "DEFAULT_BILLING_CHANGED",
                    "Default billing address set: " + shortLabel(target), null);
        }
        return mapper.toDto(target);
    }

    @Transactional
    public CustomerAddressDto setDefaultShipping(Long addressId) {
        CustomerAddress target = repo.findById(addressId)
                .orElseThrow(() -> new EntityNotFoundException("Address not found: " + addressId));
        if (!Boolean.TRUE.equals(target.getIsDefaultShipping())) {
            repo.findByCustomerIdAndIsDefaultShippingTrue(target.getCustomerId()).ifPresent(existing -> {
                existing.setIsDefaultShipping(false);
                repo.save(existing);
            });
            target.setIsDefaultShipping(true);
            repo.save(target);
            auditService.recordAction(target.getCustomerId(), "DEFAULT_SHIPPING_CHANGED",
                    "Default shipping address set: " + shortLabel(target), null);
        }
        return mapper.toDto(target);
    }

    /**
     * When a create/update flips a default flag ON, demote whoever
     * currently holds that flag (except this row itself on update).
     */
    private void applyDefaultInvariants(Long customerId, CustomerAddress row, Long excludeId) {
        if (Boolean.TRUE.equals(row.getIsDefaultBilling())) {
            repo.findByCustomerIdAndIsDefaultBillingTrue(customerId).ifPresent(existing -> {
                if (excludeId == null || !existing.getId().equals(excludeId)) {
                    existing.setIsDefaultBilling(false);
                    repo.save(existing);
                }
            });
        }
        if (Boolean.TRUE.equals(row.getIsDefaultShipping())) {
            repo.findByCustomerIdAndIsDefaultShippingTrue(customerId).ifPresent(existing -> {
                if (excludeId == null || !existing.getId().equals(excludeId)) {
                    existing.setIsDefaultShipping(false);
                    repo.save(existing);
                }
            });
        }
    }

    private void ensureCustomerExists(Long customerId) {
        if (customerId == null) {
            throw new ApplicationException("customerId is required");
        }
        if (!customerRepo.existsById(customerId)) {
            throw new EntityNotFoundException("Customer not found: " + customerId);
        }
    }

    /** Short label for audit summaries: "Head Office · Bengaluru" or first-line fallback. */
    private String shortLabel(CustomerAddress a) {
        if (a.getLabel() != null && !a.getLabel().isBlank()) {
            return a.getLabel() + (a.getCity() != null ? " · " + a.getCity() : "");
        }
        String line = a.getAddressLine1() != null ? a.getAddressLine1() : "";
        if (a.getCity() != null) line = line.isEmpty() ? a.getCity() : line + ", " + a.getCity();
        return line.length() > 100 ? line.substring(0, 100) : line;
    }
}
