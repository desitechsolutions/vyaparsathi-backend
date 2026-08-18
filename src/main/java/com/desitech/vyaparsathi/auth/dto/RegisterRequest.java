package com.desitech.vyaparsathi.auth.dto;

import com.desitech.vyaparsathi.auth.model.Role;
import com.desitech.vyaparsathi.auth.validation.StrongPassword;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registration payload. Field is called {@code password} for enterprise
 * semantics; legacy clients that POST {@code pin} continue to work via
 * {@link JsonAlias}. Server always enforces {@link StrongPassword}.
 */
@Data
@NoArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    @StrongPassword
    @JsonProperty("password")
    @JsonAlias({"pin"})
    private String password;

    @NotNull(message = "Role is required")
    private Role role;

    private String firstName;
    private String lastName;

    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Size(min = 10, max = 15)
    private String phone;

    private Long shopId;

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

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }
}
