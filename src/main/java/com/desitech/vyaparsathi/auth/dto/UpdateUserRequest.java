package com.desitech.vyaparsathi.auth.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class UpdateUserRequest {
    private String firstName;
    private String lastName;

    @Email(message = "Please provide a valid email address")
    private String email;

    private Long shopId;
}