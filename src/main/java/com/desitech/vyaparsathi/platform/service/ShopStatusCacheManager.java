package com.desitech.vyaparsathi.platform.service;

import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShopStatusCacheManager {

    private final ShopRepository shopRepository;
    private final Map<Long, ShopStatusEntry> cache = new ConcurrentHashMap<>();

    public boolean isShopActive(Long shopId) {
        if (shopId == null) return true;

        LocalDateTime now = LocalDateTime.now();
        ShopStatusEntry entry = cache.get(shopId);

        // Check cache hit (15s TTL)
        if (entry != null && entry.cachedAt().plusSeconds(15).isAfter(now)) {
            return entry.active();
        }

        // Cache miss: query database
        Optional<Shop> shopOpt = shopRepository.findById(shopId);
        boolean active = shopOpt.map(Shop::getActive).orElse(false);
        cache.put(shopId, new ShopStatusEntry(active, now));
        return active;
    }

    public void evictShop(Long shopId) {
        if (shopId != null) {
            cache.remove(shopId);
            log.info("Evicted shop active status cache for shopId={}", shopId);
        }
    }

    private record ShopStatusEntry(boolean active, LocalDateTime cachedAt) {}
}
