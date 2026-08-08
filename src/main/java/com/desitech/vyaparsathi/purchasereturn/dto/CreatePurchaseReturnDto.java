package com.desitech.vyaparsathi.purchasereturn.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CreatePurchaseReturnDto {

    @NotNull(message = "Supplier ID is required")
    private Long supplierId;

    private Long purchaseOrderId;

    private Long receivingId;

    private LocalDateTime returnDate;

    private String notes;

    @NotEmpty(message = "Return items cannot be empty")
    @Valid
    private List<CreatePurchaseReturnItemDto> items;
}
