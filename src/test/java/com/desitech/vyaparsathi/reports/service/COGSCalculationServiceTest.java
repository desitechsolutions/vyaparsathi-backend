package com.desitech.vyaparsathi.reports.service;

import com.desitech.vyaparsathi.inventory.StockMovementType;
import com.desitech.vyaparsathi.inventory.entity.Item;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.entity.StockMovement;
import com.desitech.vyaparsathi.inventory.repository.StockMovementRepository;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class COGSCalculationServiceTest {

    // CHANGED: Mock the correct repository
    @Mock
    private StockMovementRepository stockMovementRepository;

    @InjectMocks
    private COGSCalculationService cogsCalculationService;

    private ItemVariant itemVariant;
    private Sale sale;
    private SaleItem saleItem;

    @BeforeEach
    void setUp() {
        Item item = new Item();
        item.setId(1L);
        item.setName("Test Item");

        itemVariant = new ItemVariant();
        itemVariant.setId(1L);
        itemVariant.setSku("TEST-001");
        itemVariant.setItem(item);

        sale = new Sale();
        sale.setId(1L);
        sale.setDate(LocalDateTime.now());

        saleItem = new SaleItem();
        saleItem.setId(1L);
        saleItem.setItemVariant(itemVariant);
        saleItem.setQty(new BigDecimal("5.0"));
        saleItem.setUnitPrice(new BigDecimal("100.00"));
        saleItem.setSale(sale);

        sale.setSaleItems(Collections.singletonList(saleItem));
    }

    @Test
    @DisplayName("Should calculate COGS correctly based on weighted average cost from stock movements")
    void shouldCalculateCOGSWithWeightedAverageCost() {
        // Setup stock movements with different costs
        StockMovement movement1 = createAddStockMovement(new BigDecimal("60.00"), new BigDecimal("10"));
        StockMovement movement2 = createAddStockMovement(new BigDecimal("80.00"), new BigDecimal("20"));

        when(stockMovementRepository.findByItemVariantIdAndMovementType(1L, StockMovementType.ADD))
                .thenReturn(Arrays.asList(movement1, movement2));

        // Expected Average Cost: (60*10 + 80*20) / (10+20) = (600 + 1600) / 30 = 73.33
        // Expected COGS: 5 (quantity sold) * 73.33 = 366.65
        BigDecimal expectedCOGS = new BigDecimal("366.65");

        BigDecimal totalCOGS = cogsCalculationService.calculateCOGS(Collections.singletonList(sale));

        assertEquals(0, expectedCOGS.compareTo(totalCOGS));
    }

    @Test
    @DisplayName("Should return zero COGS when no purchase data (stock movements) is available")
    void shouldReturnZeroCOGSWhenNoPurchaseData() {
        when(stockMovementRepository.findByItemVariantIdAndMovementType(anyLong(), eq(StockMovementType.ADD)))
                .thenReturn(Collections.emptyList());

        BigDecimal totalCOGS = cogsCalculationService.calculateCOGS(Collections.singletonList(sale));
        assertEquals(0, totalCOGS.compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Should calculate item COGS correctly with a single stock movement")
    void shouldCalculateItemCOGSCorrectly() {
        StockMovement movement = createAddStockMovement(new BigDecimal("70.00"), new BigDecimal("50"));

        when(stockMovementRepository.findByItemVariantIdAndMovementType(1L, StockMovementType.ADD))
                .thenReturn(Collections.singletonList(movement));

        // Expected: quantity sold (5) * average cost (70) = 350
        BigDecimal expectedCOGS = new BigDecimal("350.00");
        BigDecimal totalCOGS = cogsCalculationService.calculateCOGS(Collections.singletonList(sale));

        assertEquals(0, expectedCOGS.compareTo(totalCOGS));
    }

    @Test
    @DisplayName("Should calculate total COGS for multiple sale items")
    void shouldCalculateTotalCOGSForMultipleSaleItems() {
        // Setup another sale item for the same variant
        SaleItem saleItem2 = new SaleItem();
        saleItem2.setId(2L);
        saleItem2.setItemVariant(itemVariant);
        saleItem2.setQty(new BigDecimal("3.0"));
        saleItem2.setSale(sale);

        // Add both items to the sale
        sale.setSaleItems(Arrays.asList(saleItem, saleItem2));

        StockMovement movement = createAddStockMovement(new BigDecimal("50.00"), new BigDecimal("100"));

        when(stockMovementRepository.findByItemVariantIdAndMovementType(1L, StockMovementType.ADD))
                .thenReturn(Collections.singletonList(movement));

        BigDecimal totalCOGS = cogsCalculationService.calculateCOGS(Collections.singletonList(sale));

        // Expected: (5 * 50) + (3 * 50) = 250 + 150 = 400
        assertEquals(0, new BigDecimal("400.00").compareTo(totalCOGS));
    }

    @Test
    @DisplayName("Should handle zero quantity in stock movements gracefully")
    void shouldHandleZeroQuantityInStockMovements() {
        StockMovement movement1 = createAddStockMovement(new BigDecimal("50.00"), new BigDecimal("0"));
        StockMovement movement2 = createAddStockMovement(new BigDecimal("60.00"), new BigDecimal("10"));

        when(stockMovementRepository.findByItemVariantIdAndMovementType(1L, StockMovementType.ADD))
                .thenReturn(Arrays.asList(movement1, movement2));

        // Expected average cost is 60, as the zero-quantity movement should be ignored.
        // Expected COGS = 5 * 60 = 300
        BigDecimal totalCOGS = cogsCalculationService.calculateCOGS(Collections.singletonList(sale));
        assertEquals(0, new BigDecimal("300.00").compareTo(totalCOGS));
    }

    // CHANGED: Helper method to create 'ADD' StockMovement objects
    private StockMovement createAddStockMovement(BigDecimal unitCost, BigDecimal quantity) {
        StockMovement movement = new StockMovement();
        movement.setItemVariant(itemVariant);
        movement.setCostPerUnit(unitCost);
        movement.setQuantity(quantity);
        movement.setMovementType(StockMovementType.ADD);
        movement.setTimestamp(LocalDateTime.now());
        return movement;
    }
}