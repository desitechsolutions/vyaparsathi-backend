package com.desitech.vyaparsathi.refund.dto;

import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import com.desitech.vyaparsathi.refund.enums.RefundStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RefundDto {
    private Long id;
    private String refundNo;
    private Long originalPaymentId;
    private Long customerId;
    private String customerName;
    private LocalDateTime refundDate;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private String reference;
    private String notes;
    private RefundStatus status;

    /** Signed URL the client can use to download the refund receipt PDF. */
    private String signedUrl;
}
