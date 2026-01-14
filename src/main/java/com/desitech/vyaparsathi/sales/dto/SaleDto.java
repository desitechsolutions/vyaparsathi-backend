package com.desitech.vyaparsathi.sales.dto;

import com.desitech.vyaparsathi.common.util.CustomLocalDateTimeDeserializer;
import com.desitech.vyaparsathi.delivery.dto.DeliveryDTO;
import com.desitech.vyaparsathi.payment.dto.PaymentDto;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SaleDto {
    private Long id;
    private Long customerId;
    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime date;
    private BigDecimal discount;
    private Boolean isGstRequired;
    private String invoiceNo;
    private String customerName;
    @NotNull(message = "Items list cannot be null")
    @NotEmpty(message = "Items list cannot be empty")
    @Valid
    private List<SaleItemDto> items;
    @Valid
    private List<PaymentDto> paymentDetails;
    private BigDecimal paidAmount;
    private BigDecimal roundOff;
    private BigDecimal totalAmount;
    private BigDecimal dueAmount;
    private DeliveryDTO delivery;
    private String signedInvoiceUrl;

}