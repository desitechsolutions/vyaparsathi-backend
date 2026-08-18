package com.desitech.vyaparsathi.customer.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.customer.entity.CustomerAudit;
import com.desitech.vyaparsathi.customer.repository.CustomerAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerAuditService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerAuditService.class);

    private final CustomerAuditRepository auditRepo;

    public CustomerAuditService(CustomerAuditRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    @Transactional
    public void recordAction(Long customerId, String action, String summary, String changesJson) {
        try {
            Long shopId = TenantContext.getCurrentShopId();
            if (shopId == null) {
                logger.warn("Cannot record customer audit: TenantContext has no shopId");
                return;
            }

            String user = "system";
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
                user = auth.getName();
            }

            CustomerAudit audit = new CustomerAudit();
            audit.setCustomerId(customerId);
            audit.setAction(action);
            audit.setPerformedBy(user);
            audit.setSummary(summary);
            audit.setChanges(changesJson);

            auditRepo.save(audit);
            logger.debug("Recorded audit for customerId={}, action={}", customerId, action);
        } catch (Exception e) {
            logger.error("Failed to record customer audit: {}", e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<CustomerAudit> getAuditTrail(Long customerId) {
        return auditRepo.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Transactional(readOnly = true)
    public Page<CustomerAudit> getAuditTrailPaged(Long customerId, Pageable pageable) {
        return auditRepo.findByCustomerId(customerId, pageable);
    }
}
