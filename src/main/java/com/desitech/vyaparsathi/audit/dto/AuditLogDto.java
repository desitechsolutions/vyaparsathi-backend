package com.desitech.vyaparsathi.audit.dto;

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
    private String ipAddress;
    private String userAgent;

    // Extended SuperAdmin Control Center audit fields
    private Long actorAdminId;
    private Long targetShopId;
    private String reason;
    private String previousValue;
    private String newValue;
    private String impersonationSessionId;
}