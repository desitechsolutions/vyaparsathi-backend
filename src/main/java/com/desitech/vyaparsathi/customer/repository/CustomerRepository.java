package com.desitech.vyaparsathi.customer.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CustomerRepository extends BaseRepository<Customer, Long> {
    List<Customer> findByNameContainingIgnoreCase(String name);
}
