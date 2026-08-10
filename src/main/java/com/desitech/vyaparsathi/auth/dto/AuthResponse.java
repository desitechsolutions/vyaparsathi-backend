package com.desitech.vyaparsathi.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String role;
    private Boolean onboardingRequired;

    public AuthResponse(String accessToken, String role, boolean onboardingRequired) {
        this.accessToken = accessToken;
        this.role = role;
        this.onboardingRequired = onboardingRequired;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Boolean getOnboardingRequired() { return onboardingRequired; }
    public void setOnboardingRequired(Boolean onboardingRequired) { this.onboardingRequired = onboardingRequired; }
}