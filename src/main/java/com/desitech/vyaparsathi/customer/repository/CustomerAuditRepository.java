package com.desitech.vyaparsathi.customer.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.customer.entity.CustomerAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CustomerAuditRepository extends BaseRepository<CustomerAudit, Long> {

    /** All audit entries for a customer, most recent first. */
    List<CustomerAudit> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    /** Paginated audit entries. */
    Page<CustomerAudit> findByCustomerId(Long customerId, Pageable pageable);
}
