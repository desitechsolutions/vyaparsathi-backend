package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.inventory.entity.SavedView;
import com.desitech.vyaparsathi.inventory.repository.SavedViewRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SavedViewService {

    private final SavedViewRepository repository;
    private final ShopRepository shopRepository;

    public SavedViewService(SavedViewRepository repository, ShopRepository shopRepository) {
        this.repository = repository;
        this.shopRepository = shopRepository;
    }

    @Transactional
    public SavedView save(SavedView payload) {
        if (payload.getShop() == null) {
            Long shopId = TenantUtils.getCurrentShopId();
            if (shopId != null) shopRepository.findById(shopId).ifPresent(payload::setShop);
        }
        return repository.save(payload);
    }

    @Transactional(readOnly = true)
    public List<SavedView> list(Long userId, String surface) {
        return repository.findByUserIdAndSurface(userId, surface);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Saved view not found: " + id);
        }
        repository.deleteById(id);
    }
}
