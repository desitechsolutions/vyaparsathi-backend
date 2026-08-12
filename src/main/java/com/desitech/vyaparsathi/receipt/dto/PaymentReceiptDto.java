package com.desitech.vyaparsathi.receipt.dto;

import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentReceiptDto {
    private Long id;
    private String receiptNumber;
    private Long paymentId;
    private Long customerId;
    private String customerName;
    private LocalDateTime receiptDate;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private String reference;
    private String notes;

    /** Signed URL the client can use to download the receipt PDF. Populated on creation. */
    private String signedUrl;
}
