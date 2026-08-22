package com.desitech.vyaparsathi.payroll.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayCalendarDto {
    private Long id;

    @NotNull(message = "Year is required")
    @Min(value = 2000, message = "Year must be valid")
    private Integer year;

    @NotNull(message = "Calendar name is required")
    private String name;

    @NotNull(message = "Total working days is required")
    private Integer totalWorkingDays;

    @NotNull(message = "Weekly off days are required")
    private String weeklyOffDays;

    private List<HolidayEventDto> events;
}
