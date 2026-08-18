package com.desitech.vyaparsathi.customer.service;

import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.customer.dto.CustomerCustomFieldValueDto;
import com.desitech.vyaparsathi.customer.entity.CustomerCustomFieldValue;
import com.desitech.vyaparsathi.customer.repository.CustomerCustomFieldValueRepository;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Per-customer custom-field values (EAV). Field definitions
 * (label, type, choices) live in the {@code custom_attributes}
 * table from Phase 4 — this service only stores + retrieves values
 * keyed by {@code fieldKey}. The FE is responsible for pairing keys
 * to labels via the existing custom-attributes API.
 */
@Service
public class CustomerCustomFieldValueService {

    private final CustomerCustomFieldValueRepository repo;
    private final CustomerRepository customerRepo;

    public CustomerCustomFieldValueService(CustomerCustomFieldValueRepository repo,
                                           CustomerRepository customerRepo) {
        this.repo = repo;
        this.customerRepo = customerRepo;
    }

    @Transactional(readOnly = true)
    public List<CustomerCustomFieldValueDto> listByCustomer(Long customerId) {
        ensureCustomerExists(customerId);
        return repo.findByCustomerId(customerId).stream().map(this::toDto).toList();
    }

    /**
     * Upsert-by-key. If a value already exists for (customerId, fieldKey)
     * it's updated; otherwise a new row is created. Empty / null value
     * deletes the row so the customer's custom-field payload stays lean.
     */
    @Transactional
    public CustomerCustomFieldValueDto upsert(Long customerId, CustomerCustomFieldValueDto dto) {
        ensureCustomerExists(customerId);
        String key = dto.getFieldKey() == null ? null : dto.getFieldKey().trim();
        if (key == null || key.isEmpty()) {
            throw new ApplicationException("fieldKey is required.");
        }
        String value = dto.getFieldValue();
        boolean valueEmpty = value == null || value.isBlank();

        var existing = repo.findByCustomerIdAndFieldKey(customerId, key);
        if (existing.isPresent()) {
            CustomerCustomFieldValue row = existing.get();
            if (valueEmpty) {
                repo.delete(row);
                return null;
            }
            row.setFieldValue(value);
            return toDto(repo.save(row));
        }
        if (valueEmpty) {
            // Nothing to write — empty value + no existing row.
            return null;
        }
        CustomerCustomFieldValue row = new CustomerCustomFieldValue();
        row.setCustomerId(customerId);
        row.setFieldKey(key);
        row.setFieldValue(value);
        return toDto(repo.save(row));
    }

    /**
     * Bulk-set custom-field values in one round-trip — used by the
     * customer detail Custom Fields tab's Save action. Missing keys
     * from the map are left alone; empty-string values delete their row.
     */
    @Transactional
    public List<CustomerCustomFieldValueDto> replaceAll(Long customerId, Map<String, String> values) {
        ensureCustomerExists(customerId);
        if (values != null) {
            for (Map.Entry<String, String> e : values.entrySet()) {
                CustomerCustomFieldValueDto dto = new CustomerCustomFieldValueDto();
                dto.setFieldKey(e.getKey());
                dto.setFieldValue(e.getValue());
                upsert(customerId, dto);
            }
        }
        return listByCustomer(customerId);
    }

    @Transactional
    public void deleteByKey(Long customerId, String fieldKey) {
        ensureCustomerExists(customerId);
        repo.findByCustomerIdAndFieldKey(customerId, fieldKey).ifPresent(repo::delete);
    }

    private void ensureCustomerExists(Long customerId) {
        if (customerId == null) throw new ApplicationException("customerId is required");
        if (!customerRepo.existsById(customerId)) {
            throw new EntityNotFoundException("Customer not found: " + customerId);
        }
    }

    private CustomerCustomFieldValueDto toDto(CustomerCustomFieldValue e) {
        CustomerCustomFieldValueDto d = new CustomerCustomFieldValueDto();
        d.setId(e.getId());
        d.setCustomerId(e.getCustomerId());
        d.setFieldKey(e.getFieldKey());
        d.setFieldValue(e.getFieldValue());
        return d;
    }

    /** Convenience for the customer detail loader — returns a plain map. */
    @Transactional(readOnly = true)
    public Map<String, String> asMap(Long customerId) {
        return listByCustomer(customerId).stream()
                .collect(Collectors.toMap(
                        CustomerCustomFieldValueDto::getFieldKey,
                        v -> v.getFieldValue() == null ? "" : v.getFieldValue(),
                        (a, b) -> b));
    }
}
