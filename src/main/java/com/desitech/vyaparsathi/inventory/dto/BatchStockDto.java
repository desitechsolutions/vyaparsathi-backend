package com.desitech.vyaparsathi.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents the available stock for a specific batch (lot) of an item variant.
 * Pharmacy shops may receive the same medicine in multiple batches with different
 * expiry dates; this DTO exposes the per-batch breakdown so the UI can display
 * "Batch A – 20 strips (exp Jun-2025)" and "Batch B – 30 strips (exp Dec-2025)"
 * rather than a single aggregated line with the last-seen expiry date.
 */
@Data
public class BatchStockDto {
    private Long itemVariantId;
    private String itemName;
    private String sku;
    private String unit;

    /** Batch/lot identifier from the stock movement (may be null for non-pharmacy entries). */
    private String batchNumber;

    /** Expiry date recorded on the stock movement for this batch. */
    private LocalDate expiryDate;

    /** Net remaining quantity for this batch (ADD movements minus DEDUCT/ADJUST). */
    private BigDecimal quantity;

    /** Weighted-average purchase cost for this batch. */
    private BigDecimal costPerUnit;

    /** Maximum Retail Price from the item variant. */
    private BigDecimal mrp;

    /** Whether this medicine can be dispensed in sub-units. */
    private Boolean isLooseMedicine;

    /** Number of dispensing units per stock unit (e.g. 15 tablets per strip). */
    private BigDecimal packSize;
}
