package com.desitech.vyaparsathi.refund.dto;

import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/payments/{id}/refund}.
 */
@Data
public class RefundRequest {

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01", message = "Refund amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Refund method is required")
    private PaymentMethod paymentMethod;

    /** External reference — cheque number, UPI transaction ID, etc. */
    private String reference;

    private String notes;
}
