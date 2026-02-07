package com.desitech.vyaparsathi.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private Long id;
    private String type;
    private String message;
    private String recipient;
    private boolean read;
    private String link;
    private java.time.LocalDateTime timestamp;
}
