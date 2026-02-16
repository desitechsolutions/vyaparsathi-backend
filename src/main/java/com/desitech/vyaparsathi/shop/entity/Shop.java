package com.desitech.vyaparsathi.shop.entity;

import com.desitech.vyaparsathi.common.util.LocalDateTimeAttributeConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "shop")
@Getter
@Setter
@NoArgsConstructor
public class Shop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String ownerName;

    private String address;

    @Column(nullable = false)
    private String state;

    private String gstin;

    @Column(unique = true, nullable = false)
    private String code;

    private String locale;

    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();


    @Column(nullable = true)
    String logoPath;

    @Column(name = "industry_type")
    private String industryType;

    private String signaturePath;
}