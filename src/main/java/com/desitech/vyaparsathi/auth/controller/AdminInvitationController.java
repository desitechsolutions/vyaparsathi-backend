package com.desitech.vyaparsathi.auth.controller;

import com.desitech.vyaparsathi.auth.entity.AdminInvitation;
import com.desitech.vyaparsathi.auth.security.CustomUserDetails;
import com.desitech.vyaparsathi.auth.service.AdminInvitationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AdminInvitationController {

    private final AdminInvitationService invitationService;

    // --- PROTECTED SUPERADMIN ENDPOINTS ---

    @GetMapping("/api/admin/invitations")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<AdminInvitation>> getPendingInvitations() {
        return ResponseEntity.ok(invitationService.getPendingInvitations());
    }

    @PostMapping("/api/admin/invitations")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<InvitationResponse> createInvitation(
            @RequestBody InvitationRequest request,
            @AuthenticationPrincipal CustomUserDetails admin) {

        String token = invitationService.createInvitation(
                request.getEmail(),
                request.getRole(),
                admin.getId(),
                admin.getUsername()
        );

        InvitationResponse resp = new InvitationResponse();
        resp.setEmail(request.getEmail());
        resp.setRole(request.getRole() != null ? request.getRole() : "TECH_ADMIN");
        resp.setInvitationToken(token);
        resp.setMessage("Admin invitation generated successfully. Share raw token securely.");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/admin/invitations/{id}/revoke")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<String> revokeInvitation(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails admin) {

        invitationService.revokeInvitation(id, admin.getId(), admin.getUsername());
        return ResponseEntity.ok("Invitation revoked successfully.");
    }

    // --- PUBLIC INVITATION ACCEPTANCE ENDPOINTS ---

    @GetMapping("/api/v1/public/invitations/validate")
    public ResponseEntity<AdminInvitation> validateToken(@RequestParam String token) {
        return ResponseEntity.ok(invitationService.validateInvitationToken(token));
    }

    @PostMapping("/api/v1/public/invitations/accept")
    public ResponseEntity<String> acceptInvitation(@RequestBody AcceptRequest request) {
        invitationService.acceptInvitation(
                request.getToken(),
                request.getPassword(),
                request.getFirstName(),
                request.getLastName()
        );
        return ResponseEntity.ok("Platform admin account created successfully.");
    }

    @Data
    public static class InvitationRequest {
        private String email;
        private String role;
    }

    @Data
    public static class InvitationResponse {
        private String email;
        private String role;
        private String invitationToken;
        private String message;
    }

    @Data
    public static class AcceptRequest {
        private String token;
        private String password;
        private String firstName;
        private String lastName;
    }
}
