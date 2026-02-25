package com.desitech.vyaparsathi.subscriptions.dto;

import com.desitech.vyaparsathi.subscriptions.enums.BillingCycle;
import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import lombok.Data;

@Data
public class PaymentRequest {
    private String utrNumber;
    private Tier planTier;
    private BillingCycle billingCycle;
    private Double amountPaid;
}