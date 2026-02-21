package com.desitech.vyaparsathi.support.service;

import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.support.dto.ChatSummaryDTO;
import com.desitech.vyaparsathi.support.dto.SupportMessage;
import com.desitech.vyaparsathi.support.entity.ChatMessageEntity;
import com.desitech.vyaparsathi.support.mapper.SupportMessageMapper;
import com.desitech.vyaparsathi.support.repository.ChatRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SupportMessageMapper messageMapper;

    /**
     * Saves a message from either Admin or Shop Owner
     */
    @Transactional
    public SupportMessage saveMessage(SupportMessage dto) {

        ChatMessageEntity entity = new ChatMessageEntity();

        // ---------- Shop Mapping ----------
        if (dto.getShopId() != null) {
            Shop shopProxy = entityManager.getReference(Shop.class, dto.getShopId());
            entity.setShop(shopProxy);
        }

        // ---------- Message Data ----------
        entity.setSenderName(dto.getSenderName());
        entity.setMessage(dto.getMessage());
        entity.setFromAdmin(dto.isFromAdmin());

        // Always set timestamp here (single source of truth)

        ChatMessageEntity saved = chatRepository.save(entity);

        // ---------- Convert Back To DTO ----------
        SupportMessage response = new SupportMessage();
        response.setId(saved.getId());
        response.setMessage(saved.getMessage());
        response.setSenderName(saved.getSenderName());
        response.setFromAdmin(saved.isFromAdmin());
        response.setTimestamp(saved.getCreatedAt());
        response.setShopId(saved.getShop().getId());

        return response;
    }


    /**
     * For Shop Owners: Fetches history for the current tenant.
     * The TenantContext filter will automatically restrict this to their shopId.
     */
    public List<SupportMessage> getShopChatHistory() {
        return chatRepository.findAllByOrderByCreatedAtAsc().stream().map(messageMapper::toDto).toList();
    }

    /**
     * For Super Admin: Fetches history for any specific shop.
     * Note: If you use Hibernate Filters, you may need to disable the filter
     * in the EntityManager before calling this, or use a Native Query.
     */
    public List<SupportMessage> getAdminViewHistory(Long targetShopId) {
        return chatRepository.findByShopIdNative(targetShopId).stream().map(messageMapper::toDto).toList();
    }

    public List<ChatSummaryDTO> getAllChatSummaries() {
        logger.info("Fetching all chat summaries for Super Admin view");

        // 1. Fetch the latest message from every shop using native query
        List<ChatMessageEntity> latestMessages = chatRepository.findLatestMessagesPerShop();

        if (latestMessages == null || latestMessages.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. Map entities to DTOs
        return latestMessages.stream().map(msg -> {
            ChatSummaryDTO dto = new ChatSummaryDTO();

            dto.setShopId(msg.getShop().getId());
            dto.setShopName(msg.getShop().getName() != null ? msg.getShop().getName() : "Unknown Shop");
            dto.setLastMessage(msg.getMessage());
            dto.setTimestamp(msg.getCreatedAt());

            boolean unreadExists = chatRepository.existsByShopIdAndIsFromAdminFalseAndIsReadByAdminFalse(msg.getShop().getId());
            dto.setHasUnread(unreadExists);

            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void markMessagesAsRead(Long shopId) {
        chatRepository.markAllAsReadNative(shopId);
    }
}