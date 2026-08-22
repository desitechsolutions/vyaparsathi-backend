package com.desitech.vyaparsathi.payroll.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayEventDto {
    private Long id;

    @NotNull(message = "Holiday date is required")
    private LocalDate holidayDate;

    @NotNull(message = "Event name is required")
    private String eventName;

    @NotNull(message = "Event type is required")
    private String eventType; // NATIONAL, REGIONAL, OPTIONAL, RESTRICTED

    private String description;
}
