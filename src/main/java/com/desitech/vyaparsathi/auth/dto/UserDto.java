package com.desitech.vyaparsathi.auth.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserDto {
    private Long id;
    private String username;
    private String role;
    private boolean active;

    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private Long shopId;
    private String shopName;
    private LocalDateTime createdAt;
}