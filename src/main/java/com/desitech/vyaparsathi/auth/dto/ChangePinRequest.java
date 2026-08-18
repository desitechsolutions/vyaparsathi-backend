package com.desitech.vyaparsathi.auth.dto;

import com.desitech.vyaparsathi.auth.validation.StrongPassword;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Change-password request. Retains the legacy class name
 * {@code ChangePinRequest} + endpoint {@code /api/auth/change-pin} for
 * backwards compatibility; the underlying fields have been renamed
 * password/currentPassword/newPassword with {@link JsonAlias} on the legacy
 * {@code currentPin}/{@code newPin} JSON names so existing frontend
 * versions keep working through Phase 1 rollout.
 */
@Data
public class ChangePinRequest {

    @NotBlank(message = "Current password is required")
    @JsonProperty("currentPassword")
    @JsonAlias({"currentPin"})
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @StrongPassword
    @JsonProperty("newPassword")
    @JsonAlias({"newPin"})
    private String newPassword;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }

    /** @deprecated Legacy alias for {@link #getCurrentPassword()}. */
    @Deprecated
    public String getCurrentPin() { return currentPassword; }

    /** @deprecated Legacy alias for {@link #setCurrentPassword(String)}. */
    @Deprecated
    public void setCurrentPin(String currentPin) { this.currentPassword = currentPin; }

    /** @deprecated Legacy alias for {@link #getNewPassword()}. */
    @Deprecated
    public String getNewPin() { return newPassword; }

    /** @deprecated Legacy alias for {@link #setNewPassword(String)}. */
    @Deprecated
    public void setNewPin(String newPin) { this.newPassword = newPin; }
}
