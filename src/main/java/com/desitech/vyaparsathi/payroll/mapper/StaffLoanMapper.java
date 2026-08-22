package com.desitech.vyaparsathi.payroll.mapper;

import com.desitech.vyaparsathi.payroll.dto.StaffLoanDto;
import com.desitech.vyaparsathi.payroll.dto.StaffLoanRepaymentDto;
import com.desitech.vyaparsathi.payroll.entity.StaffLoan;
import com.desitech.vyaparsathi.payroll.entity.StaffLoanRepayment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.stream.Collectors;

@Component
public class StaffLoanMapper {

    public StaffLoanDto toDto(StaffLoan entity) {
        if (entity == null) {
            return null;
        }

        return StaffLoanDto.builder()
                .id(entity.getId())
                .employeeId(entity.getEmployee() != null ? entity.getEmployee().getId() : null)
                .employeeName(entity.getEmployee() != null ? entity.getEmployee().getFirstName() + " " + entity.getEmployee().getLastName() : "")
                .loanType(entity.getLoanType())
                .loanNumber(entity.getLoanNumber())
                .principalAmount(entity.getPrincipalAmount())
                .interestRateAnnual(entity.getInterestRateAnnual())
                .tenureMonths(entity.getTenureMonths())
                .monthlyEMI(entity.getMonthlyEMI())
                .totalRepaid(entity.getTotalRepaid())
                .remainingBalance(entity.getRemainingBalance())
                .disbursementDate(entity.getDisbursementDate())
                .recoveryStartMonth(entity.getRecoveryStartMonth())
                .recoveryEndMonth(entity.getRecoveryEndMonth())
                .status(entity.getStatus())
                .remarks(entity.getRemarks())
                .repayments(entity.getRepayments() != null ?
                        entity.getRepayments().stream()
                                .map(this::repaymentToDto)
                                .collect(Collectors.toList())
                        : null)
                .build();
    }

    public StaffLoan toEntity(StaffLoanDto dto) {
        if (dto == null) {
            return null;
        }

        StaffLoan entity = new StaffLoan();
        entity.setId(dto.getId());
        entity.setLoanType(dto.getLoanType());
        entity.setLoanNumber(dto.getLoanNumber());
        entity.setPrincipalAmount(dto.getPrincipalAmount());
        entity.setInterestRateAnnual(dto.getInterestRateAnnual());
        entity.setTenureMonths(dto.getTenureMonths());
        entity.setMonthlyEMI(dto.getMonthlyEMI());
        entity.setTotalRepaid(dto.getTotalRepaid() != null ? dto.getTotalRepaid() : BigDecimal.ZERO);
        entity.setRemainingBalance(dto.getRemainingBalance() != null ? dto.getRemainingBalance() : dto.getPrincipalAmount());
        entity.setDisbursementDate(dto.getDisbursementDate());
        entity.setRecoveryStartMonth(dto.getRecoveryStartMonth());
        entity.setRecoveryEndMonth(dto.getRecoveryEndMonth());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : com.desitech.vyaparsathi.payroll.enums.LoanStatus.ACTIVE);
        entity.setRemarks(dto.getRemarks());
        return entity;
    }

    private StaffLoanRepaymentDto repaymentToDto(StaffLoanRepayment entity) {
        if (entity == null) {
            return null;
        }

        return StaffLoanRepaymentDto.builder()
                .id(entity.getId())
                .loanId(entity.getLoan() != null ? entity.getLoan().getId() : null)
                .installmentNumber(entity.getInstallmentNumber())
                .dueDate(entity.getDueDate())
                .paidDate(entity.getPaidDate())
                .principalAmount(entity.getPrincipalAmount())
                .interestAmount(entity.getInterestAmount())
                .totalEMI(entity.getTotalEMI())
                .paymentStatus(entity.getPaymentStatus())
                .payrollSlipId(entity.getPayrollSlipId())
                .remarks(entity.getRemarks())
                .build();
    }
}
