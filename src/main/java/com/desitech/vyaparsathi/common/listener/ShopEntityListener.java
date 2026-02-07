package com.desitech.vyaparsathi.common.listener;

import com.desitech.vyaparsathi.auth.entity.RefreshToken;
import com.desitech.vyaparsathi.auth.entity.User;  // ← import your User class
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.common.util.SpringContext;
import com.desitech.vyaparsathi.shop.entity.Shop;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ShopEntityListener {

    @PrePersist
    @PreUpdate
    public void setShopBeforeSave(Object entity) {

        Long currentShopId = TenantContext.getCurrentShopId();

        // Allow saving without shop during registration/login/onboarding for specific entities
        if (currentShopId == null) {
            if (entity instanceof User || entity instanceof RefreshToken) {
                log.debug("Skipping shop enforcement for {} (no TenantContext – onboarding/login flow)",
                        entity.getClass().getSimpleName());
                return;  // ← Skip completely
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