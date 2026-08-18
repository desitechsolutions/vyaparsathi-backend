package com.desitech.vyaparsathi.document.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.common.enums.DocumentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One row per PDF download / print request. Fuels the compliance dashboard's
 * "print audit" report (§35 of the CGST Rules requires the taxpayer to be
 * able to demonstrate how many copies of a statutory doc were issued).
 *
 * <p>The V99 schema is denormalised on purpose — a stable snapshot of the
 * doc number and document-hash at print time so a later edit to the source
 * doc doesn't rewrite the audit trail.
 */
@Entity
@Table(name = "document_print_audit")
@Getter
@Setter
@NoArgsConstructor
public class DocumentPrintAudit extends ShopAwareEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "document_number", length = 50)
    private String documentNumber;

    @Column(name = "printed_at", nullable = false)
    private LocalDateTime printedAt = LocalDateTime.now();

    @Column(name = "printed_by")
    private Long printedBy;

    @Column(name = "printed_by_name", length = 120)
    private String printedByName;

    /** True when there is already ≥1 print row for this (type, id). Set by
     *  the writer service — a duplicate print is the operational signal
     *  §31 auditors look for. */
    @Column(name = "is_duplicate", nullable = false)
    private Boolean isDuplicate = Boolean.FALSE;

    @Column(name = "document_hash_snapshot", length = 80)
    private String documentHashSnapshot;
}
