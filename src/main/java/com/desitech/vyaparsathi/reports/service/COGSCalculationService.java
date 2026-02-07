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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to calculate Cost of Goods Sold (COGS).
 * Uses a weighted average cost method based on 'ADD' movements from the StockMovement table.
 */
@Service
public class COGSCalculationService {

    private static final Logger logger = LoggerFactory.getLogger(COGSCalculationService.class);

    // CHANGED: Use StockMovementRepository, our new single source of truth.
    @Autowired
    private StockMovementRepository stockMovementRepository;

    // A simple cache to avoid recalculating the average cost for the same item within a single report.
    private final Map<Long, BigDecimal> costCache = new ConcurrentHashMap<>();

    /**
     * Calculate COGS for a list of sales. Clears the cache before starting.
     *
     * @param sales List of sales in the date range
     * @return Total COGS for the period
     */
    public BigDecimal calculateCOGS(List<Sale> sales) {
        costCache.clear(); // Clear cache for each new report run.
        return sales.stream()
                .flatMap(sale -> sale.getSaleItems().stream())
                .map(this::calculateItemCOGS)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

        // Use the cache to get the average cost. If not present, calculate it.
        BigDecimal averageCost = costCache.computeIfAbsent(itemVariantId, this::calculateWeightedAverageCost);

        return quantitySold.multiply(averageCost);
    }

    /**
     * Calculate the weighted average cost for an item variant from all its 'ADD' stock movements.
     * This is the core of our new, correct COGS logic.
     *
     * @param itemVariantId The item variant ID
     * @return Weighted average cost per unit
     */
    private BigDecimal calculateWeightedAverageCost(Long itemVariantId) {
        // 1. Fetch all historical purchase ('ADD') movements for this item.
        List<StockMovement> addMovements = stockMovementRepository.findByItemVariantIdAndMovementType(itemVariantId, StockMovementType.ADD);

        if (addMovements.isEmpty()) {
            logger.warn("No 'ADD' stock movements found for itemVariantId: {}. Cannot calculate COGS. Returning ZERO.", itemVariantId);
            return BigDecimal.ZERO;
        }

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalQuantity = BigDecimal.ZERO;

        // 2. Sum the total cost and total quantity from all historical purchases.
        for (StockMovement movement : addMovements) {
            if (movement.getCostPerUnit() != null && movement.getQuantity() != null && movement.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal entryTotalCost = movement.getCostPerUnit().multiply(movement.getQuantity());
                totalCost = totalCost.add(entryTotalCost);
                totalQuantity = totalQuantity.add(movement.getQuantity());
            }
        }

        if (totalQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // 3. Return weighted average cost = Total Cost of Purchases / Total Quantity Purchased
        return totalCost.divide(totalQuantity, 2, RoundingMode.HALF_UP);
    }

    /**
     * A helper method for logging, which delegates to the main calculateCOGS method.
     */
    public BigDecimal calculateCOGSForPeriod(List<Sale> sales, LocalDate fromDate, LocalDate toDate) {
        logger.info("Calculating COGS for period: {} to {}", fromDate, toDate);
        return calculateCOGS(sales);
    }
}