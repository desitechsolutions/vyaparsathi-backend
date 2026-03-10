package com.desitech.vyaparsathi.purchaseorder.dto;

import com.desitech.vyaparsathi.payment.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Summary of the payment position for a single Purchase Order.
 * Returned by {@code GET /api/purchase-orders/{id}/payment-summary}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderPaymentSummaryDto {
    private Long purchaseOrderId;
    private String poNumber;
    private BigDecimal totalAmount;
    private BigDecimal totalPaid;
    private BigDecimal amountDue;
    private PaymentStatus paymentStatus;
}
