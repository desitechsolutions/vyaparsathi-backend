package com.desitech.vyaparsathi.audit.helper;

import com.desitech.vyaparsathi.audit.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@RequiredArgsConstructor
public class AuditHelper {

    private final AuditLogService auditLogService;

    public void log(String action, String entity, String entityId, String details) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        HttpServletRequest request = ((ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes()).getRequest();

        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }

        String ua = request.getHeader("User-Agent");

        auditLogService.log(username, action, entity, entityId, details, ip, ua);
    }
}