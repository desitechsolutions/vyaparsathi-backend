package com.desitech.vyaparsathi.sales.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Slim access layer for {@link SaleItem}. Most sale-item persistence goes
 * through the parent {@code Sale} via cascade, but the analytics/reorder
 * modules need read-only projection queries against sold lines directly.
 */
@Repository
public interface SaleItemRepository extends BaseRepository<SaleItem, Long> {

    /**
     * Revenue per variant since {@code since}, used by the ABC classifier
     * on the low-stock alerts page. Uses {@code taxable_value} because it
     * already reflects the discounted line total before GST — the closest
     * proxy to "money contributed" that this schema carries.
     *
     * <p>Custom / one-off line items (with a null itemVariant) are excluded
     * — they cannot be bucketed against the catalog. Fully-returned lines
     * are also excluded so a return-heavy variant does not float to the A
     * bucket on paper revenue that was later reversed.
     */
    interface VariantRevenueProjection {
        Long getVariantId();
        BigDecimal getRevenue();
    }

    @Query("SELECT si.itemVariant.id AS variantId, " +
            "COALESCE(SUM(si.taxableValue), 0) AS revenue " +
            "FROM SaleItem si " +
            "WHERE si.itemVariant IS NOT NULL " +
            "AND si.isReturned = false " +
            "AND si.sale.date >= :since " +
            "GROUP BY si.itemVariant.id")
    List<VariantRevenueProjection> findRevenueByVariantSince(@Param("since") LocalDateTime since);
}
