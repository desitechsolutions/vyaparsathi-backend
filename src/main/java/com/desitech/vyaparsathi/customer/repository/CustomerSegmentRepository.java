package com.desitech.vyaparsathi.customer.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.customer.entity.CustomerSegment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerSegmentRepository extends BaseRepository<CustomerSegment, Long> {
    List<CustomerSegment> findAllByOrderByNameAsc();
    Optional<CustomerSegment> findByNameIgnoreCase(String name);

    /**
     * Fetch every segment membership for a customer in a single query.
     * Native SQL because there is no JPA-mapped join entity (the
     * membership table is intentionally not modelled as an entity to
     * keep Customer loads cheap).
     */
    @Query(value = "SELECT s.id, s.shop_id, s.name, s.description, s.color, s.created_at, s.updated_at " +
            "FROM customer_segment s " +
            "JOIN customer_segment_member m ON m.segment_id = s.id " +
            "WHERE m.customer_id = :customerId " +
            "ORDER BY s.name ASC",
            nativeQuery = true)
    List<CustomerSegment> findByCustomerId(@Param("customerId") Long customerId);
}
