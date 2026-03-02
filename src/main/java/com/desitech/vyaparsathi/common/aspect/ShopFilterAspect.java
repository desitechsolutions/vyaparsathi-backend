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

@Slf4j
@Aspect
@Component
public class ShopFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Around("execution(* org.springframework.data.repository.Repository+.*(..)) && !@annotation(com.desitech.vyaparsathi.common.annotations.SkipShopFilter)")
    public Object applyShopFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        Long shopId = TenantContext.getCurrentShopId();

        if (shopId == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean isSuperAdmin = auth != null && auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));

            if (isSuperAdmin) {
                return joinPoint.proceed();
            }

            // Only log warning for non-admins
            log.warn("No shopId for non-admin call to {}", joinPoint.getSignature().getName());
            return joinPoint.proceed();
        }

        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("shopFilter").setParameter("shopId", shopId);

        try {
            return joinPoint.proceed();
        } finally {
            session.disableFilter("shopFilter");
        }
    }

    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }
}