package com.desitech.vyaparsathi.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentsSummaryDto {
    private BigDecimal totalPayments;
    private Integer paymentCount;
}
