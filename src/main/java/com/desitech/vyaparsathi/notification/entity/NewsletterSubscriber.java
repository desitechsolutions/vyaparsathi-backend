package com.desitech.vyaparsathi.notification.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "newsletter_subscriber")
@Getter
@Setter
@NoArgsConstructor
public class NewsletterSubscriber extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "subscribed_at", nullable = false)
    private LocalDateTime subscribedAt = LocalDateTime.now();

    @Column(name = "unsubscribed_at")
    private LocalDateTime unsubscribedAt;

    private String source;

    @Column(name = "unsubscribe_token", nullable = false, unique = true)
    private String unsubscribeToken;
}
