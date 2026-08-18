package com.desitech.vyaparsathi.auth.controller;

import com.desitech.vyaparsathi.auth.dto.UserSessionDto;
import com.desitech.vyaparsathi.auth.entity.RefreshToken;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.auth.service.SessionService;
import com.desitech.vyaparsathi.common.payload.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Active Sessions endpoints — surfaced in the Account Security UI so
 * a user can see every browser/device signed in as them and revoke
 * any of them individually or all-at-once.
 *
 *   GET    /api/auth/sessions                        — list
 *   DELETE /api/auth/sessions/{sessionId}            — revoke one
 *   POST   /api/auth/sessions/revoke-all-except-current — revoke all but this one
 */
@RestController
@RequestMapping("/api/auth/sessions")
public class UserSessionController {

    private static final Logger log = LoggerFactory.getLogger(UserSessionController.class);

    private final SessionService sessionService;
    private final JwtUtil jwtUtil;

    public UserSessionController(SessionService sessionService, JwtUtil jwtUtil) {
        this.sessionService = sessionService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public ResponseEntity<List<UserSessionDto>> listMySessions(Authentication auth, HttpServletRequest request) {
        String username = auth.getName();
        String currentSid = extractSessionIdFromRequest(request);

        List<UserSessionDto> body = sessionService.listActiveSessionsFor(username).stream()
                .map(row -> toDto(row, currentSid))
                .toList();
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<String>> revoke(@PathVariable String sessionId,
                                                      Authentication auth,
                                                      HttpServletRequest request) {
        String username = auth.getName();
        String currentSid = extractSessionIdFromRequest(request);

        // Guard against the user accidentally revoking their own
        // current session via this endpoint — the "Sign out" button
        // is a better UX for that. If they really want to, they can
        // still call the logout endpoint.
        if (Objects.equals(sessionId, currentSid)) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(
                    "error",
                    "To end this session, use Sign out instead.",
                    null));
        }

        sessionService.revokeSession(username, sessionId, "user-revoked");
        log.info("User {} revoked session {}", username, sessionId);
        return ResponseEntity.ok(new ApiResponse<>("success", "Session revoked.", null));
    }

    @PostMapping("/revoke-all-except-current")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revokeAllExceptCurrent(Authentication auth,
                                                                                    HttpServletRequest request) {
        String username = auth.getName();
        String currentSid = extractSessionIdFromRequest(request);
        int revoked = sessionService.revokeAllExceptCurrent(username, currentSid, "user-revoked-others");
        log.info("User {} revoked {} other sessions", username, revoked);

        Map<String, Object> data = new HashMap<>();
        data.put("revokedCount", revoked);
        return ResponseEntity.ok(new ApiResponse<>("success",
                revoked == 0 ? "No other sessions were active." : "Other sessions signed out.",
                data));
    }

    private String extractSessionIdFromRequest(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return jwtUtil.extractSessionId(header.substring(7));
    }

    private UserSessionDto toDto(RefreshToken row, String currentSid) {
        return new UserSessionDto(
                row.getSessionId(),
                row.getDeviceLabel(),
                row.getUserAgent(),
                row.getIpAddress(),
                row.getCreatedAt(),
                row.getLastActiveAt(),
                Objects.equals(row.getSessionId(), currentSid)
        );
    }
}
