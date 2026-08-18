package com.desitech.vyaparsathi.common.listener;

import com.desitech.vyaparsathi.audit.entity.AuditLog;
import com.desitech.vyaparsathi.auth.entity.PasswordResetToken;
import com.desitech.vyaparsathi.auth.entity.RefreshToken;
import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.common.util.SpringContext;
import com.desitech.vyaparsathi.document.entity.DocumentPrintAudit;
import com.desitech.vyaparsathi.shop.entity.Shop;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShopEntityListener {
    private static final Logger log = LoggerFactory.getLogger(ShopEntityListener.class);

    @PrePersist
    @PreUpdate
    public void setShopBeforeSave(Object entity) {

        Long currentShopId = TenantContext.getCurrentShopId();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isSuperAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));

        // Allow saving without shop during registration/login/onboarding for specific entities
        if (currentShopId == null) {
            if (entity instanceof User || entity instanceof RefreshToken || entity instanceof PasswordResetToken) {
                log.debug("Skipping shop enforcement for {} (no TenantContext – onboarding/login flow)",
                        entity.getClass().getSimpleName());
                return;
            }
            // AuditLog captures platform-admin (SUPER_ADMIN / TECH_ADMIN /
            // SUPPORT_AGENT / BILLING_ADMIN) events that legitimately have no
            // shop context. PENDING_OWNER events are filtered upstream by
            // AuditAspect, so anything reaching this path is either a
            // platform admin action or a system-level event. Multi-tenancy
            // stays intact because ShopFilterAspect scopes tenant queries
            // by shop_id — a null-shop audit row can only be surfaced
            // through platform-admin endpoints that bypass the filter.
            if (entity instanceof AuditLog) {
                log.debug("Skipping shop enforcement for AuditLog (platform-level event)");
                return;
            }
            // DocumentPrintAudit is a write-once audit log and may be written from
            // public / signed-URL PDF endpoints where TenantContext is empty.
            // Silently allow — the shop foreign key is nullable for this table.
            if (entity instanceof DocumentPrintAudit) {
                log.debug("Skipping shop enforcement for DocumentPrintAudit (public/signed-URL PDF path)");
                return;
            }
            if (isSuperAdmin && entity instanceof ShopAwareEntity sae && sae.getShop() != null) {
                return;
            }
            // For all other entities → still fail if no context
            log.error("❌ No shopId found in TenantContext while saving {}", entity.getClass().getSimpleName());
            throw new IllegalStateException("No shopId in TenantContext");
        }

        // Normal case: set shop if missing
        if (entity instanceof ShopAwareEntity shopEntity) {
            if (shopEntity.getShop() == null) {
                EntityManager em = SpringContext.getBean(EntityManager.class);
                Shop shopRef = em.getReference(Shop.class, currentShopId);
                shopEntity.setShop(shopRef);
                log.debug("✅ Set shop id={} for {}", currentShopId, entity.getClass().getSimpleName());
            }
        }
    }
}