package com.desitech.vyaparsathi.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payload for {@code POST /api/item-variants/bulk-patch}: apply the same
 * partial update to every variant in {@link #ids}.
 *
 * <p>Every rule field is nullable — only the ones the caller explicitly
 * sets are applied. This lets the FE "Bulk edit" dialog send just the one
 * field the user chose (e.g. only preferredSupplierId) without wiping
 * unrelated fields on the target variants.
 */
@Data
public class BulkVariantPatchDto {

    @NotNull
    @NotEmpty(message = "ids must not be empty")
    private List<Long> ids;

    @DecimalMin(value = "0.00", message = "Low stock threshold cannot be negative")
    private BigDecimal lowStockThreshold;

    @DecimalMin(value = "0.00", message = "Reorder point cannot be negative")
    private BigDecimal reorderPoint;

    @DecimalMin(value = "0.00", message = "Reorder qty cannot be negative")
    private BigDecimal reorderQty;

    @DecimalMin(value = "0.00", message = "Safety stock cannot be negative")
    private BigDecimal safetyStock;

    @DecimalMin(value = "0.00", message = "Max stock cannot be negative")
    private BigDecimal maxStock;

    @Min(value = 0, message = "Lead time cannot be negative")
    private Integer leadTimeDays;

    private Long preferredSupplierId;
}
