package com.desitech.vyaparsathi.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdvanceRequestDto {
    private Long id;
    private BigDecimal amount;
    private String reason;
    private String status;
    private LocalDate requestedAt;
    private LocalDate approvedAt;
    private LocalDate disbursedAt;
}
