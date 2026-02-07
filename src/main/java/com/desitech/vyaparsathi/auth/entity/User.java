package com.desitech.vyaparsathi.auth.entity;

import com.desitech.vyaparsathi.auth.model.Role;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.shop.entity.Shop;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @NotBlank(message = "Username is required")
    @Column(unique = true, nullable = false, length = 100)
    private String username;

    @NotBlank(message = "PIN hash is required")
    @Column(nullable = false, length = 255)
    private String pinHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    // Critical fix: Allow null during registration/onboarding
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = true)
    private Shop shop;
}