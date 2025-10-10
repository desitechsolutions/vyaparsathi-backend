package com.desitech.vyaparsathi.auth.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "reset_token")
@Getter
@Setter
@NoArgsConstructor
public class ResetToken extends ShopAwareEntity {

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private LocalDateTime expiry;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
    private Boolean used = false;
}