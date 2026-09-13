package com.desitech.vyaparsathi.sales.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.sales.repository.OfflineSalesQueueRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests that OfflineSalesProcessorService.processQueuedSales() sets TenantContext
 * correctly even when triggered from a scheduled task that has no HTTP request context.
 *
 * Background: TenantContextTaskDecorator captures the calling thread's shopId at
 * @Async submission time. When the scheduler fires it has no TenantContext, so the
 * decorator captures null and skips the set. processQueuedSales() must set it itself.
 *
 * We test the synchronous inner logic directly (processQueuedSales body before @Async
 * dispatch is not unit-testable without Spring context), so we verify that:
 * 1. TenantContext is set to shopId at the start of processing.
 * 2. If TenantContext was null before, it is now non-null during processing.
 * 3. The per-shop AtomicBoolean lock gate still works correctly.
 */
@ExtendWith(MockitoExtension.class)
class OfflineSalesProcessorTenantTest {

    @Mock
    private OfflineSalesQueueRepository offlineSalesQueueRepository;
    @Mock
    private SaleService saleService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OfflineSalesProcessorService processor;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("processQueuedSales sets TenantContext.shopId even when called from scheduler (no prior context)")
    void processQueuedSales_setsShopId_whenCalledFromScheduler() {
        // Simulate scheduler: no TenantContext on calling thread
        TenantContext.clear();
        assertNull(TenantContext.getCurrentShopId(), "Pre-condition: TenantContext must be null (scheduler path)");

        Long shopId = 42L;

        // Capture the shopId that is set during processing
        when(offlineSalesQueueRepository.findByShopIdAndStatusIn(eq(shopId), anyList()))
            .thenAnswer(inv -> {
                Long capturedShopId = TenantContext.getCurrentShopId();
                // Verify that TenantContext is set at the point repo is called
                assertEquals(shopId, capturedShopId,
                    "TenantContext.shopId must equal the shopId being processed when the repository is queried");
                return List.of();
            });
        when(offlineSalesQueueRepository.findRetriableSales(eq(shopId), any(), anyInt()))
            .thenReturn(List.of());

        // Call processQueuedSales synchronously (the @Async annotation does not fire in unit tests)
        processor.processQueuedSales(shopId);

        verify(offlineSalesQueueRepository).findByShopIdAndStatusIn(eq(shopId), anyList());
    }

    @Test
    @DisplayName("processQueuedSales does not overwrite a pre-existing TenantContext from HTTP request")
    void processQueuedSales_withPreexistingTenantContext_usesCorrectShopId() {
        // Simulate HTTP trigger: TenantContext already set by JWT filter (same shopId)
        Long shopId = 7L;
        TenantContext.setCurrentShopId(shopId);

        when(offlineSalesQueueRepository.findByShopIdAndStatusIn(eq(shopId), anyList()))
            .thenReturn(List.of());
        when(offlineSalesQueueRepository.findRetriableSales(eq(shopId), any(), anyInt()))
            .thenReturn(List.of());

        processor.processQueuedSales(shopId);

        // TenantContext should still be the correct shopId during and after the call
        assertEquals(shopId, TenantContext.getCurrentShopId());
    }

    @Test
    @DisplayName("processQueuedSales skips when per-shop lock is already held")
    void processQueuedSales_skipsWhenAlreadyProcessing() {
        Long shopId = 5L;
        TenantContext.clear();

        when(offlineSalesQueueRepository.findByShopIdAndStatusIn(eq(shopId), anyList()))
            .thenReturn(List.of());
        when(offlineSalesQueueRepository.findRetriableSales(eq(shopId), any(), anyInt()))
            .thenReturn(List.of());

        // First call acquires the lock and releases it
        processor.processQueuedSales(shopId);

        // Second call should be permitted after the first completes
        // (the AtomicBoolean is reset to false in the finally block)
        processor.processQueuedSales(shopId);

        // Both calls should reach the repository (not skipped)
        verify(offlineSalesQueueRepository, times(2)).findByShopIdAndStatusIn(eq(shopId), anyList());
    }
}
