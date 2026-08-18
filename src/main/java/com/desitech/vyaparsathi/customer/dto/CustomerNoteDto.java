package com.desitech.vyaparsathi.customer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerNoteDto {
    private Long id;
    private Long customerId;

    @NotBlank(message = "Note body is required")
    private String body;

    private Long authorUserId;
    private String authorName;
    private Boolean pinned;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
