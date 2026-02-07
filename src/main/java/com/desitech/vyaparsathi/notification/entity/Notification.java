package com.desitech.vyaparsathi.notification.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends ShopAwareEntity {
    private String type;
    private String message;
    private String recipient;
    private boolean read;
    private String link;
    private LocalDateTime timestamp;
}
