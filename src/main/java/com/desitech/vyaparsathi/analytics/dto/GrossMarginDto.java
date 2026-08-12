package com.desitech.vyaparsathi.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Gross margin over a range — revenue minus cost of goods sold, where cost is the
 * most recent {@code PurchaseInvoiceItem.unitCost} per variant (falling back to
 * {@code variant.pricePerUnit * 0.7} when no purchase history exists).
 *
 * Sale lines without a linked variant (custom/service billing) contribute their
 * full revenue to {@code totalRevenue} but zero to {@code totalCost}. That's the
 * least-wrong option — we don't have COGS for a service line.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GrossMarginDto {
    private LocalDate from;
    private LocalDate to;
    private BigDecimal totalRevenue;
    private BigDecimal totalCost;
    private BigDecimal grossMargin;
    private double grossMarginPct;
    private long saleCount;
}
