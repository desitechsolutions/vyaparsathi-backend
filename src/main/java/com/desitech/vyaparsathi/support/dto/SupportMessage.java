package com.desitech.vyaparsathi.support.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SupportMessage {

    private Long id;

    private Long shopId;
    private String shopName;

    private String senderName;
    private String message;

    private boolean isFromAdmin;
    private boolean isReadByAdmin;

    private LocalDateTime timestamp;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
