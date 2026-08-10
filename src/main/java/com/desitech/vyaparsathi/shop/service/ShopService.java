package com.desitech.vyaparsathi.shop.service;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.model.Role;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.auth.service.RefreshTokenService;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.common.util.FileStorageService;
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
    @Autowired private JwtUtil jwtUtil;
    @Autowired private RefreshTokenService refreshTokenService;

    @Autowired
    private FileStorageService fileStorageService;

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
            try {
                UUID contextUuid = UUID.randomUUID();
                String logoUrl = fileStorageService.storeFile(logo, "logos", contextUuid);
                shop.setLogoPath(logoUrl);
            } catch (Exception e) {
                logger.error("Failed to save shop logo during onboarding", e);
                throw new RuntimeException("Could not save logo file: " + e.getMessage(), e);
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

        String newJwtToken = jwtUtil.generateAccessToken(currentUser,shop.getId());
        String refreshToken = refreshTokenService.createRefreshToken(currentUser.getUsername()).getToken();
        logger.info("Onboarding completed - Shop created: id={}, name={}, industry={}",
                shop.getId(), shop.getName(), dto.getIndustryType());

        ShopDto shopDto = shopMapper.toDto(shop);
        shopDto.setAccessToken(newJwtToken);
        shopDto.setRefreshToken(refreshToken);
        return shopDto;
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
                Category medicines = createCategoryManually("MEDICINES", root, shop);
                createCategoryManually("TABLETS & CAPSULES", medicines, shop);
                createCategoryManually("SYRUPS & LIQUIDS", medicines, shop);
                createCategoryManually("INJECTIONS & DROPS", medicines, shop);
                createCategoryManually("OINTMENTS & CREAMS", medicines, shop);
                createCategoryManually("ANTIBIOTICS", medicines, shop);
                createCategoryManually("VITAMINS & SUPPLEMENTS", medicines, shop);
                createCategoryManually("AYURVEDIC & HERBAL", medicines, shop);

                Category surgicals = createCategoryManually("SURGICALS", root, shop);
                createCategoryManually("BANDAGES & DRESSINGS", surgicals, shop);
                createCategoryManually("SYRINGES & NEEDLES", surgicals, shop);
                createCategoryManually("GLOVES & MASKS", surgicals, shop);
                createCategoryManually("DIAGNOSTIC DEVICES", surgicals, shop);

                Category personalCare = createCategoryManually("PERSONAL CARE", root, shop);
                createCategoryManually("SKIN CARE", personalCare, shop);
                createCategoryManually("BABY CARE", personalCare, shop);
                createCategoryManually("HAIR CARE", personalCare, shop);
                createCategoryManually("EYE & EAR CARE", personalCare, shop);

                Category wellness = createCategoryManually("WELLNESS", root, shop);
                createCategoryManually("HEALTH DRINKS", wellness, shop);
                createCategoryManually("FITNESS & NUTRITION", wellness, shop);
                createCategoryManually("DIABETIC CARE", wellness, shop);
                createCategoryManually("CARDIAC & BP CARE", wellness, shop);
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
    public ShopDto updateShop(Long shopId, ShopDto dto, MultipartFile logo, MultipartFile signature) throws Exception {
        // 1. Fetch shop
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new EntityNotFoundException("Shop not found with id: " + shopId));

        // 2. Update basic fields (ignores logoPath/signaturePath as per our Mapper config)
        shopMapper.updateShopFromDto(dto, shop);

        // We need a UUID for your utility.
        // If your Shop entity doesn't have a UUID, we generate one from the ID or use random.
        UUID contextUuid = UUID.nameUUIDFromBytes(shopId.toString().getBytes());

        try {
            // 3. Handle Logo
            if (logo != null && !logo.isEmpty()) {
                // Using your utility: storeFile(file, folder, userId)
                String logoUrl = fileStorageService.storeFile(logo, "logos", contextUuid);
                shop.setLogoPath(logoUrl);
            }

            // 4. Handle Signature
            if (signature != null && !signature.isEmpty()) {
                String signatureUrl = fileStorageService.storeFile(signature, "signatures", contextUuid);
                shop.setSignaturePath(signatureUrl);
            }
        } catch (IOException e) {
            logger.error("File upload failed for shop settings update", e);
            throw new ApplicationException("Failed to save branding images: " + e.getMessage());
        }

        // 5. Save and Return
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