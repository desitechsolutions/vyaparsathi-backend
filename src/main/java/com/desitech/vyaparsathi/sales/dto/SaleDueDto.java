package com.desitech.vyaparsathi.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SaleDueDto {
    private Long saleId;
    private String invoiceNo;
    private BigDecimal dueAmount;
    private Long customerId;
    private LocalDateTime date;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private String customerName;
    private String addressLine1;
    private String city;
    private String state;
    private String GSTIN;
    private String phone;
    private String shopName;
    private String postalCode;
    private String status;
    private String einvoiceStatus;
    private String irn;
    private String ackNo;
    private LocalDateTime ackDate;
    private String qrCodePath;
    private String ewayBillNo;
}
