package com.desitech.vyaparsathi.common.configs;

import org.springframework.stereotype.Component;

@Component
public class TenantContext {
    // Plain ThreadLocal (not InheritableThreadLocal). InheritableThreadLocal
    // copies the parent-thread value into child threads at creation time, which
    // means pooled async threads inherit the shopId of whichever request first
    // created them — a cross-tenant data-leak risk. With a plain ThreadLocal the
    // value is absent on a fresh pool thread, and TenantContextTaskDecorator in
    // AsyncConfig explicitly propagates+clears it for every task.
    private static final ThreadLocal<Long> currentShopId = new ThreadLocal<>();

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