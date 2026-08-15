package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.inventory.entity.SupplierRateCard;
import com.desitech.vyaparsathi.inventory.repository.SupplierRateCardRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class SupplierRateCardService {

    private final SupplierRateCardRepository repository;
    private final ShopRepository shopRepository;

    public SupplierRateCardService(SupplierRateCardRepository repository,
                                   ShopRepository shopRepository) {
        this.repository = repository;
        this.shopRepository = shopRepository;
    }

    @Transactional
    public SupplierRateCard save(SupplierRateCard payload) {
        if (payload.getShop() == null) {
            Long shopId = TenantUtils.getCurrentShopId();
            if (shopId != null) shopRepository.findById(shopId).ifPresent(payload::setShop);
        }
        if (payload.getValidFrom() == null) payload.setValidFrom(LocalDate.now());
        return repository.save(payload);
    }

    @Transactional(readOnly = true)
    public List<SupplierRateCard> listBySupplier(Long supplierId) {
        return repository.findBySupplierId(supplierId);
    }

    @Transactional(readOnly = true)
    public Optional<SupplierRateCard> activeRate(Long supplierId, Long variantId) {
        return repository.findFirstBySupplierIdAndItemVariantIdAndValidFromLessThanEqualOrderByValidFromDesc(
                supplierId, variantId, LocalDate.now())
                .filter(r -> r.getValidTo() == null || !r.getValidTo().isBefore(LocalDate.now()));
    }
}
