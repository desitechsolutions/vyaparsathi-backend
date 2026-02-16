package com.desitech.vyaparsathi.payroll.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class StaffDto {
    private Long id;

    @NotBlank(message = "Name is required")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Phone must be 10 digits")
    private String phone;

    @NotBlank(message = "Role is required")
    private String role;

    @NotNull(message = "Base salary is required")
    @DecimalMin(value = "0.0", message = "Salary cannot be negative")
    private BigDecimal baseSalary;

    private BigDecimal advanceBalance;

    @NotNull(message = "Joining date is required")
    private LocalDate joiningDate;

    private boolean active = true;
}