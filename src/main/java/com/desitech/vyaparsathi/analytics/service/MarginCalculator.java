package com.desitech.vyaparsathi.analytics.service;

import com.desitech.vyaparsathi.analytics.dto.GrossMarginDto;
import com.desitech.vyaparsathi.analytics.model.AnalyticsRange;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.purchases.repository.PurchaseInvoiceItemRepository;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes gross margin without falling into N+1 territory: pulls every variant's
 * cost history in one query, reduces to "latest cost per variant" in Java, then
 * walks the sale lines.
 *
 * Fallback for a variant with no purchase history: {@code pricePerUnit * 0.7} —
 * matches the estimate already used by procurement suggestions so the two views
 * agree.
 */
@Component
public class MarginCalculator {

    /** Fraction of retail price used as a stand-in cost when no purchase history exists. */
    private static final BigDecimal FALLBACK_COST_RATIO = BigDecimal.valueOf(0.7);

    @Autowired
    private PurchaseInvoiceItemRepository purchaseInvoiceItemRepository;

    /**
     * Return the latest known unit cost per variant. Variants absent from the map
     * had no purchase history at query time — callers should fall back with
     * {@link #costFor(ItemVariant, Map)}.
     */
    public Map<Long, BigDecimal> latestCostByVariant(Set<Long> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) return Map.of();
        Map<Long, BigDecimal> out = new HashMap<>();
        for (Object[] r : purchaseInvoiceItemRepository.findLatestCostsByVariantIds(variantIds)) {
            Long id = (Long) r[0];
            BigDecimal cost = (BigDecimal) r[1];
            // Rows are ordered by purchase date desc, so first hit per variant is the latest.
            out.putIfAbsent(id, cost);
        }
        return out;
    }

    public BigDecimal costFor(ItemVariant v, Map<Long, BigDecimal> latestCosts) {
        if (v == null) return BigDecimal.ZERO;
        BigDecimal cost = latestCosts.get(v.getId());
        if (cost != null) return cost;
        return v.getPricePerUnit() != null
                ? v.getPricePerUnit().multiply(FALLBACK_COST_RATIO)
                : BigDecimal.ZERO;
    }

    public GrossMarginDto compute(AnalyticsRange range, List<Sale> sales) {
        Set<Long> variantIds = new HashSet<>();
        for (Sale s : sales) {
            if (s.getSaleItems() == null) continue;
            for (SaleItem si : s.getSaleItems()) {
                if (si.getItemVariant() != null && si.getItemVariant().getId() != null) {
                    variantIds.add(si.getItemVariant().getId());
                }
            }
        }
        Map<Long, BigDecimal> latestCosts = latestCostByVariant(variantIds);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        long saleCount = 0;
        for (Sale s : sales) {
            saleCount++;
            if (s.getSaleItems() == null) continue;
            for (SaleItem si : s.getSaleItems()) {
                BigDecimal revLine = si.getTaxableValue() != null ? si.getTaxableValue() : BigDecimal.ZERO;
                totalRevenue = totalRevenue.add(revLine);
                if (si.getItemVariant() != null) {
                    BigDecimal qty = si.getQty() != null ? si.getQty() : BigDecimal.ZERO;
                    BigDecimal unitCost = costFor(si.getItemVariant(), latestCosts);
                    totalCost = totalCost.add(unitCost.multiply(qty));
                }
            }
        }
        BigDecimal margin = totalRevenue.subtract(totalCost);
        double marginPct = totalRevenue.signum() == 0
                ? 0.0
                : margin.multiply(BigDecimal.valueOf(100))
                        .divide(totalRevenue, 2, RoundingMode.HALF_UP)
                        .doubleValue();
        return new GrossMarginDto(
                range.getFrom(), range.getTo(),
                totalRevenue, totalCost, margin, marginPct, saleCount);
    }
}
