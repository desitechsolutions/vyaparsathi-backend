package com.desitech.vyaparsathi.supplier.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SupplierStatementDto {
    private Long supplierId;
    private String supplierName;
    private BigDecimal openingBalance = BigDecimal.ZERO;
    private BigDecimal closingBalance = BigDecimal.ZERO;
    private BigDecimal totalBilled = BigDecimal.ZERO;
    private BigDecimal totalPaid = BigDecimal.ZERO;
    private BigDecimal totalReturned = BigDecimal.ZERO;
    private List<SupplierStatementEntryDto> statementEntries = new ArrayList<>();
}
