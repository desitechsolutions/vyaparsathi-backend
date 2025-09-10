package com.desitech.vyaparsathi.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePinRequest {
    @NotBlank(message = "Current PIN is required")
    private String currentPin;

    @NotBlank(message = "New PIN is required")
    @Size(min = 4, max = 4, message = "New PIN must be exactly 4 digits")
    private String newPin;
}