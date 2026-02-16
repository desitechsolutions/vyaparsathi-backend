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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;


@Service
public class ShopService {

    private static final Logger logger = LoggerFactory.getLogger(ShopService.class);
    private final String uploadDir = "uploads/logos/";
    @Autowired private ShopRepository shopRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryService categoryService; // ← reuse this
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ShopMapper shopMapper;
    @Autowired private CategoryMapper categoryMapper;

    @Transactional
    public ShopDto completeOnboarding(ShopDto dto, User currentUser, MultipartFile logo) {
        if (currentUser.getShop() != null) {
            throw new IllegalStateException("Shop already exists for this user");
        }

        if (shopRepository.existsByCode(dto.getCode())) {
            throw new IllegalArgumentException("Shop code '" + dto.getCode() + "' is already in use");
        }

        // 1. Create shop
        Shop shop = shopMapper.toEntity(dto);

        // Handle Logo Storage
        if (logo != null && !logo.isEmpty()) {
            String fileName = UUID.randomUUID().toString() + "_" + logo.getOriginalFilename();
            try {
                Path path = Paths.get(uploadDir);
                if (!Files.exists(path)) Files.createDirectories(path);
                Files.copy(logo.getInputStream(), path.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                shop.setLogoPath(fileName);
            } catch (IOException e) {
                logger.error("Failed to save shop logo", e);
                throw new RuntimeException("Could not save logo file", e);
            }
        }

        shop = shopRepository.saveAndFlush(shop);

        // 2. Set Context and Seed
        TenantContext.setCurrentShopId(shop.getId());
        try {
            currentUser.setShop(shop);
            currentUser.setRole(Role.OWNER);
            userRepository.save(currentUser);

            // Seed based on selected industry
            seedDefaultCategories(shop, dto.getIndustryType());
        } finally {
            // ALWAYS clear to prevent context pollution
            TenantContext.clear();
        }

        logger.info("Onboarding completed - Shop created: id={}, name={}, industry={}",
                shop.getId(), shop.getName(), dto.getIndustryType());

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

    private void seedDefaultCategories(Shop shop, String industryType) {
        if (shop.getId() == null) throw new IllegalStateException("Shop has no ID");

        logger.info("Seeding categories for industry: {} in shop {}", industryType, shop.getId());

        if (industryType == null) industryType = "GENERAL";

        switch (industryType.toUpperCase()) {
            case "CLOTHING":
                Category men = createCategoryManually("MEN", null, shop);
                Category women = createCategoryManually("WOMEN", null, shop);
                createCategoryManually("CASUAL", men, shop);
                createCategoryManually("FORMAL", men, shop);
                createCategoryManually("ETHNIC / SAREES", women, shop);
                createCategoryManually("FOOTWEAR", null, shop);
                break;

            case "ELECTRONICS":
                Category electronics = createCategoryManually("ELECTRONICS", null, shop);
                createCategoryManually("MOBILE & ACCESSORIES", electronics, shop);
                createCategoryManually("LAPTOPS & COMPUTING", electronics, shop);
                createCategoryManually("HOME APPLIANCES", electronics, shop);
                createCategoryManually("AUDIO & WEARABLES", electronics, shop);
                break;

            case "HARDWARE":
                Category hardware = createCategoryManually("HARDWARE", null, shop);
                createCategoryManually("ELECTRICAL FITTINGS", hardware, shop);
                createCategoryManually("PLUMBING & SANITARY", hardware, shop);
                createCategoryManually("PAINTS & ADHESIVES", hardware, shop);
                createCategoryManually("HAND & POWER TOOLS", hardware, shop);
                break;

            default: // GENERAL
                Category others = createCategoryManually("OTHERS", null, shop);
                createCategoryManually("STATIONERY", others, shop);
                createCategoryManually("TOYS & GAMES", others, shop);
                createCategoryManually("HOME DECOR", others, shop);
                break;
        }
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
public boolean existsByCode(String code) {
    Long count = shopRepository.countByCodeGlobal(code);
    return count != null && count > 0;
}
}
