package com.desitech.vyaparsathi.analytics.dto;

import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodShareDto {
    private PaymentMethod method;
    private BigDecimal amount;
    private long txnCount;
    private double percentage; // 0..100, rounded to 2 decimals
}
