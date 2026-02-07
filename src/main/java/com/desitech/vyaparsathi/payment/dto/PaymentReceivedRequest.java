package com.desitech.vyaparsathi.payment.dto;

import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import com.desitech.vyaparsathi.payment.enums.PaymentSourceType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentReceivedRequest {
    private Long sourceId;
    private PaymentSourceType sourceType;
    private BigDecimal amount;
    private PaymentMethod paymentMethod; // Use String for flexibility, or enum if you have PaymentMethod enum
    private Long customerId;
    private Long supplierId;
    private LocalDateTime paymentDate;
    private String transactionId; // Optional: for client/system idempotency
    private String reference;
    private String notes;
}