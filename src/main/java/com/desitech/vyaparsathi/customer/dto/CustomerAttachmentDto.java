package com.desitech.vyaparsathi.customer.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerAttachmentDto {
    private Long id;
    private Long customerId;
    private String fileName;
    private String filePath;
    private String mimeType;
    private Long sizeBytes;
    private String category; // KYC / CONTRACT / PO / OTHER
    private String uploadedBy;
    private LocalDateTime createdAt;
}
