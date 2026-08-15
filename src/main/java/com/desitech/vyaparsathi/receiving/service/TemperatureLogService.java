package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.receiving.entity.TemperatureLog;
import com.desitech.vyaparsathi.receiving.repository.TemperatureLogRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TemperatureLogService {

    private final TemperatureLogRepository repository;
    private final ShopRepository shopRepository;

    public TemperatureLogService(TemperatureLogRepository repository, ShopRepository shopRepository) {
        this.repository = repository;
        this.shopRepository = shopRepository;
    }

    @Transactional
    public TemperatureLog record(TemperatureLog payload, BigDecimal minC, BigDecimal maxC) {
        if (payload.getShop() == null) {
            Long shopId = TenantUtils.getCurrentShopId();
            if (shopId != null) shopRepository.findById(shopId).ifPresent(payload::setShop);
        }
        if (minC != null && maxC != null && payload.getTemperatureC() != null) {
            BigDecimal t = payload.getTemperatureC();
            payload.setWithinSpec(t.compareTo(minC) >= 0 && t.compareTo(maxC) <= 0);
        }
        return repository.save(payload);
    }

    @Transactional(readOnly = true)
    public List<TemperatureLog> listForReceiving(Long receivingId) {
        return repository.findByReceivingIdOrderByReadingAtDesc(receivingId);
    }
}
