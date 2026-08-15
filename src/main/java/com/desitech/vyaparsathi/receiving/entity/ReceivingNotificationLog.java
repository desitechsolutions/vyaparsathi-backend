package com.desitech.vyaparsathi.receiving.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Audit trail of every notification pushed from the receiving module.
 * Mirrors the delivery-log pattern used elsewhere so operators can inspect
 * what went out, to whom, and when — without spelunking through mail server
 * logs.
 */
@Entity
@Table(name = "receiving_notification_log")
@Getter
@Setter
@NoArgsConstructor
public class ReceivingNotificationLog extends ShopAwareEntity {

    @Column(name = "receiving_id")
    private Long receivingId;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "event", nullable = false, length = 50)
    private String event;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "recipient", length = 200)
    private String recipient;

    @Column(name = "subject", length = 200)
    private String subject;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();

    @Column(name = "status", nullable = false, length = 20)
    private String status = "SENT";

    @Column(name = "error_note", length = 500)
    private String errorNote;
}
