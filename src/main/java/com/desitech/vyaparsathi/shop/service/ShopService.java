package com.desitech.vyaparsathi.shop.service;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.model.Role;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.inventory.dto.CategoryCreateDto;
import com.desitech.vyaparsathi.inventory.dto.CategoryDto;
import com.desitech.vyaparsathi.inventory.entity.Category;
import com.desitech.vyaparsathi.inventory.mapper.CategoryMapper;
import com.desitech.vyaparsathi.inventory.repository.CategoryRepository;
import com.desitech.vyaparsathi.inventory.service.CategoryService;
import com.desitech.vyaparsathi.shop.dto.ShopDto;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.mapper.ShopMapper;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class ShopService {

    private static final Logger logger = LoggerFactory.getLogger(ShopService.class);
    @Autowired private ShopRepository shopRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryService categoryService; // ← reuse this
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ShopMapper shopMapper;
    @Autowired private CategoryMapper categoryMapper;

    @Transactional
    public ShopDto completeOnboarding(ShopDto dto, User currentUser) {
        if (currentUser.getShop() != null) {
            throw new IllegalStateException("Shop already exists for this user");
        }

        // Validate unique shop code
        if (shopRepository.existsByCode(dto.getCode())) {
            throw new IllegalArgumentException("Shop code '" + dto.getCode() + "' is already in use");
        }

        // 1. Create shop
        Shop shop = shopMapper.toEntity(dto);
        shop = shopRepository.save(shop);

        // 2. Assign shop to user & upgrade role
        currentUser.setShop(shop);
        currentUser.setRole(Role.OWNER);
        userRepository.save(currentUser);

        // 3. Seed default clothing categories
        seedDefaultClothingCategories(shop);

        logger.info("Onboarding completed - Shop created: id={}, name={}", shop.getId(), shop.getName());

        return shopMapper.toDto(shop);
    }
    @Transactional
    public ShopDto updateShop(ShopDto dto) {
        // Get current shop from security context
        Long currentShopId = TenantContext.getCurrentShopId();
        if (currentShopId == null) {
            throw new IllegalStateException("No active shop context");
        }

        Shop shop = shopRepository.findById(currentShopId)
                .orElseThrow(() -> new EntityNotFoundException("Shop not found"));

        shopMapper.updateShopFromDto(dto, shop);
        shop = shopRepository.save(shop);
        return shopMapper.toDto(shop);
    }

    public ShopDto getShop() {
        Long currentShopId = TenantContext.getCurrentShopId();
        if (currentShopId == null) {
            throw new IllegalStateException("No active shop context");
        }

        return shopRepository.findById(currentShopId)
                .map(shopMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Shop not found"));
    }

    private void seedDefaultClothingCategories(Shop shop) {
        Long shopId = shop.getId();

        // Skip if already seeded (idempotent)
        if (categoryService.getAllCategories().stream().anyMatch(c -> c.getShopId().equals(shopId))) {
            logger.info("Categories already seeded for shop: {}", shopId);
            return;
        }

        // Root categories
        Category men = createCategory("MEN", null, shop);
        Category women = createCategory("WOMEN", null, shop);
        Category kids = createCategory("KIDS", null, shop);
        Category unisex = createCategory("UNISEX / ACCESSORIES", null, shop);

        // MEN sub-categories
        createCategory("CASUAL", men, shop);
        createCategory("FORMAL", men, shop);
        createCategory("ETHNIC / FESTIVE", men, shop);
        createCategory("ACTIVEWEAR / SPORTS", men, shop);
        createCategory("INNERWEAR", men, shop);

        // WOMEN sub-categories
        createCategory("ETHNIC / SAREES", women, shop);
        createCategory("WESTERN / FUSION", women, shop);
        createCategory("KURTIS & TOPS", women, shop);
        createCategory("LEGGINGS & BOTTOMS", women, shop);

        // KIDS sub-categories
        createCategory("BOYS", kids, shop);
        createCategory("GIRLS", kids, shop);
        createCategory("INFANT / TODDLER", kids, shop);

        // Unisex / Accessories
        createCategory("FOOTWEAR", unisex, shop);
        createCategory("BAGS & WALLETS", unisex, shop);
        createCategory("JEWELLERY & WATCHES", unisex, shop);

        logger.info("Seeded {} default clothing categories for shop: {}",
                categoryService.getAllCategories().stream().filter(c -> c.getShopId().equals(shopId)).count(),
                shopId);
    }

    private Category createCategory(String name, Category parent, Shop shop) {
        Long shopId = shop.getId();

        // 1. Idempotent check (fast existence check)
        if (categoryRepository.existsByNameAndShopId(name, shopId)) {
            logger.debug("Category '{}' already exists for shop {}", name, shopId);
            // Return existing (fetch only when needed)
            return categoryRepository.findByNameAndShopId(name, shopId)
                    .orElseThrow(() -> new IllegalStateException("Category existence check failed after positive result"));
        }

        // 2. Prepare DTO for creation
        CategoryCreateDto dto = CategoryCreateDto.builder()
                .name(name)
                .parentId(parent != null ? parent.getId() : null)
                .build();

        // 3. Create via service (handles mapping, shop assignment via listener, validation)
        CategoryDto savedDto = categoryService.createCategory(dto);

        // 4. Convert back to entity if caller needs full entity (optional)
        //    - If you only need ID or basic info, return DTO instead
        Category savedEntity = categoryMapper.toEntity(savedDto);

        logger.info("Created category: name='{}', id={}, parentId={}, shopId={}",
                name, savedEntity.getId(), parent != null ? parent.getId() : null, shopId);

        return savedEntity;
    }
}
