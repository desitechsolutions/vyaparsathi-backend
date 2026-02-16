package com.desitech.vyaparsathi.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationDto {
    private Long id;
    private String type;
    private String title;
    private String message;
    private String recipient;
    private boolean isRead;
    private String link;
    private String priority;
    private LocalDateTime timestamp;
}