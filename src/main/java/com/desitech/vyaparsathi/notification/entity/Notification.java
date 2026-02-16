package com.desitech.vyaparsathi.notification.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Getter @Setter @NoArgsConstructor
public class Notification extends ShopAwareEntity {
    private String type;
    private String title;
    private String message;
    private String recipient;

    @Column(name = "is_read")
    private boolean isRead = false;

    private String link;
    private String priority;
    private LocalDateTime timestamp;
}