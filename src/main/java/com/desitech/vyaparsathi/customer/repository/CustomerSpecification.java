package com.desitech.vyaparsathi.customer.repository;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.enums.CustomerType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic JPA Specification builder for the Customer entity.
 * <p>
 * Always scopes to the current tenant via {@link TenantContext#getCurrentShopId()}.
 * The Hibernate {@code @Filter} on {@code ShopAwareEntity} already provides
 * row-level tenant isolation at the ORM layer, but we also include an
 * explicit {@code shop.id = ?} predicate as defence-in-depth — Specification
 * queries constructed via CriteriaBuilder can bypass Hibernate filters in
 * some JPA implementations.
 */
public final class CustomerSpecification {

    private CustomerSpecification() {}

    /**
     * Builds a composite specification from the supplied filter parameters.
     * Null/blank parameters are silently ignored — callers never need to
     * null-check before passing.
     */
    public static Specification<Customer> withFilters(
            String search,
            Boolean active,
            String customerType,
            String source,
            String city,
            String tags) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // ── Tenant scope (defence-in-depth) ─────────────────────────
            Long shopId = TenantContext.getCurrentShopId();
            if (shopId != null) {
                predicates.add(cb.equal(root.get("shop").get("id"), shopId));
            }

            // Free-text search across name, phone, email, trade name
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("tradeName")), pattern),
                        cb.like(root.get("phone"), "%" + search.trim() + "%"),
                        cb.like(cb.lower(root.get("email")), pattern)
                ));
            }

            // Active filter
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            // Customer type
            if (customerType != null && !customerType.isBlank()) {
                try {
                    CustomerType type = CustomerType.valueOf(customerType.trim().toUpperCase());
                    predicates.add(cb.equal(root.get("customerType"), type));
                } catch (IllegalArgumentException ignored) { /* skip invalid type */ }
            }

            // Lead source
            if (source != null && !source.isBlank()) {
                predicates.add(cb.equal(root.get("source"), source.trim()));
            }

            // City
            if (city != null && !city.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("city")),
                        "%" + city.trim().toLowerCase() + "%"));
            }

            // Tags — comma-separated search; any match
            if (tags != null && !tags.isBlank()) {
                String tagPattern = "%" + tags.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("tags")), tagPattern));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
