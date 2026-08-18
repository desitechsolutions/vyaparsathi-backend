package com.desitech.vyaparsathi.customer.service;

import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.customer.dto.CustomerContactDto;
import com.desitech.vyaparsathi.customer.entity.CustomerContact;
import com.desitech.vyaparsathi.customer.mapper.CustomerContactMapper;
import com.desitech.vyaparsathi.customer.repository.CustomerContactRepository;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD + primary-contact invariant for {@link CustomerContact}.
 *
 * <p>Invariant: a customer may have zero or many contacts, but at most
 * one is flagged {@code isPrimary=true}. The service demotes the
 * existing primary whenever a new one is elected — the DB has no
 * partial-unique support in MySQL 8 so this can't be an index.</p>
 */
@Service
public class CustomerContactService {

    private final CustomerContactRepository repo;
    private final CustomerRepository customerRepo;
    private final CustomerContactMapper mapper;
    private final CustomerAuditService auditService;

    public CustomerContactService(CustomerContactRepository repo,
                                  CustomerRepository customerRepo,
                                  CustomerContactMapper mapper,
                                  CustomerAuditService auditService) {
        this.repo = repo;
        this.customerRepo = customerRepo;
        this.mapper = mapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CustomerContactDto> listByCustomer(Long customerId) {
        ensureCustomerExists(customerId);
        return mapper.toDtoList(repo.findByCustomerIdOrderByIsPrimaryDescIdAsc(customerId));
    }

    @Transactional
    public CustomerContactDto create(Long customerId, CustomerContactDto dto) {
        ensureCustomerExists(customerId);
        CustomerContact entity = mapper.toEntity(dto);
        entity.setId(null);
        entity.setCustomerId(customerId);
        // Enforce primary invariant BEFORE save — if this new contact
        // claims primary, demote the current one first.
        if (Boolean.TRUE.equals(entity.getIsPrimary())) {
            demoteExistingPrimary(customerId);
        } else {
            // Auto-promote the first contact so a customer never has
            // "some contacts but no primary" state.
            if (repo.findByCustomerIdOrderByIsPrimaryDescIdAsc(customerId).isEmpty()) {
                entity.setIsPrimary(true);
            }
        }
        CustomerContact saved = repo.save(entity);
        auditService.recordAction(customerId, "CONTACT_ADDED", "Added contact: " + saved.getName(), null);
        return mapper.toDto(saved);
    }

    @Transactional
    public CustomerContactDto update(Long contactId, CustomerContactDto dto) {
        CustomerContact existing = repo.findById(contactId)
                .orElseThrow(() -> new EntityNotFoundException("Contact not found: " + contactId));
        Boolean wasPrimary = existing.getIsPrimary();
        mapper.updateEntityFromDto(dto, existing);
        // Handle primary flip in one place, not two — mapper's default
        // NullValuePropertyMappingStrategy.IGNORE means null flags don't
        // clobber the existing value, but a real TRUE/FALSE will.
        if (Boolean.TRUE.equals(dto.getIsPrimary()) && !Boolean.TRUE.equals(wasPrimary)) {
            demoteExistingPrimary(existing.getCustomerId());
            existing.setIsPrimary(true);
        } else if (Boolean.FALSE.equals(dto.getIsPrimary()) && Boolean.TRUE.equals(wasPrimary)) {
            // Refuse to demote the primary if it would leave the customer
            // with no primary at all — force the caller to promote another
            // one first via setPrimary().
            long total = repo.findByCustomerIdOrderByIsPrimaryDescIdAsc(existing.getCustomerId()).size();
            if (total > 1) {
                existing.setIsPrimary(false);
            } else {
                throw new ApplicationException("Cannot un-set the primary contact when it's the only one. Promote another contact first.");
            }
        }
        CustomerContact saved = repo.save(existing);
        auditService.recordAction(existing.getCustomerId(), "CONTACT_UPDATED", "Updated contact: " + saved.getName(), null);
        return mapper.toDto(saved);
    }

    @Transactional
    public void delete(Long contactId) {
        CustomerContact existing = repo.findById(contactId)
                .orElseThrow(() -> new EntityNotFoundException("Contact not found: " + contactId));
        Long customerId = existing.getCustomerId();
        boolean wasPrimary = Boolean.TRUE.equals(existing.getIsPrimary());
        repo.delete(existing);
        // If we just deleted the primary and other contacts remain,
        // auto-promote the oldest surviving one so the customer keeps
        // exactly one primary.
        if (wasPrimary) {
            List<CustomerContact> remaining = repo.findByCustomerIdOrderByIsPrimaryDescIdAsc(customerId);
            if (!remaining.isEmpty()) {
                CustomerContact promote = remaining.get(0);
                promote.setIsPrimary(true);
                repo.save(promote);
            }
        }
        auditService.recordAction(customerId, "CONTACT_REMOVED", "Removed contact: " + existing.getName(), null);
    }

    @Transactional
    public CustomerContactDto setPrimary(Long contactId) {
        CustomerContact target = repo.findById(contactId)
                .orElseThrow(() -> new EntityNotFoundException("Contact not found: " + contactId));
        if (Boolean.TRUE.equals(target.getIsPrimary())) {
            return mapper.toDto(target);
        }
        demoteExistingPrimary(target.getCustomerId());
        target.setIsPrimary(true);
        repo.save(target);
        auditService.recordAction(target.getCustomerId(), "CONTACT_PRIMARY_CHANGED",
                "Primary contact set to: " + target.getName(), null);
        return mapper.toDto(target);
    }

    private void demoteExistingPrimary(Long customerId) {
        repo.findByCustomerIdAndIsPrimaryTrue(customerId).ifPresent(existing -> {
            existing.setIsPrimary(false);
            repo.save(existing);
        });
    }

    private void ensureCustomerExists(Long customerId) {
        if (customerId == null) {
            throw new ApplicationException("customerId is required");
        }
        if (!customerRepo.existsById(customerId)) {
            throw new EntityNotFoundException("Customer not found: " + customerId);
        }
    }

}
