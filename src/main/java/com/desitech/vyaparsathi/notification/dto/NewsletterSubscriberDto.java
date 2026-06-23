package com.desitech.vyaparsathi.notification.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NewsletterSubscriberDto {
    private Long id;
    private String email;
    private boolean active;
    private LocalDateTime subscribedAt;
    private LocalDateTime unsubscribedAt;
    private String source;
    private String unsubscribeToken;
}
