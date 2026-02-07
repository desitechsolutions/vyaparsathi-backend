package com.desitech.vyaparsathi.receiving.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReceivingTicketDTO {
    @NotNull(message = "Receiving ID is required")
    private Long receivingId;

    @NotBlank(message = "Reason is required")
    private String reason;

    @NotBlank(message = "Description is required")
    private String description;

    @NotBlank(message = "Raised By is required")
    private String raisedBy;
}