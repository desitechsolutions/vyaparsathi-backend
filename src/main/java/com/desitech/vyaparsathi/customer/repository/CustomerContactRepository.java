package com.desitech.vyaparsathi.customer.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.customer.entity.CustomerContact;

import java.util.List;
import java.util.Optional;

public interface CustomerContactRepository extends BaseRepository<CustomerContact, Long> {
    List<CustomerContact> findByCustomerIdOrderByIsPrimaryDescIdAsc(Long customerId);
    Optional<CustomerContact> findByCustomerIdAndIsPrimaryTrue(Long customerId);
}
