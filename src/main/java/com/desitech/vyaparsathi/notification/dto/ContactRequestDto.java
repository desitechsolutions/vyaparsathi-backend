package com.desitech.vyaparsathi.notification.dto;

import lombok.Data;

@Data
public class ContactRequestDto {
    private String name;
    private String email;
    private String phone;
    private String company;
    private String serviceType;
    private String message;
}