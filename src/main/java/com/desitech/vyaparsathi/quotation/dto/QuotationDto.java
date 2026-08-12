package com.desitech.vyaparsathi.quotation.dto;

import com.desitech.vyaparsathi.customer.dto.CustomerDto;
import com.desitech.vyaparsathi.quotation.enums.QuotationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class QuotationDto {
    private Long id;
    private String quotationNo;

    private Long customerId;
    private CustomerDto customer;

    private LocalDateTime quotationDate;
    private LocalDate expiryDate;

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

    private QuotationStatus status;
    private Long convertedToSaleId;
    private String convertedInvoiceNo;

    @NotEmpty(message = "Quotation must have at least one item")
    @Valid
    private List<QuotationItemDto> items = new ArrayList<>();

    /** Signed URL the client can use to download the quotation PDF. */
    private String signedUrl;
}
