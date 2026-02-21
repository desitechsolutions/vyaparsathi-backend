package com.desitech.vyaparsathi.common.configs;

import org.springframework.stereotype.Component;

@Component
public class TenantContext {
    private static final ThreadLocal<Long> currentShopId = new InheritableThreadLocal<>();

    public static void setCurrentShopId(Long shopId) {
        currentShopId.set(shopId);
    }

    public static Long getCurrentShopId() {
        return currentShopId.get();
    }

    public static void clear() {
        currentShopId.remove();
    }
}