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

    public Long getReceivingId() { return receivingId; }
    public void setReceivingId(Long receivingId) { this.receivingId = receivingId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRaisedBy() { return raisedBy; }
    public void setRaisedBy(String raisedBy) { this.raisedBy = raisedBy; }
}