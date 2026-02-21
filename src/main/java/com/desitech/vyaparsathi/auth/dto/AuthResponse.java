package com.desitech.vyaparsathi.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String role;
    private Boolean onboardingRequired;
}