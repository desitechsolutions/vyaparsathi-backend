package com.desitech.vyaparsathi.customer.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerAddressDto {
    private Long id;
    private Long customerId;

    /** BILLING · SHIPPING · REGISTERED — free-text so shops can extend. */
    @Size(max = 20)
    private String addressType;

    @Size(max = 120)
    private String label;

    @Size(max = 255)
    private String addressLine1;

    @Size(max = 255)
    private String addressLine2;

    @Size(max = 120)
    private String city;

    @Size(max = 120)
    private String state;

    @Pattern(regexp = "^$|^\\d{2}$", message = "State code must be 2 digits")
    private String stateCode;

    @Size(max = 20)
    private String postalCode;

    @Size(max = 120)
    private String country;

    private Boolean isDefaultBilling;
    private Boolean isDefaultShipping;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
