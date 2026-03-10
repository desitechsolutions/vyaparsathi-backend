package com.desitech.vyaparsathi.sales.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SaleItemDto {
    private Long itemId;
    @JsonProperty("id")
    @NotNull(message = "Item Variant ID cannot be null")
    private Long itemVariantId;
    @NotBlank(message = "Item name cannot be blank")
    private String itemName;
    @NotNull(message = "Quantity cannot be null")
    @Min(value = 0, message = "Quantity must be a positive number")
    private BigDecimal qty;
    @NotNull(message = "Unit price cannot be null")
    private BigDecimal unitPrice;
    private BigDecimal costPerUnit;
    private BigDecimal discount = BigDecimal.ZERO;
    private int gstRate;
    private BigDecimal taxableValue;
    private BigDecimal returnedQty;
    private BigDecimal netQty;

    /**
     * True when the frontend is selling this item in loose/tablet mode
     * (e.g., dispensing individual tablets from a strip).
     * When true, {@link #loosePackSize} must also be provided.
     */
    private Boolean isLooseSale;

    /**
     * The number of dispensing units per stock unit at the time of this sale
     * (e.g., 15 tablets per strip).  Sent by the frontend when {@link #isLooseSale}
     * is true.  Takes precedence over the ItemVariant's stored packSize so that a
     * pharmacist can sell loose even if the variant has not been pre-configured.
     */
    private BigDecimal loosePackSize;

    /**
     * Batch/lot number of the medicine dispensed (pharmacy compliance).
     * Captured at point-of-sale for batch traceability and drug-recall tracking.
     */
    private String batchNumber;

    /**
     * Expiry date of the specific batch dispensed (pharmacy compliance).
     * Stored per sale-item so invoices and narcotics registers show accurate expiry info.
     */
    private java.time.LocalDate expiryDate;
}