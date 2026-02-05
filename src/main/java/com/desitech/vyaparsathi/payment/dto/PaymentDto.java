package com.desitech.vyaparsathi.payment.dto;

import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import com.desitech.vyaparsathi.payment.enums.PaymentSourceType;
import com.desitech.vyaparsathi.payment.enums.PaymentStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentDto {
    private Long id;
    private Long sourceId;
    private PaymentSourceType sourceType;
    private Long supplierId;
    private Long customerId;
    private BigDecimal amount;
    private LocalDateTime paymentDate;
    private PaymentMethod paymentMethod;
    private String reference;
    private String notes;
    private String transactionId;
    private PaymentStatus status;
    private String invoiceNumber;
}