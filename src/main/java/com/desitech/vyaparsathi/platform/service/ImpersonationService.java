package com.desitech.vyaparsathi.platform.service;

import com.desitech.vyaparsathi.audit.entity.AuditLog;
import com.desitech.vyaparsathi.audit.repository.AuditLogRepository;
import com.desitech.vyaparsathi.auth.entity.ImpersonationSession;
import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.repository.ImpersonationSessionRepository;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.platform.dto.ImpersonationResponseDto;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImpersonationService {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final ImpersonationSessionRepository impersonationSessionRepository;
    private final ImpersonationSessionCacheManager impersonationSessionCacheManager;
    private final AuditLogRepository auditLogRepository;
    private final JwtUtil jwtUtil;

    @Transactional
    public ImpersonationResponseDto startImpersonation(Long targetShopId, Long targetUserId, String reason, Long superAdminId, String superAdminEmail, String sourceIp) {
        Shop targetShop = shopRepository.findById(targetShopId)
                .orElseThrow(() -> new IllegalArgumentException("Target shop not found: " + targetShopId));

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found: " + targetUserId));

        String sessionUuid = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(15); // 15-minute TTL

        ImpersonationSession session = new ImpersonationSession();
        session.setSessionUuid(sessionUuid);
        session.setSuperAdminId(superAdminId);
        session.setSuperAdminEmail(superAdminEmail);
        session.setTargetUserId(targetUserId);
        session.setTargetShopId(targetShopId);
        session.setReason(reason);
        session.setStartedAt(now);
        session.setExpiresAt(expiresAt);
        session.setSourceIp(sourceIp);
        impersonationSessionRepository.save(session);

        // Structured security log
        log.info("[SECURITY IMPERSONATION STARTED] adminId={}, targetShopId={}, targetUserId={}, sessionUuid={}",
                superAdminId, targetShopId, targetUserId, sessionUuid);

        // Generate Impersonation Token
        String token = jwtUtil.generateImpersonationToken(targetUser, targetShopId, sessionUuid, superAdminId, superAdminEmail);

        // Audit Log entry
        AuditLog audit = new AuditLog();
        audit.setUsername(superAdminEmail);
        audit.setAction("IMPERSONATION_STARTED");
        audit.setEntity("User");
        audit.setEntityId(String.valueOf(targetUserId));
        audit.setActorAdminId(superAdminId);
        audit.setTargetShopId(targetShopId);
        audit.setImpersonationSessionId(sessionUuid);
        audit.setReason(reason);
        audit.setTimestamp(now);
        audit.setIpAddress(sourceIp);
        audit.setDetails("SuperAdmin " + superAdminEmail + " initiated 15-min impersonation for user " + targetUser.getUsername() + " on shop " + targetShop.getName() + ". Reason: " + reason);
        auditLogRepository.save(audit);

        ImpersonationResponseDto dto = new ImpersonationResponseDto();
        dto.setToken(token);
        dto.setSessionUuid(sessionUuid);
        dto.setTargetShopId(targetShopId);
        dto.setTargetShopName(targetShop.getName());
        dto.setTargetUsername(targetUser.getUsername());
        dto.setExpiresAt(expiresAt);
        return dto;
    }

    @Transactional
    public void exitImpersonation(String sessionUuid, Long superAdminId, String superAdminEmail) {
        impersonationSessionRepository.findBySessionUuid(sessionUuid).ifPresent(session -> {
            session.setEndedAt(LocalDateTime.now());
            impersonationSessionRepository.save(session);

            // Instantly invalidate in-memory cache
            impersonationSessionCacheManager.evictSession(sessionUuid);

            // Structured security log
            log.info("[SECURITY IMPERSONATION EXITED] adminId={}, sessionUuid={}", superAdminId, sessionUuid);

            AuditLog audit = new AuditLog();
            audit.setUsername(superAdminEmail);
            audit.setAction("IMPERSONATION_ENDED");
            audit.setEntity("ImpersonationSession");
            audit.setEntityId(sessionUuid);
            audit.setActorAdminId(superAdminId);
            audit.setTargetShopId(session.getTargetShopId());
            audit.setImpersonationSessionId(sessionUuid);
            audit.setTimestamp(LocalDateTime.now());
            audit.setDetails("SuperAdmin " + superAdminEmail + " manually exited impersonation session " + sessionUuid);
            auditLogRepository.save(audit);
        });
    }
}
