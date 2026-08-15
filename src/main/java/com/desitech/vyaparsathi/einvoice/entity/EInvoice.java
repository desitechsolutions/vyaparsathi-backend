package com.desitech.vyaparsathi.einvoice.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * IRP-issued e-Invoice record. One document (Tax Invoice / Debit Note /
 * Credit Note) may have at most one active {@link EInvoice}; a cancellation
 * flips {@code status = CANCELLED} and the caller must generate a new one
 * with a fresh IRN.
 */
@Entity
@Table(name = "e_invoice")
@Getter
@Setter
@NoArgsConstructor
public class EInvoice extends ShopAwareEntity {

    @Column(name = "document_type", nullable = false, length = 30)
    private String documentType;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "document_number", length = 50)
    private String documentNumber;

    @Column(name = "irn", nullable = false, unique = true, length = 80)
    private String irn;

    @Column(name = "ack_number", length = 50)
    private String ackNumber;

    @Column(name = "ack_date")
    private LocalDateTime ackDate;

    @Column(name = "qr_payload", columnDefinition = "TEXT")
    private String qrPayload;

    @Column(name = "signed_invoice", columnDefinition = "MEDIUMTEXT")
    private String signedInvoice;

    @Column(name = "signed_qr_code", columnDefinition = "TEXT")
    private String signedQrCode;

    /** GENERATED | CANCELLED | FAILED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "GENERATED";

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "raw_response", columnDefinition = "MEDIUMTEXT")
    private String rawResponse;
}
