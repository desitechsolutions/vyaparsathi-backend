package com.desitech.vyaparsathi.delivery.dto;

import com.desitech.vyaparsathi.delivery.enums.DeliveryPaidBy;
import com.desitech.vyaparsathi.delivery.enums.DeliveryStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DeliveryDTO {
    private Long id;
    private Long saleId;
    private String invoiceNumber;
    private String customerName;
    private String deliveryAddress;
    private Double deliveryCharge;
    private DeliveryPaidBy deliveryPaidBy;
    private DeliveryStatus deliveryStatus;
    private DeliveryPersonDTO deliveryPerson;
    private String deliveryNotes;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<DeliveryStatusHistoryDTO> statusHistory;
}