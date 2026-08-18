package com.desitech.vyaparsathi.customer.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.customer.entity.CustomerCustomFieldValue;

import java.util.List;
import java.util.Optional;

public interface CustomerCustomFieldValueRepository extends BaseRepository<CustomerCustomFieldValue, Long> {
    List<CustomerCustomFieldValue> findByCustomerId(Long customerId);
    Optional<CustomerCustomFieldValue> findByCustomerIdAndFieldKey(Long customerId, String fieldKey);
}
