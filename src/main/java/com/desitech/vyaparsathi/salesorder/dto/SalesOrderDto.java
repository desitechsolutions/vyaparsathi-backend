package com.desitech.vyaparsathi.salesorder.dto;

import com.desitech.vyaparsathi.customer.dto.CustomerDto;
import com.desitech.vyaparsathi.salesorder.enums.SalesOrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderDto {
    private Long id;
    private String orderNo;

    private Long customerId;
    private CustomerDto customer;

    private LocalDateTime orderDate;
    private LocalDate expectedDeliveryDate;

    private BigDecimal totalTaxableAmount;
    private BigDecimal totalCgst;
    private BigDecimal totalSgst;
    private BigDecimal totalIgst;
    private BigDecimal invoiceDiscount = BigDecimal.ZERO;
    private BigDecimal shippingCharges = BigDecimal.ZERO;
    private BigDecimal otherCharges = BigDecimal.ZERO;
    private BigDecimal totalAmount;

    private Boolean isGstRequired = Boolean.TRUE;

    private String notes;
    private String terms;

    private SalesOrderStatus status;

    private Long quotationId;
    private String quotationNo;

    /**
     * Populated by {@code convertToSale} — the id of the freshly-created DRAFT sale
     * so the frontend can navigate directly to it (via {@code ?resumeId=…}).
     * Null on every other read.
     */
    private Long createdSaleId;

    @NotEmpty(message = "Sales order must have at least one item")
    @Valid
    private List<SalesOrderItemDto> items = new ArrayList<>();

    private String signedUrl;
}
