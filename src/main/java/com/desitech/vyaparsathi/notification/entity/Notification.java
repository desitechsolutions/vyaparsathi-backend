package com.desitech.vyaparsathi.notification.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Data
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String type;
    private String message;
    private String recipient;
    private boolean read;
    private String link;
    private LocalDateTime timestamp;
}
