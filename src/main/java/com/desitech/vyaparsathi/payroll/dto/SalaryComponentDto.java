package com.desitech.vyaparsathi.payroll.dto;

import com.desitech.vyaparsathi.payroll.enums.ComponentType;
import com.desitech.vyaparsathi.payroll.enums.CalculationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryComponentDto {

    private Long id;

    @NotBlank(message = "Component name is required")
    private String componentName;

    @NotBlank(message = "Component code is required")
    private String componentCode;

    @NotNull(message = "Component type is required")
    private ComponentType componentType;

    @NotNull(message = "Calculation type is required")
    private CalculationType calculationType;

    @NotNull(message = "Calculation value is required")
    private BigDecimal calculationValue;

    private Boolean isTaxable;

    private Boolean affectsPF;

    private Boolean affectsESI;

    private Boolean isStatutory;

    private Boolean isActive;

    private Integer orderSequence;
}
