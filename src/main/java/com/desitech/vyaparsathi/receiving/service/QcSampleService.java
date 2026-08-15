package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.receiving.entity.QcSample;
import com.desitech.vyaparsathi.receiving.repository.QcSampleRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class QcSampleService {

    private final QcSampleRepository repository;
    private final ShopRepository shopRepository;

    public QcSampleService(QcSampleRepository repository, ShopRepository shopRepository) {
        this.repository = repository;
        this.shopRepository = shopRepository;
    }

    @Transactional
    public QcSample record(QcSample payload, BigDecimal aqlThresholdPct) {
        if (payload.getShop() == null) {
            Long shopId = TenantUtils.getCurrentShopId();
            if (shopId != null) shopRepository.findById(shopId).ifPresent(payload::setShop);
        }
        int sample = payload.getSampleSize() != null ? payload.getSampleSize() : 0;
        int defects = payload.getDefectsFound() != null ? payload.getDefectsFound() : 0;
        if (sample > 0) {
            BigDecimal aql = BigDecimal.valueOf(defects)
                    .divide(BigDecimal.valueOf(sample), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(3, RoundingMode.HALF_UP);
            payload.setAqlPct(aql);
            if (aqlThresholdPct != null) {
                payload.setVerdict(aql.compareTo(aqlThresholdPct) <= 0 ? "PASS" : "FAIL");
            } else if (payload.getVerdict() == null || "PENDING".equals(payload.getVerdict())) {
                payload.setVerdict(defects == 0 ? "PASS" : "PENDING");
            }
        }
        return repository.save(payload);
    }

    @Transactional(readOnly = true)
    public List<QcSample> listForReceiving(Long receivingId) {
        return repository.findByReceivingId(receivingId);
    }
}
