package com.desitech.vyaparsathi.payroll.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveApplicationDto {
    private Long id;

    @NotNull(message = "Employee ID is required")
    private Long employeeId;

    @NotNull(message = "Leave type ID is required")
    private Long leaveTypeId;

    @NotNull(message = "From date is required")
    private LocalDate fromDate;

    @NotNull(message = "To date is required")
    private LocalDate toDate;

    @NotNull(message = "Number of days is required")
    private BigDecimal days;

    private String reason;

    private String status; // DRAFT, PENDING, APPROVED, REJECTED, CANCELLED

    private Long approverId;

    private String approverName;

    private String approvalComments;

    private String employeeName;

    private String leaveTypeName;
}
