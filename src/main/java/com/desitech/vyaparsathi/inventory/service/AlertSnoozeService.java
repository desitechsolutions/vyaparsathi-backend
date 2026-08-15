package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.inventory.entity.AlertSnooze;
import com.desitech.vyaparsathi.inventory.repository.AlertSnoozeRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlertSnoozeService {

    private final AlertSnoozeRepository repository;
    private final ShopRepository shopRepository;

    public AlertSnoozeService(AlertSnoozeRepository repository, ShopRepository shopRepository) {
        this.repository = repository;
        this.shopRepository = shopRepository;
    }

    @Transactional
    public AlertSnooze snooze(Long userId, String alertType, String alertKey, LocalDateTime until) {
        AlertSnooze existing = repository
                .findByUserIdAndAlertTypeAndAlertKey(userId, alertType, alertKey)
                .orElse(null);
        AlertSnooze row = existing != null ? existing : new AlertSnooze();
        row.setUserId(userId);
        row.setAlertType(alertType);
        row.setAlertKey(alertKey);
        row.setSnoozedUntil(until);
        if (row.getShop() == null) {
            Long shopId = TenantUtils.getCurrentShopId();
            if (shopId != null) shopRepository.findById(shopId).ifPresent(row::setShop);
        }
        return repository.save(row);
    }

    @Transactional(readOnly = true)
    public List<AlertSnooze> listActive(Long userId, String alertType) {
        return repository.findByUserIdAndAlertType(userId, alertType).stream()
                .filter(s -> s.getSnoozedUntil() != null && s.getSnoozedUntil().isAfter(LocalDateTime.now()))
                .toList();
    }
}
