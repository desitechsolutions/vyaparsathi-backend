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

    /**
     * When true, the user has MFA enabled and the caller must complete the
     * MFA challenge before they get a real access token. In this case
     * {@link #accessToken} is empty and {@link #mfaChallengeToken} is set.
     */
    private Boolean mfaRequired;

    /**
     * Short-lived (5-min) MFA challenge token. The client posts this
     * alongside the TOTP / backup code to {@code /api/auth/mfa/verify} to
     * exchange for the real access token. Never sent when
     * {@link #mfaRequired} is null / false.
     */
    private String mfaChallengeToken;

    public AuthResponse(String accessToken, String role, boolean onboardingRequired) {
        this.accessToken = accessToken;
        this.role = role;
        this.onboardingRequired = onboardingRequired;
    }

    /** Factory for the MFA-required response — no access token yet. */
    public static AuthResponse mfaChallenge(String challengeToken, String role) {
        AuthResponse r = new AuthResponse();
        r.setAccessToken(null);
        r.setRole(role);
        r.setOnboardingRequired(Boolean.FALSE);
        r.setMfaRequired(Boolean.TRUE);
        r.setMfaChallengeToken(challengeToken);
        return r;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Boolean getOnboardingRequired() { return onboardingRequired; }
    public void setOnboardingRequired(Boolean onboardingRequired) { this.onboardingRequired = onboardingRequired; }

    public Boolean getMfaRequired() { return mfaRequired; }
    public void setMfaRequired(Boolean mfaRequired) { this.mfaRequired = mfaRequired; }

    public String getMfaChallengeToken() { return mfaChallengeToken; }
    public void setMfaChallengeToken(String mfaChallengeToken) { this.mfaChallengeToken = mfaChallengeToken; }
}
