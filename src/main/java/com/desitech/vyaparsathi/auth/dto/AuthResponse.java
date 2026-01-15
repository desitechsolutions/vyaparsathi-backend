package com.desitech.vyaparsathi.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class AuthResponse {
    private String token;
    private String refreshToken;
    private String role;
    private Boolean onboardingRequired;

    public AuthResponse(String token, String refreshToken, String role) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.role = role;
    }
}