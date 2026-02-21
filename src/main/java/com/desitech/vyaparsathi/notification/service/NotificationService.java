package com.desitech.vyaparsathi.notification.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.event.NotificationEvent;
import com.desitech.vyaparsathi.notification.dto.ContactRequestDto;
import com.desitech.vyaparsathi.notification.dto.NotificationDto;
import com.desitech.vyaparsathi.notification.entity.Notification;
import com.desitech.vyaparsathi.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private NotificationRepository repository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private EmailService emailService;

    /**
     * Listen for internal Spring Events.
     * Logic: Save to DB for persistence, then push via WebSocket for real-time UI.
     */
    @Async("notificationExecutor")
    @EventListener
    @Transactional
    public void handleNotificationEvent(NotificationEvent event) {
        logger.debug("Processing NotificationEvent for: {}", event.getRecipient());
        try {
            Notification n = new Notification();
            n.setType(event.getType());
            n.setTitle(event.getTitle());
            n.setMessage(event.getMessage());
            n.setRecipient(event.getRecipient());
            n.setLink(event.getLink());
            n.setPriority(event.getPriority() != null ? event.getPriority() : "medium");
            n.setRead(false);
            n.setTimestamp(LocalDateTime.now());

            Notification savedNotification = repository.save(n);
            logger.info("Notification saved. ID: {} | Recipient: {}", savedNotification.getId(), event.getRecipient());

            // BROADCAST: Push to WebSocket so the UI updates instantly
            pushRealTimeUpdate(savedNotification);

        } catch (Exception e) {
            logger.error("Failed to process notification event", e);
        }
    }

    /**
     * Pushes the notification to the frontend using the secured shop-specific topic.
     */
    private void pushRealTimeUpdate(Notification notification) {
        Long shopId = TenantContext.getCurrentShopId();
        if (shopId != null) {
            // Path matches our WebSocketConfig security interceptor: /topic/shop/{shopId}/notifications
            String destination = "/topic/shop/" + shopId + "/notifications";
            messagingTemplate.convertAndSend(destination, toDto(notification));
            logger.info("Real-time notification pushed to shop: {}", shopId);
        } else {
            logger.warn("No ShopId found in TenantContext. WebSocket push skipped.");
        }
    }

    /**
     * Manual notification trigger.
     * Modified to also push real-time updates.
     */
    @Transactional
    public void sendNotification(String type, String title, String message, String recipient, String link, String priority) {
        Notification n = new Notification();
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setRecipient(recipient);
        n.setRead(false);
        n.setLink(link);
        n.setPriority(priority);
        n.setTimestamp(LocalDateTime.now());

        Notification saved = repository.save(n);
        pushRealTimeUpdate(saved);
    }

    /**
     * Optimized helper for specific string-based messaging (e.g., Password Reset).
     * If it's a private message, it uses the /user prefix.
     */
    public void sendDirectMessage(String recipientUsername, String message) {
        messagingTemplate.convertAndSendToUser(recipientUsername, "/queue/notifications", message);
        logger.info("Direct WebSocket message sent to user: {}", recipientUsername);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(String recipient) {
        return repository.findByRecipientOrderByTimestampDesc(recipient)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void markAsRead(Long id) {
        repository.findById(id).ifPresent(n -> n.setRead(true));
    }

    @Transactional
    public void markAllAsRead(String recipient) {
        List<Notification> unread = repository.findByRecipientAndIsReadFalse(recipient);
        unread.forEach(n -> n.setRead(true));
        logger.info("Marked all notifications as read for: {}", recipient);
    }

    @Transactional
    public void clearAll(String recipient) {
        repository.deleteByRecipient(recipient);
        logger.info("Cleared all notifications for: {}", recipient);
    }

    private NotificationDto toDto(Notification n) {
        return new NotificationDto(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getRecipient(),
                n.isRead(),
                n.getLink(),
                n.getPriority(),
                n.getTimestamp()
        );
    }

    /**
     * Processes public demo/contact requests.
     * 1. Sends email to admin.
     * 2. Saves notification for persistent record.
     * 3. (Optional) Pushes real-time alert to all online Admins.
     */
    @Transactional
    public void processContactRequest(ContactRequestDto request) {
        String adminEmail = "sales@desitechsolutions.com";
        String subject = "New Demo Request: " + request.getServiceType();

        String htmlContent = String.format(
                "<h3>New Lead from Website</h3>" +
                        "<p><b>Name:</b> %s</p>" +
                        "<p><b>Email:</b> %s</p>" +
                        "<p><b>Phone:</b> %s</p>" +
                        "<p><b>Company:</b> %s</p>" +
                        "<p><b>Message:</b> %s</p>",
                request.getName(), request.getEmail(), request.getPhone(),
                request.getCompany(), request.getMessage()
        );

        try {
            // 1. Send Email
            emailService.sendEmail(adminEmail, subject, htmlContent);

            // 2. Save as System Notification for internal tracking (Recipient = 'ADMIN')
            /*sendNotification(
                    "LEAD",
                    "New Demo Request",
                    "Lead from " + request.getName() + " (" + request.getCompany() + ")",
                    "ADMIN",
                    null,
                    "high"
            );*/

            logger.info("Public contact request processed for: {}", request.getEmail());
        } catch (Exception e) {
            logger.error("Error processing contact request", e);
            throw new RuntimeException("Failed to process request");
        }
    }
}