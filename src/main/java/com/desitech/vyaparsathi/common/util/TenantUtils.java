package com.desitech.vyaparsathi.common.util;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TenantUtils {
    private static final Logger log = LoggerFactory.getLogger(TenantUtils.class);
    private TenantUtils() {}

    public static Long getCurrentShopId() {
        Long shopId = TenantContext.getCurrentShopId();
        if (shopId == null) {
            log.error("No shopId found in TenantContext");
            throw new IllegalStateException("No shopId found in TenantContext");
        }
        return shopId;
    }
}