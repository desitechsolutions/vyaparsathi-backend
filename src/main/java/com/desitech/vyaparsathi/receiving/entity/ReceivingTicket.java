package com.desitech.vyaparsathi.receiving.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ReceivingTicket extends ShopAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiving_id", nullable = false)
    private Receiving receiving;

    private String reason;
    private String description;
    /**
     * String-backed enum (see {@link com.desitech.vyaparsathi.receiving.enums.ReceivingTicketStatus}).
     * Kept as String on the column so V88's data migration remains a no-op and
     * lenient {@code fromString} handles legacy free-text values. New code
     * should read this via {@link #getStatusEnum()}.
     */
    private String status;

    @CreatedDate
    private LocalDateTime raisedAt;

    private String raisedBy;

    // V88 resolution audit — populated when the ticket moves to RESOLVED / CLOSED.
    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    /** V91 — debit note issued to the supplier for this dispute. */
    @Column(name = "debit_note_id")
    private Long debitNoteId;

    @OneToMany(mappedBy = "receivingTicket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReceivingTicketAttachment> attachments;

    @LastModifiedDate
    private LocalDateTime lastUpdatedAt;

    public Receiving getReceiving() { return receiving; }
    public void setReceiving(Receiving receiving) { this.receiving = receiving; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getRaisedAt() { return raisedAt; }
    public void setRaisedAt(LocalDateTime raisedAt) { this.raisedAt = raisedAt; }

    public String getRaisedBy() { return raisedBy; }
    public void setRaisedBy(String raisedBy) { this.raisedBy = raisedBy; }

    public List<ReceivingTicketAttachment> getAttachments() { return attachments; }
    public void setAttachments(List<ReceivingTicketAttachment> attachments) { this.attachments = attachments; }

    public LocalDateTime getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }

    public Long getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }

    public Long getDebitNoteId() { return debitNoteId; }
    public void setDebitNoteId(Long debitNoteId) { this.debitNoteId = debitNoteId; }

    /** Enum view of the string status — lenient on legacy free-text values. */
    public com.desitech.vyaparsathi.receiving.enums.ReceivingTicketStatus getStatusEnum() {
        return com.desitech.vyaparsathi.receiving.enums.ReceivingTicketStatus.fromString(status);
    }

    public void setStatusEnum(com.desitech.vyaparsathi.receiving.enums.ReceivingTicketStatus s) {
        this.status = s != null ? s.name() : null;
    }
}