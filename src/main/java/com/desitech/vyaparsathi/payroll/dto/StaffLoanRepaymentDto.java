package com.desitech.vyaparsathi.payroll.dto;

import com.desitech.vyaparsathi.payroll.enums.RepaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffLoanRepaymentDto {

    private Long id;

    private Long loanId;

    private Integer installmentNumber;

    private LocalDate dueDate;

    private LocalDate paidDate;

    private BigDecimal principalAmount;

    private BigDecimal interestAmount;

    private BigDecimal totalEMI;

    private RepaymentStatus paymentStatus;

    private Long payrollSlipId;

    private String remarks;
}
