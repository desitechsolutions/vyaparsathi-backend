package com.desitech.vyaparsathi.receiving.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@EntityListeners(AuditingEntityListener.class)
public class ReceivingTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiving_id", nullable = false)
    private Receiving receiving;

    private String reason;

    private String description;

    private String status;

    @CreatedDate
    private LocalDateTime raisedAt;

    private String raisedBy;

    @OneToMany(mappedBy = "receivingTicket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReceivingTicketAttachment> attachments;

    @LastModifiedDate
    private LocalDateTime lastUpdatedAt;  // Added for auditing
}