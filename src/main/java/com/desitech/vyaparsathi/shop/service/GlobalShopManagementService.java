package com.desitech.vyaparsathi.shop.service;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.shop.dto.GlobalShopSummaryDTO;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import com.desitech.vyaparsathi.subscriptions.entity.Subscription;
import com.desitech.vyaparsathi.subscriptions.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GlobalShopManagementService {

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public Page<GlobalShopSummaryDTO> getAllShopsForDashboard(Pageable pageable) {
        return shopRepository.findAllShopSummaries(pageable);
    }

    private GlobalShopSummaryDTO mapToSummaryDTO(Shop shop) {
        // Find the owner (Assuming first user registered for the shop or based on Role)
        User owner = userRepository.findFirstByShopOrderByCreatedAtAsc(shop).orElse(null);

        // Find subscription
        Subscription sub = subscriptionRepository.findByShopId(shop.getId()).orElse(null);

        return GlobalShopSummaryDTO.builder()
                .shopId(shop.getId())
                .shopName(shop.getName())
                .shopCode(shop.getCode())
                .state(shop.getState())
                .createdAt(shop.getCreatedAt())
                .ownerName(owner != null ? owner.getFirstName() + " " + owner.getLastName() : "No Owner")
                .ownerEmail(owner != null ? owner.getEmail() : "N/A")
                .currentTier(sub != null ? sub.getTier() : null)
                .subscriptionStatus(sub != null ? sub.getStatus() : null)
                .expiryDate(sub != null ? sub.getEndDate() : null)
                .build();
    }

    @Transactional
    public void toggleShopStatus(Long shopId, boolean active) {
        List<User> users = userRepository.findByShopId(shopId);
        if (users.isEmpty()) throw new ResourceNotFoundException("No users found for this shop");

        users.forEach(u -> u.setActive(active));
        userRepository.saveAll(users);
    }
}