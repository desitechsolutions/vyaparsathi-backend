package com.desitech.vyaparsathi.common.aspect;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.model.Role;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
@Component
public class AuditAspect {
    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditLogService auditLogService;

    public AuditAspect(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

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

        // Prefer a User argument's data (for pre-authentication events like login,
        // where SecurityContext is empty / anonymous). Falls back to
        // SecurityContext for anything the user is signed in for.
        User actorFromArgs = findUserArg(joinPoint);

        // Skip audit for PENDING_OWNER — those events (register, first login
        // before onboarding, forgot-password from the pre-shop state) don't
        // have a shop context to attach to. Writing them with a null shop_id
        // would break tenant queries; writing them at all adds noise without
        // audit value. Once the user completes onboarding, subsequent events
        // are audited normally.
        if (actorFromArgs != null && actorFromArgs.getRole() == Role.PENDING_OWNER) {
            return;
        }

        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        // ✅ Context Metadata
        String ip = request != null ? getClientIp(request) : "SYSTEM";
        String userAgent = request != null ? request.getHeader("User-Agent") : "SYSTEM";

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = resolveUsername(auth, actorFromArgs);

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
     * Looks in the method arguments for a {@link User} instance. Used to
     * recover the acting user's identity when the SecurityContext is empty
     * (as during login, before the JWT filter has run).
     */
    private User findUserArg(ProceedingJoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof User u) return u;
        }
        return null;
    }

    /**
     * Resolves the username to record on the audit row. Priority:
     *   1. A real, authenticated Spring principal (SecurityContext), when
     *      it isn't the anonymous placeholder.
     *   2. The User argument passed to the audited method (recovers login /
     *      register username when SecurityContext isn't set yet).
     *   3. Anonymous / SYSTEM fallback.
     */
    private String resolveUsername(Authentication auth, User actorFromArgs) {
        if (auth != null && auth.isAuthenticated()) {
            String name = auth.getName();
            if (name != null && !"anonymousUser".equalsIgnoreCase(name)) {
                return name;
            }
        }
        if (actorFromArgs != null && actorFromArgs.getUsername() != null) {
            return actorFromArgs.getUsername();
        }
        return auth != null ? auth.getName() : "SYSTEM";
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
