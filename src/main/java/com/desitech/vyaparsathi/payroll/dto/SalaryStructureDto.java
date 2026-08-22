package com.desitech.vyaparsathi.payroll.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryStructureDto {

    private Long id;

    @NotBlank(message = "Structure name is required")
    private String structureName;

    @NotBlank(message = "Structure code is required")
    private String structureCode;

    private String description;

    private Boolean isActive;

    @NotNull(message = "Effective from date is required")
    private LocalDate effectiveFrom;

    @Valid
    private List<SalaryComponentDto> components;
}
