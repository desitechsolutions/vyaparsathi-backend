package com.desitech.vyaparsathi.reports.service;

import com.desitech.vyaparsathi.inventory.enums.StockMovementType;
import com.desitech.vyaparsathi.inventory.entity.StockMovement;
import com.desitech.vyaparsathi.inventory.repository.StockMovementRepository;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to calculate Cost of Goods Sold (COGS).
 * Uses a weighted average cost method based on ADD and positive ADJUSTMENT movements.
 */
@Service
public class COGSCalculationService {

    private static final Logger logger = LoggerFactory.getLogger(COGSCalculationService.class);

    @Autowired
    private StockMovementRepository stockMovementRepository;

    // Cache to optimize performance during report generation
    private final Map<Long, BigDecimal> costCache = new ConcurrentHashMap<>();

    /**
     * Calculate COGS for a list of sales. Clears the cache before starting.
     *
     * @param sales List of sales in the date range
     * @return Total COGS for the period
     */
    public BigDecimal calculateCOGS(List<Sale> sales) {
        costCache.clear();
        if (sales == null || sales.isEmpty()) return BigDecimal.ZERO;

        return sales.stream()
                .filter(sale -> sale.getSaleItems() != null)
                .flatMap(sale -> sale.getSaleItems().stream())
                .map(this::calculateItemCOGS)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                // Final rounding to 2 decimal places for the report total
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate COGS for a specific sale item using the weighted average cost.
     *
     * @param saleItem The sale item to calculate COGS for
     * @return COGS for this item (Quantity Sold * Weighted Average Cost)
     */
    private BigDecimal calculateItemCOGS(SaleItem saleItem) {
        if (saleItem.getItemVariant() == null || saleItem.getQty() == null) {
            return BigDecimal.ZERO;
        }

        Long itemVariantId = saleItem.getItemVariant().getId();
        BigDecimal quantitySold = saleItem.getQty();

        // Calculate average cost with higher precision (4 decimals) for accuracy
        BigDecimal averageCost = costCache.computeIfAbsent(itemVariantId, this::calculateWeightedAverageCost);

        return quantitySold.multiply(averageCost);
    }

    /**
     * Core Logic: WAC = (Sum of all Purchase Costs) / (Sum of all Purchased Quantities)
     */
    private BigDecimal calculateWeightedAverageCost(Long itemVariantId) {
        // We include ADD (purchases) and we should also check ADJUSTMENTS that added stock
        List<StockMovement> movements = stockMovementRepository.findByItemVariantIdAndMovementTypeIn(
                itemVariantId,
                Arrays.asList(StockMovementType.ADD, StockMovementType.ADJUST)
        );

        if (movements.isEmpty()) {
            logger.warn("No stock movements found for itemVariantId: {}. COGS will be ZERO.", itemVariantId);
            return BigDecimal.ZERO;
        }

        BigDecimal totalCostAmount = BigDecimal.ZERO;
        BigDecimal totalQuantityCount = BigDecimal.ZERO;

        for (StockMovement movement : movements) {
            BigDecimal qty = movement.getQuantity();
            BigDecimal cost = movement.getCostPerUnit();

            // Only factor in movements that added value/stock to the warehouse
            if (qty != null && cost != null && qty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal entryValue = cost.multiply(qty);
                totalCostAmount = totalCostAmount.add(entryValue);
                totalQuantityCount = totalQuantityCount.add(qty);
            }
        }

        if (totalQuantityCount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Use scale of 4 for intermediate WAC to prevent precision loss (e.g., 188.2642)
        return totalCostAmount.divide(totalQuantityCount, 4, RoundingMode.HALF_UP);
    }

    /**
     * A helper method for logging, which delegates to the main calculateCOGS method.
     */
    public BigDecimal calculateCOGSForPeriod(List<Sale> sales, LocalDate fromDate, LocalDate toDate) {
        logger.info("Generating COGS Report: {} to {}", fromDate, toDate);
        return calculateCOGS(sales);
    }
}