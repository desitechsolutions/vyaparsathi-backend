package com.desitech.vyaparsathi.payroll.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveTypeDto {
    private Long id;

    @NotBlank(message = "Leave type name is required")
    private String name;

    @NotNull
    @Min(value = 1, message = "Max days per year must be at least 1")
    private Integer maxDaysPerYear;

    @NotNull
    @Min(value = 0, message = "Carry forward days cannot be negative")
    private Integer carryForwardDays;

    @NotNull
    private Boolean isProrated;

    @NotNull
    private Boolean isActive;
}
