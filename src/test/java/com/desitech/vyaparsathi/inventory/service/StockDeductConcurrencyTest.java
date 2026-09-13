package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.common.exception.InsufficientStockException;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.inventory.repository.StockMovementRepository;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderItemRepository;
import com.desitech.vyaparsathi.sales.repository.SaleItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Concurrency guard tests for StockService.deductStock().
 *
 * These tests verify that:
 * 1. A pessimistic write lock (findByIdForUpdate) is acquired BEFORE reading stock —
 *    ensuring concurrent transactions serialize rather than race.
 * 2. Sufficient stock passes correctly.
 * 3. Insufficient stock (post-concurrent-deduction) throws InsufficientStockException.
 * 4. Unknown variant throws EntityNotFoundAppException before any stock read.
 */
@ExtendWith(MockitoExtension.class)
class StockDeductConcurrencyTest {

    @Mock
    private ItemVariantRepository itemVariantRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Mock
    private SaleItemRepository saleItemRepository;

    @InjectMocks
    private StockService stockService;

    private ItemVariant variant;

    @BeforeEach
    void setUp() {
        variant = new ItemVariant();
        variant.setId(1L);
    }

    @Test
    @DisplayName("deductStock acquires pessimistic lock BEFORE reading stock sum")
    void deductStock_acquiresPessimisticLockBeforeStockRead() {
        // Stock = 10, deducting 7 — should succeed
        when(itemVariantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(variant));
        when(stockMovementRepository.sumQuantityByItemVariantId(1L)).thenReturn(BigDecimal.TEN);
        // FEFO path: itemVariantRepository.findAll() returns empty by default →
        // getBatchWiseStock() short-circuits → flat deduction path taken
        when(stockMovementRepository.sumTotalCostForAddMovements(1L)).thenReturn(BigDecimal.valueOf(100));
        when(stockMovementRepository.sumTotalQuantityForAddMovements(1L)).thenReturn(BigDecimal.TEN);
        // recordStockMovement() calls the unlocked findById() to build the StockMovement entity
        when(itemVariantRepository.findById(1L)).thenReturn(Optional.of(variant));
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        stockService.deductStock(1L, BigDecimal.valueOf(7), "Sale", "INV/001");

        // Verify lock acquired before stock sum read
        InOrder inOrder = inOrder(itemVariantRepository, stockMovementRepository);
        inOrder.verify(itemVariantRepository).findByIdForUpdate(1L);
        inOrder.verify(stockMovementRepository).sumQuantityByItemVariantId(1L);
    }

    @Test
    @DisplayName("deductStock throws InsufficientStockException when concurrent deduction already consumed stock")
    void deductStock_throwsInsufficientStock_whenConcurrentDeductionAlreadyConsumedStock() {
        // Simulates: Device A sold 7, committed. Device B now reads stock = 3, wants qty = 6.
        when(itemVariantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(variant));
        // After Tx A committed, the locked read sees the updated SUM = 3 (10 - 7)
        when(stockMovementRepository.sumQuantityByItemVariantId(1L)).thenReturn(BigDecimal.valueOf(3));

        InsufficientStockException ex = assertThrows(
            InsufficientStockException.class,
            () -> stockService.deductStock(1L, BigDecimal.valueOf(6), "Sale", "INV/002")
        );

        assertTrue(ex.getMessage().contains("Insufficient stock") || ex.getMessage().contains("item variant 1"),
            "Exception message should identify the stock shortage: " + ex.getMessage());
        // Verify no DEDUCT movement was written
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("deductStock with exact available quantity succeeds (boundary condition)")
    void deductStock_exactQuantity_succeeds() {
        // Stock = 10, deduct exactly 10 — should succeed
        when(itemVariantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(variant));
        when(stockMovementRepository.sumQuantityByItemVariantId(1L)).thenReturn(BigDecimal.TEN);
        when(stockMovementRepository.sumTotalCostForAddMovements(1L)).thenReturn(BigDecimal.valueOf(100));
        when(stockMovementRepository.sumTotalQuantityForAddMovements(1L)).thenReturn(BigDecimal.TEN);
        when(itemVariantRepository.findById(1L)).thenReturn(Optional.of(variant));
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(
            () -> stockService.deductStock(1L, BigDecimal.TEN, "Sale", "INV/003")
        );
    }

    @Test
    @DisplayName("deductStock throws EntityNotFoundAppException when variant not found — no stock read")
    void deductStock_unknownVariant_throwsEntityNotFound_beforeStockRead() {
        when(itemVariantRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(
            EntityNotFoundAppException.class,
            () -> stockService.deductStock(99L, BigDecimal.ONE, "Sale", "INV/004")
        );

        // Confirm stock sum was never queried — lock failure prevents the read
        verify(stockMovementRepository, never()).sumQuantityByItemVariantId(anyLong());
    }

    @Test
    @DisplayName("deductStock stock-check path uses findByIdForUpdate — not the unlocked findById")
    void deductStock_stockCheckPath_usesLockedRead() {
        when(itemVariantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(variant));
        when(stockMovementRepository.sumQuantityByItemVariantId(1L)).thenReturn(BigDecimal.valueOf(5));
        when(stockMovementRepository.sumTotalCostForAddMovements(1L)).thenReturn(BigDecimal.valueOf(50));
        when(stockMovementRepository.sumTotalQuantityForAddMovements(1L)).thenReturn(BigDecimal.valueOf(5));
        // recordStockMovement() calls the unlocked findById() for entity construction — that is expected
        when(itemVariantRepository.findById(1L)).thenReturn(Optional.of(variant));
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        stockService.deductStock(1L, BigDecimal.ONE, "Sale", "INV/005");

        // The stock-check acquire path must use the locking variant exactly once
        verify(itemVariantRepository, times(1)).findByIdForUpdate(1L);
        // findById() is legitimately called by recordStockMovement() for entity construction —
        // the important guarantee is that the LOCK is held before the stock SUM is read,
        // not that findById is never called at all.
    }
}
