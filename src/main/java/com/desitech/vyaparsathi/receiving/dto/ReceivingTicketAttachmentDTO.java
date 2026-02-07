package com.desitech.vyaparsathi.receiving.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ReceivingTicketAttachmentDTO {
    @NotNull(message = "Receiving Ticket ID is required")
    private Long receivingTicketId;

    @NotNull(message = "File is required")
    private MultipartFile file;
}
