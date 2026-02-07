package com.desitech.vyaparsathi.notification.service;

import com.desitech.vyaparsathi.notification.dto.NotificationDto;
import com.desitech.vyaparsathi.notification.entity.Notification;
import com.desitech.vyaparsathi.notification.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {
    @Autowired
    private NotificationRepository repository;

    public void sendNotification(String type, String message, String recipient, String link) {
        Notification notification = new Notification();
        notification.setType(type);
        notification.setMessage(message);
        notification.setRecipient(recipient);
        notification.setRead(false);
        notification.setLink(link);
        notification.setTimestamp(LocalDateTime.now());
        repository.save(notification);
    }

    public List<NotificationDto> getNotifications(String recipient) {
        return repository.findByRecipientOrderByTimestampDesc(recipient)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public void markAsRead(Long id) {
        repository.findById(id).ifPresent(n -> {
            n.setRead(true);
            repository.save(n);
        });
    }

    private NotificationDto toDto(Notification n) {
        return new NotificationDto(
                n.getId(),
                n.getType(),
                n.getMessage(),
                n.getRecipient(),
                n.isRead(),
                n.getLink(),
                n.getTimestamp()
        );
    }
}
