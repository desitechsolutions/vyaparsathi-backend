package com.desitech.vyaparsathi.rbac.aspect;

import com.desitech.vyaparsathi.rbac.annotation.RequirePermission;
import com.desitech.vyaparsathi.rbac.service.PermissionResolver;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Enforces {@link RequirePermission}. Runs BEFORE the method body — if
 * the acting user doesn't have the required permission(s) in their
 * active shop, an {@link AccessDeniedException} is thrown and the
 * business logic never executes.
 *
 * <p>The message deliberately names the missing permission code so the
 * frontend / support team can spot RBAC misconfigurations without
 * turning on debug logging. It is safe to leak — the code alone doesn't
 * expose sensitive data.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class PermissionCheckAspect {

    private static final Logger log = LoggerFactory.getLogger(PermissionCheckAspect.class);

    private final PermissionResolver permissionResolver;

    @Around("@annotation(requirePermission)")
    public Object check(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        String[] required = requirePermission.value();
        if (required == null || required.length == 0) {
            // A @RequirePermission with no codes is a bug — fail closed.
            throw new AccessDeniedException("Permission check misconfigured: no codes specified.");
        }

        Set<String> held = permissionResolver.currentUserPermissions();
        boolean ok = switch (requirePermission.mode()) {
            case ALL -> containsAll(held, required);
            case ANY -> containsAny(held, required);
        };

        if (!ok) {
            String missing = requirePermission.mode() == RequirePermission.Mode.ALL
                    ? firstMissing(held, required)
                    : String.join(" or ", required);
            log.warn("AccessDenied: user lacks permission '{}' on {}",
                    missing, joinPoint.getSignature().toShortString());
            throw new AccessDeniedException("You do not have permission to perform this action. Missing: " + missing);
        }

        return joinPoint.proceed();
    }

    private boolean containsAll(Set<String> held, String[] required) {
        for (String r : required) if (!held.contains(r)) return false;
        return true;
    }

    private boolean containsAny(Set<String> held, String[] required) {
        for (String r : required) if (held.contains(r)) return true;
        return false;
    }

    private String firstMissing(Set<String> held, String[] required) {
        for (String r : required) if (!held.contains(r)) return r;
        return required[0];
    }
}
