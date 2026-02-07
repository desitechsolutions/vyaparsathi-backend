package com.desitech.vyaparsathi.receiving.dto;

import com.desitech.vyaparsathi.inventory.dto.ItemDto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateReceivingDto {
    @NotNull(message = "Purchase Order ID is required")
    private Long purchaseOrderId;

    @PastOrPresent(message = "Received date cannot be in the future")
    private LocalDateTime receivedDate;
}