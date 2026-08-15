package com.desitech.vyaparsathi.document.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Typed cross-reference between two documents. The renderer's "Reference
 * Documents" block queries this table; reports use it to walk the doc chain
 * end-to-end (PO → GRN → Invoice → Payment / RTV / DN).
 */
@Entity
@Table(name = "document_reference")
@Getter
@Setter
@NoArgsConstructor
public class DocumentReference extends ShopAwareEntity {

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "source_number", length = 50)
    private String sourceNumber;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "target_number", length = 50)
    private String targetNumber;

    /** PARENT | DERIVED | CANCELS | REVISES | APPLIES_TO */
    @Column(name = "link_kind", nullable = false, length = 30)
    private String linkKind;
}
