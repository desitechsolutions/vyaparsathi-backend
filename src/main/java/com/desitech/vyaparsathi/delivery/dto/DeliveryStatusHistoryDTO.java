package com.desitech.vyaparsathi.delivery.dto;

import com.desitech.vyaparsathi.delivery.enums.DeliveryStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeliveryStatusHistoryDTO {
    private Long id;
    private Long deliveryId;
    private DeliveryStatus status;
    private LocalDateTime changedAt;
    private String changedBy;
}