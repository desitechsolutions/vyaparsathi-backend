package com.desitech.vyaparsathi.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerContactDto {
    private Long id;
    private Long customerId;

    @NotBlank(message = "Contact name is required")
    @Size(max = 200)
    private String name;

    @Size(max = 120)
    private String designation;

    @Pattern(regexp = "^$|^[+\\d][\\d\\s-]{5,29}$", message = "Invalid phone format")
    private String phone;

    @Email(message = "Invalid email")
    private String email;

    /** Exactly one contact per customer is primary; service enforces the invariant. */
    private Boolean isPrimary;

    @Size(max = 500)
    private String notes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
