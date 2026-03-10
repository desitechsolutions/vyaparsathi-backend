package com.desitech.vyaparsathi.supplier.dto;

import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request payload for paying multiple Purchase Orders for a supplier in one transaction.
 * The {@code totalAmount} is distributed across {@code selectedPoIds} in FIFO order until
 * the amount is exhausted or all selected POs are fully settled.
 */
@Data
public class SupplierBulkPaymentRequest {
    private Long supplierId;
    /** IDs of the purchase orders to settle (processed in ID-ascending/FIFO order). */
    private List<Long> selectedPoIds;
    private BigDecimal totalAmount;
    private PaymentMethod paymentMethod;
    private LocalDateTime paymentDate;
    private String reference;
    private String notes;
}
