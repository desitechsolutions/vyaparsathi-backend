package com.desitech.vyaparsathi.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Payment method breakdown over a range. Uses actual payment records
 * (not sale.paymentMethod) so partial payments and multi-tender sales
 * are reflected correctly.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMixDto {
    private LocalDate from;
    private LocalDate to;
    private BigDecimal totalAmount;
    private long totalTxnCount;
    private List<PaymentMethodShareDto> methods;
}
