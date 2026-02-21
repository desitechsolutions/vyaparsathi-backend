package com.desitech.vyaparsathi.shop.service;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.model.Role;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.inventory.entity.Category;
import com.desitech.vyaparsathi.inventory.repository.CategoryRepository;
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
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ShopMapper shopMapper;

    @Transactional
    public ShopDto completeOnboarding(ShopDto dto, User currentUser, MultipartFile logo) {
        if (currentUser.getShop() != null) {
            throw new IllegalStateException("Shop already exists for this user");
        }

        if (shopRepository.existsByCode(dto.getCode())) {
            throw new IllegalArgumentException("Shop code '" + dto.getCode() + "' is already in use");
        }

        Shop shop = shopMapper.toEntity(dto);

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

        TenantContext.setCurrentShopId(shop.getId());
        try {
            currentUser.setShop(shop);
            currentUser.setRole(Role.OWNER);
            userRepository.save(currentUser);

            // Seed categories with Industry as Root
            seedDefaultCategories(shop, dto.getIndustryType());
        } finally {
            TenantContext.clear();
        }

        logger.info("Onboarding completed - Shop created: id={}, name={}, industry={}",
                shop.getId(), shop.getName(), dto.getIndustryType());

        return shopMapper.toDto(shop);
    }

    private void seedDefaultCategories(Shop shop, String industryType) {
        if (shop.getId() == null) throw new IllegalStateException("Shop has no ID");

        String type = (industryType == null) ? "GENERAL" : industryType.toUpperCase();
        logger.info("Seeding categories for industry: {} in shop {}", type, shop.getId());

        // Create the Root Industry Node (CRITICAL for UI detection)
        Category root = createCategoryManually(type, null, shop);

        switch (type) {
            case "CLOTHING":
                Category men = createCategoryManually("MEN", root, shop);
                Category women = createCategoryManually("WOMEN", root, shop);
                Category kids = createCategoryManually("KIDS", root, shop);

                // Sub-categories for Men
                createCategoryManually("MEN CASUAL", men, shop);
                createCategoryManually("MEN FORMAL", men, shop);

                // Sub-categories for Women
                createCategoryManually("WOMEN ETHNIC", women, shop);
                createCategoryManually("WOMEN WESTERN", women, shop);

                // Sub-categories for Kids
                createCategoryManually("BOYS", kids, shop);
                createCategoryManually("GIRLS", kids, shop);

                createCategoryManually("FOOTWEAR", root, shop);
                break;

            case "ELECTRONICS":
                createCategoryManually("MOBILES & TABLETS", root, shop);
                createCategoryManually("LAPTOPS & COMPUTERS", root, shop);
                createCategoryManually("HOME APPLIANCES", root, shop);
                createCategoryManually("AUDIO & ACCESSORIES", root, shop);
                createCategoryManually("WEARABLES", root, shop);
                break;

            case "HARDWARE":
                createCategoryManually("ELECTRICALS", root, shop);
                createCategoryManually("PLUMBING", root, shop);
                createCategoryManually("PAINTS", root, shop);
                createCategoryManually("TOOLS & FASTENERS", root, shop);
                break;

            case "PHARMACY":
                createCategoryManually("MEDICINES", root, shop);
                createCategoryManually("PERSONAL CARE", root, shop);
                createCategoryManually("SURGICALS", root, shop);
                createCategoryManually("WELLNESS", root, shop);
                break;

            case "GROCERY":
                createCategoryManually("DAIRY & BAKERY", root, shop);
                createCategoryManually("STAPLES", root, shop);
                createCategoryManually("SNACKS & BEVERAGES", root, shop);
                createCategoryManually("HOUSEHOLD CARE", root, shop);
                break;

            case "AUTOMOBILE":
                createCategoryManually("SPARE PARTS", root, shop);
                createCategoryManually("LUBRICANTS", root, shop);
                createCategoryManually("TYRES", root, shop);
                createCategoryManually("ACCESSORIES", root, shop);
                break;

            case "STATIONERY":
                createCategoryManually("OFFICE SUPPLIES", root, shop);
                createCategoryManually("SCHOOL SUPPLIES", root, shop);
                createCategoryManually("ART & CRAFT", root, shop);
                break;

            case "FOOTWEAR":
                createCategoryManually("SPORTS FOOTWEAR", root, shop);
                createCategoryManually("FORMAL FOOTWEAR", root, shop);
                createCategoryManually("CASUAL FOOTWEAR", root, shop);
                break;

            case "FURNITURE":
                createCategoryManually("OFFICE FURNITURE", root, shop);
                createCategoryManually("HOME FURNITURE", root, shop);
                createCategoryManually("FURNISHINGS", root, shop);
                break;

            case "JEWELLERY":
                createCategoryManually("GOLD", root, shop);
                createCategoryManually("SILVER", root, shop);
                createCategoryManually("FASHION JEWELLERY", root, shop);
                break;

            default: // GENERAL
                createCategoryManually("OTHERS", root, shop);
                createCategoryManually("MISC", root, shop);
                break;
        }
    }

    private Category createCategoryManually(String name, Category parent, Shop shop) {
        return categoryRepository.findByNameAndShopId(name, shop.getId())
                .orElseGet(() -> {
                    Category category = new Category();
                    category.setName(name);
                    category.setShop(shop);
                    category.setParent(parent);
                    return categoryRepository.save(category);
                });
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


    public boolean existsByCode(String code) {
        Long count = shopRepository.countByCodeGlobal(code);
        return count != null && count > 0;
    }
}