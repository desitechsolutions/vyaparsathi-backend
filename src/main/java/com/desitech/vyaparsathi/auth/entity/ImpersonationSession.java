package com.desitech.vyaparsathi.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "impersonation_sessions")
@Getter
@Setter
@NoArgsConstructor
public class ImpersonationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_uuid", nullable = false, unique = true, length = 64)
    private String sessionUuid;

    @Column(name = "super_admin_id", nullable = false)
    private Long superAdminId;

    @Column(name = "super_admin_email", nullable = false, length = 100)
    private String superAdminEmail;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "target_shop_id", nullable = false)
    private Long targetShopId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;
}
