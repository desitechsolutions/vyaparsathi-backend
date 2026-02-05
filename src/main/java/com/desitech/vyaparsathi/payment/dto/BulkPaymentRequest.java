package com.desitech.vyaparsathi.payment.dto;

import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class BulkPaymentRequest {
    private Long customerId;
    private BigDecimal totalAmount;
    private PaymentMethod paymentMethod;
    private LocalDateTime paymentDate;
    private String reference;
    private String notes;
    private List<Long> selectedSaleIds;
}