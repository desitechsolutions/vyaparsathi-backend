package com.desitech.vyaparsathi.audit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDto {
    private Long id;
    private String username;
    private String action;
    private String entity;
    private String entityId;
    private String details;
    private LocalDateTime timestamp;

    // --- NEW FORENSIC FIELDS ---
    private String ipAddress;
    private String userAgent;
}