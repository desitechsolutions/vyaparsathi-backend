package com.desitech.vyaparsathi.sales.dto;

import com.desitech.vyaparsathi.common.util.CustomLocalDateTimeDeserializer;
import com.desitech.vyaparsathi.customer.dto.CustomerDto;
import com.desitech.vyaparsathi.delivery.dto.DeliveryDTO;
import com.desitech.vyaparsathi.payment.dto.PaymentDto;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SaleDto {
    private Long id;
    private CustomerDto customer;
    private List<SaleItemDto> items;
    private List<PaymentDto> paymentDetails;
    private DeliveryDTO delivery;

    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal dueAmount;
    private BigDecimal roundOff;
    private BigDecimal discount;
    private Boolean isGstRequired;
    private String invoiceNo;


    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime date;

    private String signedInvoiceUrl;
    private String status;
}
