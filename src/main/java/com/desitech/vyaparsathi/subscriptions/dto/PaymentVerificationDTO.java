package com.desitech.vyaparsathi.subscriptions.dto;

import com.desitech.vyaparsathi.subscriptions.enums.BillingCycle;
import com.desitech.vyaparsathi.subscriptions.enums.PaymentVerificationStatus;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PaymentVerificationDTO {
    private Long id;
    private String utrNumber;
    private Double amount;
    private Tier planRequested;
    private BillingCycle billingCycle;
    private PaymentVerificationStatus status;
    private LocalDateTime date;
    private String rejectionReason;
}