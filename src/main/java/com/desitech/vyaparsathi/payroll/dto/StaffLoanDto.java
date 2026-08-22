package com.desitech.vyaparsathi.payroll.dto;

import com.desitech.vyaparsathi.payroll.enums.LoanStatus;
import com.desitech.vyaparsathi.payroll.enums.LoanType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffLoanDto {

    private Long id;

    @NotNull(message = "Employee ID is required")
    private Long employeeId;

    private String employeeName;

    @NotNull(message = "Loan type is required")
    private LoanType loanType;

    private String loanNumber;

    @NotNull(message = "Principal amount is required")
    private BigDecimal principalAmount;

    private BigDecimal interestRateAnnual;

    @NotNull(message = "Tenure in months is required")
    private Integer tenureMonths;

    @NotNull(message = "Monthly EMI is required")
    private BigDecimal monthlyEMI;

    private BigDecimal totalRepaid;

    private BigDecimal remainingBalance;

    @NotNull(message = "Disbursement date is required")
    private LocalDate disbursementDate;

    @NotNull(message = "Recovery start month is required")
    private String recoveryStartMonth;

    private String recoveryEndMonth;

    private LoanStatus status;

    private String remarks;

    private List<StaffLoanRepaymentDto> repayments;
}
