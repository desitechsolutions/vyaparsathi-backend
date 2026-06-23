package com.desitech.vyaparsathi.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NewsletterSubscribeRequest {

    @NotBlank(message = "Email address is required")
    @Email(message = "Invalid email format")
    private String email;

    private String source;
}
