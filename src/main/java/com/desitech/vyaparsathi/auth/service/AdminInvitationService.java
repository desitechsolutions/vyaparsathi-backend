package com.desitech.vyaparsathi.auth.service;

import com.desitech.vyaparsathi.audit.entity.AuditLog;
import com.desitech.vyaparsathi.audit.repository.AuditLogRepository;
import com.desitech.vyaparsathi.auth.entity.AdminInvitation;
import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.model.Role;
import com.desitech.vyaparsathi.auth.repository.AdminInvitationRepository;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminInvitationService {

    private final AdminInvitationRepository adminInvitationRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public String createInvitation(String email, String role, Long adminId, String adminUsername) {
        if (adminInvitationRepository.existsByEmailAndStatus(email, "PENDING")) {
            throw new IllegalArgumentException("A pending invitation already exists for email: " + email);
        }

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);

        AdminInvitation invite = new AdminInvitation();
        invite.setEmail(email);
        invite.setRole(role != null ? role : "TECH_ADMIN");
        invite.setTokenHash(tokenHash);
        invite.setInvitedByAdminId(adminId);
        invite.setStatus("PENDING");
        invite.setExpiresAt(LocalDateTime.now().plusHours(48)); // 48-hour expiration
        invite.setCreatedAt(LocalDateTime.now());
        adminInvitationRepository.save(invite);

        AuditLog audit = new AuditLog();
        audit.setUsername(adminUsername);
        audit.setAction("ADMIN_INVITATION_CREATED");
        audit.setEntity("AdminInvitation");
        audit.setEntityId(email);
        audit.setActorAdminId(adminId);
        audit.setTimestamp(LocalDateTime.now());
        audit.setDetails("Created " + role + " invitation for " + email + ". Expires in 48h.");
        auditLogRepository.save(audit);

        return rawToken;
    }

    @Transactional(readOnly = true)
    public List<AdminInvitation> getPendingInvitations() {
        return adminInvitationRepository.findAll().stream()
                .filter(i -> "PENDING".equalsIgnoreCase(i.getStatus()))
                .toList();
    }

    @Transactional
    public void revokeInvitation(Long invitationId, Long adminId, String adminUsername) {
        AdminInvitation invite = adminInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found: " + invitationId));

        invite.setStatus("REVOKED");
        adminInvitationRepository.save(invite);

        AuditLog audit = new AuditLog();
        audit.setUsername(adminUsername);
        audit.setAction("ADMIN_INVITATION_REVOKED");
        audit.setEntity("AdminInvitation");
        audit.setEntityId(invite.getEmail());
        audit.setActorAdminId(adminId);
        audit.setTimestamp(LocalDateTime.now());
        audit.setDetails("Revoked pending invitation for " + invite.getEmail());
        auditLogRepository.save(audit);
    }

    @Transactional(readOnly = true)
    public AdminInvitation validateInvitationToken(String rawToken) {
        String hash = hashToken(rawToken);
        AdminInvitation invite = adminInvitationRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invitation token."));

        if (!"PENDING".equalsIgnoreCase(invite.getStatus())) {
            throw new IllegalStateException("Invitation is no longer active (status: " + invite.getStatus() + ").");
        }

        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Invitation token has expired.");
        }

        return invite;
    }

    @Transactional
    public void acceptInvitation(String rawToken, String password, String firstName, String lastName) {
        AdminInvitation invite = validateInvitationToken(rawToken);

        if (userRepository.findByEmail(invite.getEmail()).isPresent() || userRepository.findByUsername(invite.getEmail()).isPresent()) {
            throw new IllegalArgumentException("User with email " + invite.getEmail() + " already exists.");
        }

        User newAdmin = new User();
        newAdmin.setUsername(invite.getEmail());
        newAdmin.setEmail(invite.getEmail());
        newAdmin.setFirstName(firstName);
        newAdmin.setLastName(lastName);
        newAdmin.setPinHash(passwordEncoder.encode(password));
        newAdmin.setRole(Role.fromString(invite.getRole()));
        newAdmin.setActive(true);
        newAdmin.setShop(null); // Platform Admin
        userRepository.save(newAdmin);

        invite.setStatus("ACCEPTED");
        invite.setAcceptedAt(LocalDateTime.now());
        adminInvitationRepository.save(invite);

        AuditLog audit = new AuditLog();
        audit.setUsername(invite.getEmail());
        audit.setAction("ADMIN_INVITATION_ACCEPTED");
        audit.setEntity("User");
        audit.setEntityId(String.valueOf(newAdmin.getId()));
        audit.setTimestamp(LocalDateTime.now());
        audit.setDetails("Accepted invitation and created platform admin account with role " + invite.getRole());
        auditLogRepository.save(audit);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm missing", e);
        }
    }
}
