package com.desitech.vyaparsathi.common.listener;

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
        if (entity instanceof ShopAwareEntity shopEntity) {
            if (shopEntity.getShop() == null) {
                Long currentShopId = TenantContext.getCurrentShopId();
                if (currentShopId == null) {
                    log.error("❌ No shopId found in TenantContext while saving {}", entity.getClass().getSimpleName());
                    throw new IllegalStateException("No shopId in TenantContext");
                }

                // Get Spring-managed EntityManager
                EntityManager em = SpringContext.getBean(EntityManager.class);
                Shop shopRef = em.getReference(Shop.class, currentShopId);
                shopEntity.setShop(shopRef);

                log.debug("✅ Automatically set shop (id={}) for {}", currentShopId, entity.getClass().getSimpleName());
            }
        }
    }
}
