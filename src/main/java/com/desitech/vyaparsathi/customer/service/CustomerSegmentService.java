package com.desitech.vyaparsathi.customer.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.customer.dto.CustomerSegmentDto;
import com.desitech.vyaparsathi.customer.entity.CustomerSegment;
import com.desitech.vyaparsathi.customer.mapper.CustomerSegmentMapper;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import com.desitech.vyaparsathi.customer.repository.CustomerSegmentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Shop-scoped segments (the normalised replacement for the CSV
 * {@code customer.tags} column) and per-customer membership.
 *
 * <p>The join table {@code customer_segment_member} is intentionally
 * NOT modelled as a JPA entity — an @ManyToMany on Customer would
 * slow down every Customer load. Membership operations use native
 * SQL against the join instead.</p>
 */
@Service
public class CustomerSegmentService {

    private final CustomerSegmentRepository repo;
    private final CustomerRepository customerRepo;
    private final CustomerSegmentMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public CustomerSegmentService(CustomerSegmentRepository repo,
                                  CustomerRepository customerRepo,
                                  CustomerSegmentMapper mapper) {
        this.repo = repo;
        this.customerRepo = customerRepo;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<CustomerSegmentDto> listAll() {
        List<CustomerSegment> segments = repo.findAllByOrderByNameAsc();
        List<CustomerSegmentDto> dtos = mapper.toDtoList(segments);
        // Populate memberCount per segment — one aggregate query, not N.
        if (!dtos.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Object[]> counts = em.createNativeQuery(
                    "SELECT segment_id, COUNT(*) FROM customer_segment_member " +
                    "WHERE segment_id IN (:ids) GROUP BY segment_id")
                    .setParameter("ids", segments.stream().map(CustomerSegment::getId).toList())
                    .getResultList();
            java.util.Map<Long, Long> byId = new java.util.HashMap<>();
            for (Object[] row : counts) {
                byId.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            }
            for (CustomerSegmentDto dto : dtos) {
                dto.setMemberCount(byId.getOrDefault(dto.getId(), 0L));
            }
        }
        return dtos;
    }

    @Transactional
    public CustomerSegmentDto create(CustomerSegmentDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new ApplicationException("Segment name is required");
        }
        repo.findByNameIgnoreCase(dto.getName().trim()).ifPresent(existing -> {
            throw new ApplicationException("A segment named '" + existing.getName() + "' already exists.");
        });
        CustomerSegment entity = mapper.toEntity(dto);
        entity.setId(null);
        entity.setName(dto.getName().trim());
        return mapper.toDto(repo.save(entity));
    }

    @Transactional
    public CustomerSegmentDto update(Long segmentId, CustomerSegmentDto dto) {
        CustomerSegment existing = repo.findById(segmentId)
                .orElseThrow(() -> new EntityNotFoundException("Segment not found: " + segmentId));
        // Refuse a rename that collides with another segment (case-insensitive).
        if (dto.getName() != null && !dto.getName().equalsIgnoreCase(existing.getName())) {
            repo.findByNameIgnoreCase(dto.getName().trim()).ifPresent(other -> {
                if (!other.getId().equals(existing.getId())) {
                    throw new ApplicationException("A segment named '" + other.getName() + "' already exists.");
                }
            });
        }
        mapper.updateEntityFromDto(dto, existing);
        return mapper.toDto(repo.save(existing));
    }

    @Transactional
    public void delete(Long segmentId) {
        CustomerSegment existing = repo.findById(segmentId)
                .orElseThrow(() -> new EntityNotFoundException("Segment not found: " + segmentId));
        long memberCount = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM customer_segment_member WHERE segment_id = :sid")
                .setParameter("sid", segmentId)
                .getSingleResult()).longValue();
        if (memberCount > 0) {
            throw new ApplicationException(
                    "Cannot delete segment '" + existing.getName() + "' — " + memberCount + " customer(s) are still assigned. Remove them from the segment first.");
        }
        repo.delete(existing);
    }

    // ─── Membership operations ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CustomerSegmentDto> segmentsForCustomer(Long customerId) {
        ensureCustomerExists(customerId);
        return mapper.toDtoList(repo.findByCustomerId(customerId));
    }

    @Transactional
    public void attach(Long customerId, Long segmentId) {
        ensureCustomerExists(customerId);
        if (!repo.existsById(segmentId)) {
            throw new EntityNotFoundException("Segment not found: " + segmentId);
        }
        // INSERT IGNORE so idempotent — attaching a customer twice is a no-op,
        // not an error. MySQL-specific but the rest of the app is on MySQL 8.
        em.createNativeQuery(
                "INSERT IGNORE INTO customer_segment_member (customer_id, segment_id, added_at) " +
                "VALUES (:cid, :sid, CURRENT_TIMESTAMP)")
                .setParameter("cid", customerId)
                .setParameter("sid", segmentId)
                .executeUpdate();
    }

    @Transactional
    public void detach(Long customerId, Long segmentId) {
        ensureCustomerExists(customerId);
        em.createNativeQuery(
                "DELETE FROM customer_segment_member WHERE customer_id = :cid AND segment_id = :sid")
                .setParameter("cid", customerId)
                .setParameter("sid", segmentId)
                .executeUpdate();
    }

    /** Full-replace: set the customer's segment membership to exactly the given ids. */
    @Transactional
    public List<CustomerSegmentDto> replaceMembership(Long customerId, List<Long> segmentIds) {
        ensureCustomerExists(customerId);
        em.createNativeQuery(
                "DELETE FROM customer_segment_member WHERE customer_id = :cid")
                .setParameter("cid", customerId)
                .executeUpdate();
        if (segmentIds != null) {
            for (Long sid : segmentIds) {
                if (sid == null) continue;
                if (!repo.existsById(sid)) continue;
                em.createNativeQuery(
                        "INSERT IGNORE INTO customer_segment_member (customer_id, segment_id, added_at) " +
                        "VALUES (:cid, :sid, CURRENT_TIMESTAMP)")
                        .setParameter("cid", customerId)
                        .setParameter("sid", sid)
                        .executeUpdate();
            }
        }
        return segmentsForCustomer(customerId);
    }

    private void ensureCustomerExists(Long customerId) {
        if (customerId == null) {
            throw new ApplicationException("customerId is required");
        }
        if (!customerRepo.existsById(customerId)) {
            throw new EntityNotFoundException("Customer not found: " + customerId);
        }
        // Belt-and-braces: the ShopFilterAspect covers this, but confirm
        // the current tenant is set so no cross-tenant membership can leak.
        if (TenantContext.getCurrentShopId() == null) {
            throw new ApplicationException("No active shop context.");
        }
    }
}
