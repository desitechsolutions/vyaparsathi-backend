package com.desitech.vyaparsathi.support.controller;


import com.desitech.vyaparsathi.support.dto.ChatSummaryDTO;
import com.desitech.vyaparsathi.support.dto.SupportMessage;
import com.desitech.vyaparsathi.support.entity.ChatMessageEntity;
import com.desitech.vyaparsathi.support.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/support")
public class SupportRestController {

    @Autowired
    private ChatService chatService;

    /**
     * For Shop Owners: Get my own chat history.
     * Accessible by: SHOP_OWNER, ADMIN (Shop level)
     */
    @GetMapping("/history")
    public ResponseEntity<List<SupportMessage>> getMyChatHistory() {
        // The service uses findAll... which is automatically filtered by shopId
        List<SupportMessage> history = chatService.getShopChatHistory();
        return ResponseEntity.ok(history);
    }

    /**
     * For Super Admin: Get history of any specific shop.
     * Accessible by: SUPER_ADMIN only
     */
    @GetMapping("/history/{shopId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<SupportMessage>> getShopHistoryForAdmin(@PathVariable Long shopId) {
        // Uses the native query we wrote to bypass filters
        List<SupportMessage> history = chatService.getAdminViewHistory(shopId);
        return ResponseEntity.ok(history);
    }

    /**
     * Optional: Mark messages as read when Admin opens the chat
     */
    @PostMapping("/mark-read/{shopId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> markAsRead(@PathVariable Long shopId) {
        // This is where we trigger the logic to flip isReadByAdmin to true
        chatService.markMessagesAsRead(shopId);
        return ResponseEntity.ok().build();
    }

    /**
     * For Super Admin: Get a list of all unique shops that have started a chat.
     */
    @GetMapping("/admin/conversations")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<ChatSummaryDTO>> getAllConversations() {
        // This calls a service method to get unique shopIds + their last message
        List<ChatSummaryDTO> summaries = chatService.getAllChatSummaries();
        return ResponseEntity.ok(summaries);
    }
}