package com.desitech.vyaparsathi.analytics.config;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Caffeine-backed cache for hot analytics reads.
 *
 * KPI, revenue time-series, payment mix and gross margin are pure functions of
 * (shopId, range) — a 5-minute TTL is plenty for a dashboard tick and prevents
 * the same query being re-issued on every page-load / poll.
 *
 * Cache keys are prefixed with the current shopId (from {@link TenantContext})
 * so tenants never see each other's data, and the same range for two different
 * shops occupies two entries.
 */
@Configuration
@EnableCaching
public class AnalyticsCacheConfig {

    public static final String CACHE_KPI_SUMMARY        = "analytics.kpiSummary";
    public static final String CACHE_REVENUE_TIMESERIES = "analytics.revenueTimeSeries";
    public static final String CACHE_PAYMENT_MIX        = "analytics.paymentMix";
    public static final String CACHE_GROSS_MARGIN       = "analytics.grossMargin";
    public static final String CACHE_SEASONAL_TRENDS    = "analytics.seasonalTrends";
    public static final String CACHE_TOP_ITEMS          = "analytics.topItems";
    public static final String CACHE_CHURN              = "analytics.churn";

    // Report-service caches (5-min TTL, shop-scoped — same manager)
    public static final String CACHE_SALES_SUMMARY      = "reports.salesSummary";
    public static final String CACHE_ITEMS_SOLD         = "reports.itemsSold";
    public static final String CACHE_CATEGORY_SALES     = "reports.categorySales";
    public static final String CACHE_SALES_TIMESERIES   = "reports.salesTimeSeries";

    public static final String KEY_GEN_SHOP_SCOPED = "shopScopedAnalyticsKeyGen";

    @Bean
    public CacheManager analyticsCacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager();
        mgr.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(10_000));
        mgr.setCacheNames(List.of(
                CACHE_KPI_SUMMARY,
                CACHE_REVENUE_TIMESERIES,
                CACHE_PAYMENT_MIX,
                CACHE_GROSS_MARGIN,
                CACHE_SEASONAL_TRENDS,
                CACHE_TOP_ITEMS,
                CACHE_CHURN,
                CACHE_SALES_SUMMARY,
                CACHE_ITEMS_SOLD,
                CACHE_CATEGORY_SALES,
                CACHE_SALES_TIMESERIES,
                "userDetails"
        ));
        return mgr;
    }

    /**
     * Composes the cache key as {@code shopId : arg0 : arg1 : ...}. Every analytics
     * method that reads {@link TenantContext#getCurrentShopId()} must use this
     * generator — otherwise two shops with an overlapping range would collide.
     */
    @Bean(KEY_GEN_SHOP_SCOPED)
    public KeyGenerator shopScopedAnalyticsKeyGen() {
        return (target, method, params) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(TenantContext.getCurrentShopId());
            for (Object p : params) {
                sb.append(':').append(p);
            }
            return sb.toString();
        };
    }
}
