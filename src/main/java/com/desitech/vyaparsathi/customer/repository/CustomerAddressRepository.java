package com.desitech.vyaparsathi.customer.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.customer.entity.CustomerAddress;

import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository extends BaseRepository<CustomerAddress, Long> {
    List<CustomerAddress> findByCustomerIdOrderByIdAsc(Long customerId);
    Optional<CustomerAddress> findByCustomerIdAndIsDefaultBillingTrue(Long customerId);
    Optional<CustomerAddress> findByCustomerIdAndIsDefaultShippingTrue(Long customerId);
}
