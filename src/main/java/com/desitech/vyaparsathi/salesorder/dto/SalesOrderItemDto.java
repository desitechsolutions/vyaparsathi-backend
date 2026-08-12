package com.desitech.vyaparsathi.salesorder.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SalesOrderItemDto {
    private Long id;
    private Long itemVariantId;

    @NotBlank(message = "Item name cannot be blank")
    private String itemName;

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity must be positive")
    private BigDecimal qty;

    private BigDecimal fulfilledQty;

    @NotNull(message = "Unit price is required")
    private BigDecimal unitPrice;

    private BigDecimal discount = BigDecimal.ZERO;
    private Integer gstRate = 0;

    private String customItemName;
    private String customDescription;
    private String customHsnSac;
    private String customUnit;

    private BigDecimal taxableValue;
    private BigDecimal cgstAmt;
    private BigDecimal sgstAmt;
    private BigDecimal igstAmt;
    private BigDecimal lineTotal;

    private String hsnSac;
    private String unit;

    @AssertTrue(message = "Line must reference a product (itemVariantId) or provide a customItemName")
    public boolean isEitherCatalogOrCustom() {
        return itemVariantId != null
                || (customItemName != null && !customItemName.trim().isEmpty());
    }
}
