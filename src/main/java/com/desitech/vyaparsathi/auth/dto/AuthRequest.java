package com.desitech.vyaparsathi.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Login request. Field is called {@code password} for enterprise semantics;
 * legacy clients that still POST {@code pin} continue to work via
 * {@link JsonAlias}. Serialization always emits {@code password}.
 */
@Data
public class AuthRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    @JsonProperty("password")
    @JsonAlias({"pin"})
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    /** @deprecated Legacy alias for {@link #getPassword()}. Use getPassword(). */
    @Deprecated
    public String getPin() { return password; }

    /** @deprecated Legacy alias for {@link #setPassword(String)}. Use setPassword(). */
    @Deprecated
    public void setPin(String pin) { this.password = pin; }
}
