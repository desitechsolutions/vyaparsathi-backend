
package com.desitech.vyaparsathi.notification.controller;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.desitech.vyaparsathi.notification.dto.NotificationDto;
import com.desitech.vyaparsathi.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Notifications", description = "Operations for user notifications")
public class NotificationController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);
    @Autowired
    private NotificationService service;

    @GetMapping
    @Operation(summary = "Get notifications for user", description = "Retrieve all notifications for the current user")
    @ApiResponse(responseCode = "200", description = "Notifications retrieved successfully")
    public ResponseEntity<List<NotificationDto>> getNotifications(@RequestParam String recipient) {
        try {
            List<NotificationDto> notifications = service.getNotifications(recipient);
            logger.info("Fetched notifications for recipient={}", recipient);
            return ResponseEntity.ok(notifications);
        } catch (Exception e) {
            logger.error("Error fetching notifications for recipient={}: {}", recipient, e.getMessage(), e);
            throw new ApplicationException("Failed to fetch notifications", e);
        }
    }

    @PostMapping("/mark-as-read/{id}")
    @Operation(summary = "Mark notification as read", description = "Mark a notification as read by ID")
    @ApiResponse(responseCode = "200", description = "Notification marked as read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        try {
            service.markAsRead(id);
            logger.info("Marked notification as read, id={}", id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error marking notification as read, id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to mark notification as read", e);
        }
    }
}
