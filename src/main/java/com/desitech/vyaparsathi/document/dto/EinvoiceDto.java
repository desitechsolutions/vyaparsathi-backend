package com.desitech.vyaparsathi.document.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class EinvoiceDto {
    private String irn;
    private String ackNumber;
    private LocalDateTime ackDate;
    private String qrPayload;
}
