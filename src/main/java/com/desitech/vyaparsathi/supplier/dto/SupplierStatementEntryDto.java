package com.desitech.vyaparsathi.supplier.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupplierStatementEntryDto {
    private LocalDateTime date;
    private String transactionType; // PURCHASE_ORDER, PAYMENT, PURCHASE_RETURN
    private String referenceNo;
    private String description;
    private BigDecimal debitAmount;  // Payments / Debit Notes (reduces liability)
    private BigDecimal creditAmount; // Purchase Invoices (increases liability)
    private BigDecimal runningBalance;
}
