package com.desitech.vyaparsathi.customer.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.customer.entity.CustomerAttachment;

import java.util.List;

public interface CustomerAttachmentRepository extends BaseRepository<CustomerAttachment, Long> {
    List<CustomerAttachment> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    long countByCustomerId(Long customerId);
}
