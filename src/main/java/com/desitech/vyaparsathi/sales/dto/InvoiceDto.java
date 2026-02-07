package com.desitech.vyaparsathi.sales.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class InvoiceDto {
    private Long id;
    private String invoiceNumber;
    private Long saleId;
    private LocalDateTime invoiceDate;
    private String customerName;
    private String customerPhone;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal dueAmount;
    private List<InvoiceItemDto> items;
}
