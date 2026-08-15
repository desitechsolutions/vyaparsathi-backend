package com.desitech.vyaparsathi.document.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class EwayBillDto {
    private String number;
    private LocalDateTime generatedAt;
    private LocalDateTime validTill;
    private Integer distanceKm;
    private String transporterGstin;
    private String transporterName;
    private String vehicleNumber;
    private String transportMode;
}
