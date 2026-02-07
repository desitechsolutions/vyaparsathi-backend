package com.desitech.vyaparsathi.receiving.dto;

import com.desitech.vyaparsathi.supplier.dto.SupplierDto;
import com.desitech.vyaparsathi.receiving.enums.ReceivingStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReceivingDto {
    private Long id;

    @NotNull(message = "Purchase Order ID is required")
    private Long purchaseOrderId;
    private String poNumber;
    private ReceivingStatus status;

    private LocalDateTime receivedAt;

    private String receivedBy;

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;

    @Valid
    private List<ReceivingItemDto> receivingItems;

    @NotNull(message = "Shop ID is required")
    private Long shopId;

    private SupplierDto supplier;
    private Integer putawayQty;
    private String putAwayStatus;
}