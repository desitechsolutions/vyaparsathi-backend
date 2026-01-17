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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
        shop = shopRepository.saveAndFlush(shop);

        TenantContext.setCurrentShopId(shop.getId());
        // 2. Assign shop to user & upgrade role
        try {
            // Assign to user
            currentUser.setShop(shop);
            currentUser.setRole(Role.OWNER);
            userRepository.save(currentUser);

            // Seed categories (listener will now auto-set shop if needed)
            seedDefaultClothingCategories(shop);
        }
        finally {
            // Always clear after seeding
            TenantContext.clear();
        }

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

        // ────────────────────────────────────────────────────────────────
        // Allow onboarding users (PENDING_OWNER) to have no shop context
        // ────────────────────────────────────────────────────────────────
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (currentShopId == null) {
            if (auth != null && auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_PENDING_OWNER".equals(a.getAuthority()))) {

                logger.info("No shop context for PENDING_OWNER – returning null (onboarding flow)");
                return null;  // ← Return null instead of throwing
            }

            // For all other users: strict enforcement
            throw new IllegalStateException("No active shop context");
        }

        // Normal case: shop exists
        return shopRepository.findById(currentShopId)
                .map(shopMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Shop not found"));
    }

    private void seedDefaultClothingCategories(Shop shop) {
        if (shop.getId() == null) {
            throw new IllegalStateException("Shop has no ID during seeding");
        }

        logger.info("Seeding default categories for shop {}", shop.getId());

        // Root categories
        Category men = createCategoryManually("MEN", null, shop);
        Category women = createCategoryManually("WOMEN", null, shop);
        Category kids = createCategoryManually("KIDS", null, shop);
        Category unisex = createCategoryManually("UNISEX / ACCESSORIES", null, shop);

        // MEN sub-categories
        createCategoryManually("CASUAL", men, shop);
        createCategoryManually("FORMAL", men, shop);
        createCategoryManually("ETHNIC / FESTIVE", men, shop);
        createCategoryManually("ACTIVEWEAR / SPORTS", men, shop);
        createCategoryManually("INNERWEAR", men, shop);

        // WOMEN sub-categories
        createCategoryManually("ETHNIC / SAREES", women, shop);
        createCategoryManually("WESTERN / FUSION", women, shop);
        createCategoryManually("KURTIS & TOPS", women, shop);
        createCategoryManually("LEGGINGS & BOTTOMS", women, shop);

        // KIDS sub-categories
        createCategoryManually("BOYS", kids, shop);
        createCategoryManually("GIRLS", kids, shop);
        createCategoryManually("INFANT / TODDLER", kids, shop);

        // Unisex / Accessories
        createCategoryManually("FOOTWEAR", unisex, shop);
        createCategoryManually("BAGS & WALLETS", unisex, shop);
        createCategoryManually("JEWELLERY & WATCHES", unisex, shop);

        logger.info("Seeded default categories for shop {}", shop.getId());
    }

    private Category createCategoryManually(String name, Category parent, Shop shop) {
        Long shopId = shop.getId();

        // Optional idempotent check (can be skipped during onboarding if you want, but it's fine)
        if (categoryRepository.existsByNameAndShopId(name, shopId)) {
            logger.debug("Category '{}' already exists for shop {}", name, shopId);
            return categoryRepository.findByNameAndShopId(name, shopId).orElseThrow();
        }

        Category category = new Category();
        category.setName(name);
        category.setShop(shop);  // Explicitly set shop to avoid NULL
        if (parent != null) {
            category.setParent(parent);
        }

        return categoryRepository.save(category);
    }

/*    private Category createCategory(String name, Category parent, Shop shop) {
        Long shopId = shop.getId();

        // 1. Idempotent check (fast existence check)
        if (categoryRepository.existsByNameAndShop(name, shop)) {
            logger.debug("Category '{}' already exists for shop {}", name, shopId);
            // Return existing (fetch only when needed)
            return categoryRepository.findByNameAndShop(name, shop)
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
    }*/
}
