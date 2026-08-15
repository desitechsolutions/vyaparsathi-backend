package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.inventory.entity.ProductBundle;
import com.desitech.vyaparsathi.inventory.entity.ProductBundleComponent;
import com.desitech.vyaparsathi.inventory.repository.ProductBundleRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * CRUD for product bundles / kits. When a bundle-parent variant is sold,
 * callers should look up the bundle definition and deduct each component
 * qty × sold-qty from the underlying variants. This service exposes the
 * definition; the deduct hook lives in the sales module.
 */
@Service
public class ProductBundleService {

    private final ProductBundleRepository repository;
    private final ShopRepository shopRepository;
    private final StockService stockService;

    public ProductBundleService(ProductBundleRepository repository,
                                ShopRepository shopRepository,
                                StockService stockService) {
        this.repository = repository;
        this.shopRepository = shopRepository;
        this.stockService = stockService;
    }

    @Transactional
    public ProductBundle create(Long bundleVariantId, String name, List<Map<String, Object>> components, String notes) {
        if (bundleVariantId == null) throw new BusinessValidationException("bundleVariantId is required.");
        ProductBundle bundle = new ProductBundle();
        bundle.setBundleVariantId(bundleVariantId);
        bundle.setBundleName(name);
        bundle.setActive(true);
        bundle.setNotes(notes);
        Long shopId = TenantUtils.getCurrentShopId();
        if (shopId != null) shopRepository.findById(shopId).ifPresent(bundle::setShop);
        for (Map<String, Object> c : components) {
            ProductBundleComponent comp = new ProductBundleComponent();
            comp.setProductBundle(bundle);
            comp.setComponentVariantId(Long.valueOf(c.get("componentVariantId").toString()));
            comp.setQuantity(new BigDecimal(c.get("quantity").toString()));
            comp.setUnit(c.get("unit") != null ? c.get("unit").toString() : null);
            comp.setOptionalFlag(Boolean.parseBoolean(String.valueOf(c.getOrDefault("optionalFlag", false))));
            bundle.getComponents().add(comp);
        }
        return repository.save(bundle);
    }

    @Transactional(readOnly = true)
    public List<ProductBundle> listAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public ProductBundle get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bundle not found: " + id));
    }

    @Transactional
    public ProductBundle deactivate(Long id) {
        ProductBundle b = get(id);
        b.setActive(false);
        return repository.save(b);
    }

    /**
     * Deducts each component's (qty × soldQty) from stock. Called from the
     * sales module when a bundle variant is sold.
     */
    @Transactional
    public void deductComponents(Long bundleVariantId, BigDecimal soldQty, String reference) {
        ProductBundle b = repository.findByBundleVariantId(bundleVariantId).orElse(null);
        if (b == null || !b.isActive()) return;
        for (ProductBundleComponent comp : b.getComponents()) {
            BigDecimal deduct = comp.getQuantity().multiply(soldQty);
            stockService.deductStock(comp.getComponentVariantId(), deduct,
                    "Bundle sale: " + b.getBundleName(), reference);
        }
    }
}
