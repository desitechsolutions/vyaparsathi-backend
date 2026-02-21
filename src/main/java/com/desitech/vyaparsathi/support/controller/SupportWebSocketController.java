package com.desitech.vyaparsathi.support.controller;

import com.desitech.vyaparsathi.support.dto.TypingDTO;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class SupportWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    public SupportWebSocketController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Admin types -> Message sent to /app/admin/typing
     * We broadcast it to the specific shop
     */
    @MessageMapping("/admin/typing")
    public void handleAdminTyping(@Payload TypingDTO typing) {
        // Broadcast to the Shop Owner's private topic
        messagingTemplate.convertAndSend(
                "/topic/shop/" + typing.getShopId() + "/typing",
                typing
        );
    }

    /**
     * Shop Owner types -> Message sent to /app/shop/typing
     * We broadcast it to the Super Admin dashboard
     */
    @MessageMapping("/shop/typing")
    public void handleShopTyping(@Payload TypingDTO typing) {
        // Broadcast to the Admin's global support monitor
        messagingTemplate.convertAndSend("/topic/admin/typing", typing);
    }
}