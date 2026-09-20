package com.desitech.vyaparsathi.auth.entity;

import com.desitech.vyaparsathi.auth.model.AuthProvider;
import com.desitech.vyaparsathi.auth.model.Role;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.shop.entity.Shop;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends ShopAwareEntity {

    @Column(length = 100)
    private String firstName;

    @Column(length = 100)
    private String lastName;

    @Email(message = "Invalid email format")
    @Column(unique = true, length = 255)
    private String email;

    private String phone;

    @NotBlank(message = "Username is required")
    @Column(unique = true, nullable = false, length = 100)
    private String username;

    @NotBlank(message = "Password hash is required")
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @Column
    private LocalDateTime lastLoginAt;

    @Column
    private LocalDateTime lastPasswordChangeAt;

    /** Consecutive failed-login counter. Reset to 0 on successful auth. */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    /** When set and in the future, the account is temporarily locked. */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /** Whether the user's email has been verified via the email-verification flow. */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    /** SHA-256 hex of the current email-verification token; null when no verification is pending. */
    @Column(name = "email_verification_token_hash", length = 64)
    private String emailVerificationTokenHash;

    /** Expiry timestamp of the current email-verification token. */
    @Column(name = "email_verification_expiry")
    private LocalDateTime emailVerificationExpiry;

    /** Identity provider used to create this account. LOCAL for password-based accounts. */
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    /** Subject / OID from the OAuth2 provider. Null for LOCAL accounts. */
    @Column(name = "auth_provider_id", length = 255)
    private String authProviderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = true)
    private Shop shop;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public LocalDateTime getLastPasswordChangeAt() { return lastPasswordChangeAt; }
    public void setLastPasswordChangeAt(LocalDateTime lastPasswordChangeAt) { this.lastPasswordChangeAt = lastPasswordChangeAt; }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }

    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public String getEmailVerificationTokenHash() { return emailVerificationTokenHash; }
    public void setEmailVerificationTokenHash(String emailVerificationTokenHash) {
        this.emailVerificationTokenHash = emailVerificationTokenHash;
    }

    public LocalDateTime getEmailVerificationExpiry() { return emailVerificationExpiry; }
    public void setEmailVerificationExpiry(LocalDateTime emailVerificationExpiry) {
        this.emailVerificationExpiry = emailVerificationExpiry;
    }

    public AuthProvider getAuthProvider() { return authProvider; }
    public void setAuthProvider(AuthProvider authProvider) { this.authProvider = authProvider; }

    public String getAuthProviderId() { return authProviderId; }
    public void setAuthProviderId(String authProviderId) { this.authProviderId = authProviderId; }

    public Shop getShop() { return shop; }
    public void setShop(Shop shop) { this.shop = shop; }
}