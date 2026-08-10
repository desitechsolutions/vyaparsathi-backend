package com.desitech.vyaparsathi.common.aspect;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
@Component
public class ShopFilterAspect {
    private static final Logger log = LoggerFactory.getLogger(ShopFilterAspect.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Around("execution(* org.springframework.data.repository.Repository+.*(..))")
    public Object applyShopFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        if (shouldSkipFilter(joinPoint)) {
            return joinPoint.proceed();
        }

        Long shopId = TenantContext.getCurrentShopId();

        if (shopId == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean isSuperAdmin = auth != null && auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));

            boolean isPendingOwner = auth != null && auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_PENDING_OWNER"));

            boolean isUnauthenticated = auth == null 
                    || !auth.isAuthenticated() 
                    || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken;

            if (isSuperAdmin || isPendingOwner || isUnauthenticated) {
                // Super-admins, pending owners completing onboarding, and unauthenticated public requests
                // are intentionally allowed to proceed without a shop filter
                return joinPoint.proceed();
            }

            // Reject authenticated non-admins with missing shopId to prevent cross-tenant exposure
            log.error("SECURITY: shopId is null for authenticated non-admin call to {}. Rejecting to prevent cross-tenant data access.",
                    joinPoint.getSignature().toShortString());
            throw new org.springframework.security.access.AccessDeniedException(
                    "Shop context not set. Cannot execute repository operation without a valid shop scope.");
        }

        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("shopFilter").setParameter("shopId", shopId);

        try {
            return joinPoint.proceed();
        } finally {
            session.disableFilter("shopFilter");
        }
    }

    private boolean shouldSkipFilter(ProceedingJoinPoint joinPoint) {
        if (joinPoint.getSignature() instanceof org.aspectj.lang.reflect.MethodSignature methodSignature) {
            java.lang.reflect.Method method = methodSignature.getMethod();
            if (method.isAnnotationPresent(com.desitech.vyaparsathi.common.annotations.SkipShopFilter.class)) {
                return true;
            }
            if (method.getDeclaringClass().isAnnotationPresent(com.desitech.vyaparsathi.common.annotations.SkipShopFilter.class)) {
                return true;
            }
            if (joinPoint.getTarget() != null) {
                Class<?> targetClass = joinPoint.getTarget().getClass();
                if (targetClass.isAnnotationPresent(com.desitech.vyaparsathi.common.annotations.SkipShopFilter.class)) {
                    return true;
                }
                for (Class<?> iface : targetClass.getInterfaces()) {
                    if (iface.isAnnotationPresent(com.desitech.vyaparsathi.common.annotations.SkipShopFilter.class)) {
                        return true;
                    }
                }
            }
            if (joinPoint.getThis() != null) {
                Class<?> proxyClass = joinPoint.getThis().getClass();
                if (proxyClass.isAnnotationPresent(com.desitech.vyaparsathi.common.annotations.SkipShopFilter.class)) {
                    return true;
                }
                for (Class<?> iface : proxyClass.getInterfaces()) {
                    if (iface.isAnnotationPresent(com.desitech.vyaparsathi.common.annotations.SkipShopFilter.class)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }
}