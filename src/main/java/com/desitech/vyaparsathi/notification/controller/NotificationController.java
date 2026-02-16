package com.desitech.vyaparsathi.notification.controller;

import com.desitech.vyaparsathi.notification.dto.NotificationDto;
import com.desitech.vyaparsathi.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Notifications")
public class NotificationController {

    @Autowired
    private NotificationService service;

    @GetMapping
    @Operation(summary = "Get all notifications for user")
    public ResponseEntity<List<NotificationDto>> getNotifications(@RequestParam String recipient) {
        return ResponseEntity.ok(service.getNotifications(recipient));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark a single notification as read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        service.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all notifications as read for a recipient")
    public ResponseEntity<Void> markAllRead(@RequestParam String recipient) {
        service.markAllAsRead(recipient);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/clear-all")
    @Operation(summary = "Delete all notifications for a recipient")
    public ResponseEntity<Void> clearAll(@RequestParam String recipient) {
        service.clearAll(recipient);
        return ResponseEntity.ok().build();
    }
}