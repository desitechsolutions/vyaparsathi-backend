package com.desitech.vyaparsathi.document.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class DocumentAuditDto {
    private String createdBy;
    private LocalDateTime createdAt;
    private String modifiedBy;
    private LocalDateTime modifiedAt;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String printedBy;
    private LocalDateTime printedAt;
    private Integer printCount;
    private String documentHash;
    private String verificationUrl;
}
