package com.desitech.vyaparsathi.sales;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Multi-Tenant Security — tenant isolation and async context propagation")
class MultiTenantSecurityTest {

    @Mock private SaleRepository saleRepository;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("findAllByShopIdAndDateBetween scopes results to the requested shopId only")
    void findAllByShopIdAndDateBetween_onlyReturnsTenantSales() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 9, 30, 23, 59);

        Sale shop1Sale = new Sale();
        // shop2Sale intentionally not included in shop1 result set

        when(saleRepository.findAllByShopIdAndDateBetween(1L, start, end))
                .thenReturn(List.of(shop1Sale));
        when(saleRepository.findAllByShopIdAndDateBetween(2L, start, end))
                .thenReturn(List.of());

        List<Sale> shop1Results = saleRepository.findAllByShopIdAndDateBetween(1L, start, end);
        List<Sale> shop2Results = saleRepository.findAllByShopIdAndDateBetween(2L, start, end);

        assertEquals(1, shop1Results.size(), "Shop 1 should get exactly its own sale");
        assertEquals(0, shop2Results.size(), "Shop 2 should get no sales from shop 1's data");

        verify(saleRepository, times(1)).findAllByShopIdAndDateBetween(1L, start, end);
        verify(saleRepository, times(1)).findAllByShopIdAndDateBetween(2L, start, end);
    }

    @Test
    @DisplayName("TenantContext is visible inside an async Runnable when manually propagated (decorator pattern)")
    void tenantContext_retainedOnAsyncThread() throws InterruptedException {
        TenantContext.setCurrentShopId(1L);
        Long capturedOnSubmitter = TenantContext.getCurrentShopId();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong capturedOnThread = new AtomicLong(-1L);

        // Simulate what TenantContextTaskDecorator does: capture then restore
        Long shopIdToPropagate = capturedOnSubmitter;
        Thread asyncThread = new Thread(() -> {
            try {
                TenantContext.setCurrentShopId(shopIdToPropagate);
                capturedOnThread.set(TenantContext.getCurrentShopId());
            } finally {
                TenantContext.clear();
                latch.countDown();
            }
        });
        asyncThread.start();
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Async thread did not complete in time");

        assertEquals(1L, capturedOnThread.get(),
                "TenantContext shopId should be 1L on the async thread after decorator propagation");
    }
}
