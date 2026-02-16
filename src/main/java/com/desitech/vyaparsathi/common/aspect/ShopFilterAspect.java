package com.desitech.vyaparsathi.common.aspect;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class ShopFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Pointcut to match all methods in any class annotated with @Repository.
     */
    @Around("execution(* org.springframework.data.repository.Repository+.*(..)) && !@annotation(com.desitech.vyaparsathi.common.annotations.SkipShopFilter)")
    public Object applyShopFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        Long shopId = getCurrentShopId();;

        if (shopId == null) {
            log.debug("Skipping shopFilter (no shopId in TenantContext) for {}", joinPoint.getSignature());
            return joinPoint.proceed();
        }
        Session session = entityManager.unwrap(Session.class);
        log.debug("Applying shopFilter with shopId: {}", shopId);
        session.enableFilter("shopFilter").setParameter("shopId", shopId);
        try {
            return joinPoint.proceed();
        } finally {
            // Ensure the filter is disabled after the operation to prevent state leakage
            session.disableFilter("shopFilter");
            log.debug("Disabled shopFilter for current session.");
        }
    }

    private Long getCurrentShopId() {
        Long shopId = TenantContext.getCurrentShopId();
        if (shopId == null) {
            log.error("No shop found in TenantContext for repository call");
            //throw new ApplicationException("No shopId found in TenantContext");
        }
        return shopId;
    }
}
