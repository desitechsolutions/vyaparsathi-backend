package com.desitech.vyaparsathi.auth.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class ImpersonationContext {

    private static final ThreadLocal<ImpersonationDetails> CURRENT_IMPERSONATION = new ThreadLocal<>();

    public static void set(Long actorAdminId, String impersonationSessionId, Long targetShopId) {
        CURRENT_IMPERSONATION.set(new ImpersonationDetails(actorAdminId, impersonationSessionId, targetShopId));
    }

    public static ImpersonationDetails get() {
        return CURRENT_IMPERSONATION.get();
    }

    public static boolean isImpersonating() {
        return CURRENT_IMPERSONATION.get() != null;
    }

    public static void clear() {
        CURRENT_IMPERSONATION.remove();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ImpersonationDetails {
        private Long actorAdminId;
        private String impersonationSessionId;
        private Long targetShopId;
    }
}
