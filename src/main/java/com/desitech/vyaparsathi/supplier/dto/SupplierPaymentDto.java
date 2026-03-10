package com.desitech.vyaparsathi.supplier.dto;

import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import com.desitech.vyaparsathi.supplier.enums.SupplierPaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SupplierPaymentDto {
    private Long id;
    private Long supplierId;
    private Long purchaseOrderId;
    private BigDecimal amount;
    private LocalDateTime paymentDate;
    private PaymentMethod paymentMethod;
    private String reference;
    private String notes;
    private SupplierPaymentStatus status;
}
