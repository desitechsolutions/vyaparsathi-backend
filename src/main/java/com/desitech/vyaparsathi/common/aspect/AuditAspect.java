package com.desitech.vyaparsathi.common.aspect;

import com.desitech.vyaparsathi.common.annotations.AuditValue;
import com.desitech.vyaparsathi.common.annotations.LogAudit;
import com.desitech.vyaparsathi.audit.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogService auditLogService;

    @Around("@annotation(logAudit)")
    public Object audit(ProceedingJoinPoint joinPoint, LogAudit logAudit) throws Throwable {

        Object result = null;
        Exception businessException = null;

        try {
            // ✅ Execute business logic
            result = joinPoint.proceed();
            return result;

        } catch (Exception ex) {
            businessException = ex;
            throw ex;

        } finally {
            // ✅ Audit must never break business flow
            try {
                logAuditEvent(joinPoint, logAudit, result, businessException);
            } catch (Exception auditEx) {
                log.error("Audit logging failed: {}", auditEx.getMessage());
            }
        }
    }

    private void logAuditEvent(ProceedingJoinPoint joinPoint,
                               LogAudit logAudit,
                               Object result,
                               Exception businessException) {

        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        // ✅ Context Metadata
        String ip = request != null ? getClientIp(request) : "SYSTEM";
        String userAgent = request != null ? request.getHeader("User-Agent") : "SYSTEM";

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated())
                ? auth.getName()
                : "SYSTEM";

        // ✅ Generic Identification
        String entityId = extractId(result, joinPoint);

        Object identifierSource = result != null
                ? result
                : (joinPoint.getArgs().length > 0 ? joinPoint.getArgs()[0] : null);

        String identifier = findAnnotatedValue(identifierSource);

        // ✅ Status Handling
        String status = (businessException == null) ? "SUCCESS" : "FAILED";

        // ✅ Build Message
        String details = buildDetails(logAudit, identifier, status, businessException);

        auditLogService.log(
                username,
                logAudit.action(),
                logAudit.entity(),
                entityId,
                details,
                ip,
                userAgent
        );
    }

    private String buildDetails(LogAudit logAudit,
                                String identifier,
                                String status,
                                Exception ex) {

        String base = String.format("%s: %s%s",
                capitalize(logAudit.action().replace("_", " ")),
                logAudit.entity(),
                identifier != null ? " [" + identifier + "]" : "");

        if ("FAILED".equals(status) && ex != null) {
            return base + " (FAILED: " + ex.getMessage() + ")";
        }

        return base + " (" + status + ")";
    }

    /**
     * Scans the object for any field marked with @AuditValue.
     */
    private String findAnnotatedValue(Object obj) {
        if (obj == null) return null;

        Class<?> clazz = obj.getClass();

        while (clazz != null && clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                if (field.isAnnotationPresent(AuditValue.class)) {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(obj);
                        return value != null ? String.valueOf(value) : null;
                    } catch (IllegalAccessException e) {
                        log.debug("Failed to access field {} in class {}",
                                field.getName(),
                                clazz.getName(),
                                e);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }

        return null;
    }


    private String extractId(Object result, ProceedingJoinPoint joinPoint) {

        // Try result.getId()
        String id = tryMethod(result, "getId");
        if (id != null) return id;

        // Try first argument
        if (joinPoint.getArgs().length > 0) {
            Object arg = joinPoint.getArgs()[0];

            if (arg instanceof Number || arg instanceof String) {
                return arg.toString();
            }

            return tryMethod(arg, "getId");
        }

        return "N/A";
    }

    private String tryMethod(Object obj, String methodName) {
        if (obj == null) return null;

        try {
            Method m = obj.getClass().getMethod(methodName);
            Object val = m.invoke(obj);
            return val != null ? val.toString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Production Safe IP Resolver
     */
    private String getClientIp(HttpServletRequest request) {

        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "CF-Connecting-IP",
                "True-Client-IP"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
