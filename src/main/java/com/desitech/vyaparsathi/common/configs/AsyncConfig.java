package com.desitech.vyaparsathi.common.configs;

import com.desitech.vyaparsathi.auth.security.ImpersonationContext;
import com.desitech.vyaparsathi.auth.security.ImpersonationContext.ImpersonationDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("NotifThread-");
        executor.setTaskDecorator(new TenantContextTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Bean(name = "offlineProcessorExecutor")
    public Executor offlineProcessorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("OfflineProc-");
        executor.setTaskDecorator(new TenantContextTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * Propagates request-scoped context onto pooled async threads and clears it
     * on completion so a pooled thread never carries state across tasks:
     *   - TenantContext (shop id) — otherwise a stale value from a prior task
     *     could leak into a task submitted by another tenant.
     *   - ImpersonationContext (super-admin impersonation session) — backed by a
     *     plain (non-inheritable) ThreadLocal, so it is completely lost on
     *     async threads without this snapshot/restore.
     *   - SecurityContext — required so that Spring Data JPA's AuditingEntityListener
     *     can resolve the current username via AuditorAware on async threads.
     */
    private static class TenantContextTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            final Long capturedShopId = TenantContext.getCurrentShopId();
            final ImpersonationDetails capturedImpersonation = ImpersonationContext.get();
            final SecurityContext capturedSecurityContext = SecurityContextHolder.getContext();

            return () -> {
                try {
                    SecurityContextHolder.setContext(capturedSecurityContext);
                    if (capturedShopId != null) {
                        TenantContext.setCurrentShopId(capturedShopId);
                    }
                    if (capturedImpersonation != null) {
                        ImpersonationContext.set(
                                capturedImpersonation.getActorAdminId(),
                                capturedImpersonation.getImpersonationSessionId(),
                                capturedImpersonation.getTargetShopId());
                    }
                    runnable.run();
                } finally {
                    SecurityContextHolder.clearContext();
                    TenantContext.clear();
                    ImpersonationContext.clear();
                }
            };
        }
    }
}
