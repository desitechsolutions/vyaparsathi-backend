package com.desitech.vyaparsathi.sales.service;

import com.desitech.vyaparsathi.sales.dto.OfflineSalesQueueRequest;
import com.desitech.vyaparsathi.sales.dto.OfflineSalesQueueResponse;
import com.desitech.vyaparsathi.sales.entity.OfflineSalesQueue;
import com.desitech.vyaparsathi.sales.enums.OfflineSalesStatus;
import com.desitech.vyaparsathi.sales.repository.OfflineSalesQueueRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Idempotency tests for the offline sales queue.
 *
 * Verifies that:
 * 1. Submitting the same clientTxnId twice returns the existing record (no duplicate).
 * 2. A clientTxnId from a different shop cannot match an existing record.
 * 3. A blank/null clientTxnId is rejected before any persistence.
 */
@ExtendWith(MockitoExtension.class)
class OfflineSalesIdempotencyTest {

    @Mock
    private OfflineSalesQueueRepository offlineSalesQueueRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OfflineSalesQueueService offlineSalesQueueService;

    private OfflineSalesQueueRequest request;
    private static final String CLIENT_TXN_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final Long SHOP_A = 1L;
    private static final Long SHOP_B = 2L;

    @BeforeEach
    void setUp() throws Exception {
        // validateEnqueueRequest() calls writeValueAsString() on every non-null/non-blank request.
        // Use lenient() so tests that throw before reaching objectMapper don't fail on unused stub.
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        OfflineSalesQueueRequest.SaleItemDto item = new OfflineSalesQueueRequest.SaleItemDto();
        item.setVariantId(10L);
        item.setQty(2);
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setDiscount(BigDecimal.ZERO);
        item.setIsCustom(false);

        request = new OfflineSalesQueueRequest();
        request.setClientTxnId(CLIENT_TXN_ID);
        request.setTotalAmount(new BigDecimal("200.00"));
        request.setItems(List.of(item));
        request.setDeviceId("VYAP-device-001");
    }

    @Test
    @DisplayName("Duplicate clientTxnId returns existing record without creating a new one")
    void enqueue_duplicateClientTxnId_returnsExistingRecord() {
        OfflineSalesQueue existingRecord = buildExistingRecord(CLIENT_TXN_ID, SHOP_A, OfflineSalesStatus.PENDING);

        // DB unique constraint: same shop + same clientTxnId already exists
        when(offlineSalesQueueRepository.findByShopIdAndClientTxnId(SHOP_A, CLIENT_TXN_ID))
            .thenReturn(Optional.of(existingRecord));

        OfflineSalesQueueResponse response = offlineSalesQueueService.enqueueSale(SHOP_A, "user-1", request);

        // Must return existing record, not create a new one
        assertEquals(CLIENT_TXN_ID, response.getClientTxnId());
        assertEquals(OfflineSalesStatus.PENDING, response.getStatus());

        // Verify no new record was saved
        verify(offlineSalesQueueRepository, never()).save(any());
        verify(offlineSalesQueueRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Same clientTxnId from a different shop is treated as a NEW sale")
    void enqueue_sameClientTxnId_differentShop_createsNewRecord() {
        // Shop B has no record with this clientTxnId
        when(offlineSalesQueueRepository.findByShopIdAndClientTxnId(SHOP_B, CLIENT_TXN_ID))
            .thenReturn(Optional.empty());

        OfflineSalesQueue savedRecord = buildExistingRecord(CLIENT_TXN_ID, SHOP_B, OfflineSalesStatus.PENDING);
        when(offlineSalesQueueRepository.save(any())).thenReturn(savedRecord);

        // Submit for Shop B — should create a new record (not match Shop A's record)
        OfflineSalesQueueRequest shopBRequest = cloneRequest(SHOP_B);
        OfflineSalesQueueResponse response = offlineSalesQueueService.enqueueSale(SHOP_B, "user-2", shopBRequest);

        assertNotNull(response);
        verify(offlineSalesQueueRepository).findByShopIdAndClientTxnId(SHOP_B, CLIENT_TXN_ID);
        verify(offlineSalesQueueRepository).save(any());
    }

    @Test
    @DisplayName("Null clientTxnId is rejected before any persistence attempt")
    void enqueue_nullClientTxnId_throwsBeforePersistence() {
        request.setClientTxnId(null);

        assertThrows(
            IllegalArgumentException.class,
            () -> offlineSalesQueueService.enqueueSale(SHOP_A, "user-1", request)
        );

        verify(offlineSalesQueueRepository, never()).findByShopIdAndClientTxnId(any(), any());
        verify(offlineSalesQueueRepository, never()).save(any());
    }

    @Test
    @DisplayName("Blank clientTxnId is rejected before any persistence attempt")
    void enqueue_blankClientTxnId_throwsBeforePersistence() {
        request.setClientTxnId("   ");

        assertThrows(
            IllegalArgumentException.class,
            () -> offlineSalesQueueService.enqueueSale(SHOP_A, "user-1", request)
        );

        verify(offlineSalesQueueRepository, never()).save(any());
    }

    @Test
    @DisplayName("Null shopId is rejected before any persistence attempt")
    void enqueue_nullShopId_throwsBeforePersistence() {
        assertThrows(
            IllegalArgumentException.class,
            () -> offlineSalesQueueService.enqueueSale(null, "user-1", request)
        );

        verify(offlineSalesQueueRepository, never()).save(any());
    }

    @Test
    @DisplayName("Idempotency holds across status transitions: completed record still returns existing")
    void enqueue_alreadyCompletedRecord_returnsExistingWithoutReset() {
        OfflineSalesQueue completed = buildExistingRecord(CLIENT_TXN_ID, SHOP_A, OfflineSalesStatus.COMPLETED);
        completed.setSaleId(42L);
        completed.setInvoiceNumber("INV/25-26/00001");

        when(offlineSalesQueueRepository.findByShopIdAndClientTxnId(SHOP_A, CLIENT_TXN_ID))
            .thenReturn(Optional.of(completed));

        OfflineSalesQueueResponse response = offlineSalesQueueService.enqueueSale(SHOP_A, "user-1", request);

        assertEquals(OfflineSalesStatus.COMPLETED, response.getStatus());
        assertEquals("INV/25-26/00001", response.getInvoiceNumber());
        verify(offlineSalesQueueRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private OfflineSalesQueue buildExistingRecord(String clientTxnId, Long shopId, OfflineSalesStatus status) {
        OfflineSalesQueue q = new OfflineSalesQueue();
        q.setId(1L);
        q.setClientTxnId(clientTxnId);
        q.setShopId(shopId);
        q.setUserId("user-1");
        q.setStatus(status);
        q.setDeviceId("VYAP-device-001");
        q.setRequestPayloadJson("{}");
        q.setRetryCount(0);
        q.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        q.setUpdatedAt(LocalDateTime.now().minusMinutes(5));
        return q;
    }

    private OfflineSalesQueueRequest cloneRequest(Long shopId) {
        OfflineSalesQueueRequest.SaleItemDto item = new OfflineSalesQueueRequest.SaleItemDto();
        item.setVariantId(10L);
        item.setQty(2);
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setDiscount(BigDecimal.ZERO);
        item.setIsCustom(false);

        OfflineSalesQueueRequest r = new OfflineSalesQueueRequest();
        r.setClientTxnId(CLIENT_TXN_ID);
        r.setTotalAmount(new BigDecimal("200.00"));
        r.setItems(List.of(item));
        r.setDeviceId("VYAP-device-002");
        return r;
    }
}
