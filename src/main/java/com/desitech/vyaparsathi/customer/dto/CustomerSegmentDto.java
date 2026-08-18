package com.desitech.vyaparsathi.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerSegmentDto {
    private Long id;

    @NotBlank(message = "Segment name is required")
    @Size(max = 80)
    private String name;

    @Size(max = 500)
    private String description;

    /** Hex color for the FE chip. Empty → auto-picked from name hash. */
    @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$", message = "Color must be a 6-digit hex like #F59E0B")
    private String color;

    /** Populated on list endpoints — count of customers in this segment. */
    private Long memberCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
