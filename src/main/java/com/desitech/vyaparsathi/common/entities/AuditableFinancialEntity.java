package com.desitech.vyaparsathi.common.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Base class for financial documents that need an author trail.
 * Activates Spring Data JPA AuditingEntityListener so that
 * {@code createdBy} / {@code updatedBy} are populated from the
 * {@code AuditorAware<String>} bean (JWT username) on every write.
 *
 * <p>{@code createdAt} / {@code updatedAt} timestamps are still managed
 * by {@link BaseEntity}'s {@code @PrePersist} / {@code @PreUpdate} hooks —
 * no conflict because those hooks touch different fields.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class AuditableFinancialEntity extends ShopAwareEntity {

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 255)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 255)
    private String updatedBy;
}
