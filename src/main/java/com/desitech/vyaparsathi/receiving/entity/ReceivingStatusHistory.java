package com.desitech.vyaparsathi.receiving.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Per-transition record for every GRN status change. Mirrors
 * {@code DeliveryStatusHistory} — enterprise compliance and dispute resolution
 * both need "who flipped what, when, why" beyond {@code @LastModifiedDate}
 * (which only captures the latest edit).
 */
@Entity
@Table(name = "receiving_status_history")
@Getter
@Setter
@NoArgsConstructor
public class ReceivingStatusHistory extends ShopAwareEntity {

    @Column(name = "receiving_id", nullable = false)
    private Long receivingId;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    @Column(name = "changed_by", length = 100)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    @Column(name = "note", length = 500)
    private String note;
}
