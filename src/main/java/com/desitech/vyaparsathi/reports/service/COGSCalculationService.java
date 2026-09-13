package com.desitech.vyaparsathi.reports.service;

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
import java.util.stream.Collectors;

/**
 * Service to calculate Cost of Goods Sold (COGS).
 * Uses a weighted average cost method based on ADD and positive ADJUSTMENT movements.
 */
@Service
public class COGSCalculationService {

    private static final Logger logger = LoggerFactory.getLogger(COGSCalculationService.class);

    @Autowired
    private StockMovementRepository stockMovementRepository;

    /**
     * Calculate COGS for a list of sales using a single bulk WAC query.
     *
     * <p>Instead of fetching stock movements per variant (N+1), we collect all
     * distinct variant IDs up front, call {@code findWacByVariantIds} once, and
     * look up the result in an in-memory map during the stream.
     *
     * @param sales List of sales in the date range
     * @return Total COGS for the period
     */
    public BigDecimal calculateCOGS(List<Sale> sales) {
        if (sales == null || sales.isEmpty()) return BigDecimal.ZERO;

        // ── 1. Collect all distinct variant IDs from every sale line ─────────
        List<Long> variantIds = sales.stream()
                .filter(s -> s.getSaleItems() != null)
                .flatMap(s -> s.getSaleItems().stream())
                .filter(si -> si.getItemVariant() != null)
                .map(si -> si.getItemVariant().getId())
                .distinct()
                .collect(Collectors.toList());

        if (variantIds.isEmpty()) return BigDecimal.ZERO;

        // ── 2. Single bulk query: WAC for all variants at once ────────────────
        Map<Long, BigDecimal> wacByVariant = stockMovementRepository
                .findWacByVariantIds(variantIds)
                .stream()
                .collect(Collectors.toMap(
                        StockMovementRepository.WacProjection::getVariantId,
                        StockMovementRepository.WacProjection::getWac
                ));

        // ── 3. Stream over sale lines and multiply qty × WAC ─────────────────
        BigDecimal total = BigDecimal.ZERO;
        for (Sale sale : sales) {
            if (sale.getSaleItems() == null) continue;
            for (SaleItem si : sale.getSaleItems()) {
                if (si.getItemVariant() == null || si.getQty() == null) continue;
                Long variantId = si.getItemVariant().getId();
                BigDecimal wac = wacByVariant.getOrDefault(variantId, BigDecimal.ZERO);
                if (wac.compareTo(BigDecimal.ZERO) == 0) {
                    logger.warn("No WAC found for itemVariantId: {}. Skipping COGS contribution.", variantId);
                }
                total = total.add(si.getQty().multiply(wac));
            }
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * A helper method for logging, which delegates to the main calculateCOGS method.
     */
    public BigDecimal calculateCOGSForPeriod(List<Sale> sales, LocalDate fromDate, LocalDate toDate) {
        logger.info("Generating COGS Report: {} to {}", fromDate, toDate);
        return calculateCOGS(sales);
    }
}