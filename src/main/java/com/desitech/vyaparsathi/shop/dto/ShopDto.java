package com.desitech.vyaparsathi.shop.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShopDto {

    private Long id;
    @NotBlank(message = "Shop name is required")
    private String name;

    private String ownerName;

    private String address;

    @NotBlank(message = "State is required")
    private String state;

    private String gstin;

    @NotBlank(message = "Shop code is required")
    private String code;

    private String locale;
}