package com.desitech.vyaparsathi.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {
    private String firstName;
    private String lastName;

    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Size(min = 10, max = 15)
    private String phone;

    private Long shopId;
}