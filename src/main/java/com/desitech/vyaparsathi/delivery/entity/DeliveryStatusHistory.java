package com.desitech.vyaparsathi.delivery.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.delivery.enums.DeliveryHistoryEventType;
import com.desitech.vyaparsathi.delivery.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "delivery_status_history")
public class DeliveryStatusHistory extends ShopAwareEntity {
    @ManyToOne
    @JoinColumn(name = "delivery_id")
    private Delivery delivery;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    /**
     * What kind of event this row represents. {@link DeliveryHistoryEventType#STATUS_CHANGE}
     * for status transitions, {@link DeliveryHistoryEventType#ASSIGNMENT} for delivery
     * person reassignments, {@link DeliveryHistoryEventType#ATTEMPT} for failed
     * delivery attempts. Existing rows default to {@code STATUS_CHANGE}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private DeliveryHistoryEventType eventType = DeliveryHistoryEventType.STATUS_CHANGE;

    /**
     * Free-text detail — e.g. "reassigned from Rahul to Vikash", "COD not paid,
     * customer not home". The (event_type, note) pair explains the row.
     */
    @Column(length = 500)
    private String note;

    private LocalDateTime changedAt = LocalDateTime.now();
    private String changedBy;

    public Delivery getDelivery() { return delivery; }
    public void setDelivery(Delivery delivery) { this.delivery = delivery; }

    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }

    public DeliveryHistoryEventType getEventType() { return eventType; }
    public void setEventType(DeliveryHistoryEventType eventType) { this.eventType = eventType; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }

    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
}