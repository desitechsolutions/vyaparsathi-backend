package com.desitech.vyaparsathi.payroll.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class StaffResponseDto extends StaffDto {
    // This is the key field for your UI logic
    private boolean isPaidInCurrentPeriod;

    // You can also add these for better UI summaries without extra API calls
    private LocalDate lastPaymentDate;
    private BigDecimal lastPaidAmount;
}