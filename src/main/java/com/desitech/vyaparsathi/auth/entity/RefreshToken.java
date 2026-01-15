package com.desitech.vyaparsathi.auth.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.shop.entity.Shop;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken extends ShopAwareEntity {

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private Instant expiryDate;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
    private boolean revoked;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = true)
    private Shop shop;
}