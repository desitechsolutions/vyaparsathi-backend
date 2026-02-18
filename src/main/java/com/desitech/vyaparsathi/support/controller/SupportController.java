package com.desitech.vyaparsathi.support.controller;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.support.dto.SupportMessage;
import com.desitech.vyaparsathi.support.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
public class SupportController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ChatService chatService;

    /**
     * Triggered when a SHOP OWNER sends a message to /app/support.contact
     * The frontend sends: { message: "Hello", shopId: 101, shopName: "Rakesh Electronics" }
     */
    @MessageMapping("/support.contact")
    public void handleShopMessage(
            @Payload SupportMessage message,
            SimpMessageHeaderAccessor headerAccessor
    ) {

        Long shopId = (Long) headerAccessor
                .getSessionAttributes()
                .get("shopId");

        try {
            TenantContext.setCurrentShopId(shopId);

            message.setTimestamp(LocalDateTime.now());
            message.setFromAdmin(false);
            message.setShopId(shopId);

            SupportMessage saved = chatService.saveMessage(message);

            messagingTemplate.convertAndSend("/topic/admin/support", saved);
            messagingTemplate.convertAndSend("/topic/shop/" + shopId + "/notifications", saved);

        } finally {
            TenantContext.clear();
        }
    }
    /**
     * Triggered when SUPER_ADMIN sends a message to /app/support.reply.{shopId}
     * The admin UI calls: stompClient.send("/app/support.reply.101", {}, ...)
     */
    @MessageMapping("/support.reply.{shopId}")
    public void handleAdminReply(
            @DestinationVariable Long shopId,
            @Payload SupportMessage message
    ) {

        try {
            TenantContext.setCurrentShopId(shopId);

            message.setTimestamp(LocalDateTime.now());
            message.setFromAdmin(true);
            message.setShopId(shopId);

            SupportMessage saved = chatService.saveMessage(message);

            messagingTemplate.convertAndSend("/topic/shop/" + shopId + "/notifications", saved);

        } finally {
            TenantContext.clear();
        }
    }

}