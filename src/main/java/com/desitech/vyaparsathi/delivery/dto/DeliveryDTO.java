package com.desitech.vyaparsathi.delivery.dto;

import com.desitech.vyaparsathi.delivery.enums.DeliveryPaidBy;
import com.desitech.vyaparsathi.delivery.enums.DeliveryStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class DeliveryDTO {
    private Long id;
    private Long saleId;
    private String challanNo;
    private String invoiceNumber;
    private String customerName;
    private String deliveryAddress;
    private String deliveryAddressSnapshot;
    private Double deliveryCharge;
    private DeliveryPaidBy deliveryPaidBy;
    private DeliveryStatus deliveryStatus;
    private DeliveryPersonDTO deliveryPerson;
    private String deliveryNotes;
    private LocalDateTime deliveredAt;
    private LocalDate estimatedDeliveryDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<DeliveryStatusHistoryDTO> statusHistory;

    // POD
    private String recipientName;
    private String podSignatureUrl;
    private String podPhotoUrl;
    private String podOtp;
    private LocalDateTime podCollectedAt;

    // COD
    private BigDecimal codAmount;
    private Boolean codCollected;
    private LocalDateTime codCollectedAt;

    // Attempts
    private Integer attemptCount;
    private LocalDateTime lastAttemptAt;
    private String failureReason;

    // Logistics
    private String trackingNumber;
    private String ewayBillNo;
    private String courierPartner;
}
