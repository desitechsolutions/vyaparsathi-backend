package com.desitech.vyaparsathi.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Traced outbound impact of a batch recall. Records every sale/transfer/GRN
 * that touched the recalled batch so the operator can contact affected parties.
 */
@Entity
@Table(name = "batch_recall_impact")
@Getter
@Setter
@NoArgsConstructor
public class BatchRecallImpact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_recall_id", nullable = false)
    private BatchRecall batchRecall;

    /** SALE / TRANSFER / RECEIVING / etc. */
    @Column(name = "reference_type", nullable = false, length = 30)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "quantity", precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "party_name", length = 200)
    private String partyName;

    @Column(name = "party_contact", length = 200)
    private String partyContact;

    /** NOTIFIED / RECOVERED / DESTROYED / RETURNED / N/A */
    @Column(name = "outcome", length = 30)
    private String outcome;

    @Column(name = "logged_at", nullable = false)
    private LocalDateTime loggedAt = LocalDateTime.now();
}
