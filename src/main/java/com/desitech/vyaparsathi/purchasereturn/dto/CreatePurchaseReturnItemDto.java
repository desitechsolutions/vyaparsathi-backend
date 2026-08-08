package com.desitech.vyaparsathi.purchasereturn.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class CreatePurchaseReturnItemDto {

    @NotNull(message = "Item variant ID is required")
    private Long itemVariantId;

    private String batchNumber;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "Unit cost is required")
    private BigDecimal unitCost;

    private String reason;
}
