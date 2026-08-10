package com.desitech.vyaparsathi.platform.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImpersonationResponseDto {
    private String token;
    private String sessionUuid;
    private Long targetShopId;
    private String targetShopName;
    private String targetUsername;
    private LocalDateTime expiresAt;
}
