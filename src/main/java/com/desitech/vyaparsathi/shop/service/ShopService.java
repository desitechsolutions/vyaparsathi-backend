package com.desitech.vyaparsathi.shop.service;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.model.Role;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.auth.service.MfaService;
import com.desitech.vyaparsathi.auth.service.RefreshTokenService;
import com.desitech.vyaparsathi.rbac.entity.UserShopMembership;
import com.desitech.vyaparsathi.rbac.repository.UserShopMembershipRepository;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.common.util.FileStorageService;
import com.desitech.vyaparsathi.inventory.entity.Category;
import com.desitech.vyaparsathi.inventory.repository.CategoryRepository;
import com.desitech.vyaparsathi.shop.dto.ShopDto;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.enums.IndustryType;
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
import java.util.ArrayList;
import java.util.List;
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

    @Autowired
    private MfaService mfaService;

    @Autowired
    private UserShopMembershipRepository membershipRepository;

    @Transactional
    public ShopDto completeOnboarding(ShopDto dto, User currentUser, MultipartFile logo) {
        return completeOnboarding(dto, currentUser, logo, null);
    }

    @Transactional
    public ShopDto completeOnboarding(ShopDto dto, User currentUser, MultipartFile logo,
                                      RefreshTokenService.SessionMetadata sessionMetadata) {
        if (currentUser.getShop() != null) {
            throw new IllegalStateException("Shop already exists for this user");
        }

        if (shopRepository.existsByCode(dto.getCode())) {
            throw new IllegalArgumentException("Shop code '" + dto.getCode() + "' is already in use");
        }

        Shop shop = shopMapper.toEntity(dto);

        // ── Null-safe boolean defaults ────────────────────────────────────────
        // The Shop entity uses field-based access (because @Id is on the id
        // FIELD, not on a getter), and its Boolean getters return FALSE when
        // the underlying field is null. That means a `getXxx() == null` check
        // NEVER fires — the getter lies. So we bypass the getter entirely and
        // derive the desired value from the DTO with Boolean.TRUE.equals()
        // (which is null-safe and gives FALSE for null). The setter then
        // writes a non-null value to the field, and the NOT NULL constraint
        // is satisfied.
        // New shops are always active — the DTO doesn't expose this, and the
        // Shop entity's lying getter would otherwise hide a null slip-through.
        shop.setActive(Boolean.TRUE);
        shop.setDigitalSigningEnabled(Boolean.TRUE.equals(dto.getDigitalSigningEnabled()));
        shop.setEInvoicingEnabled(Boolean.TRUE.equals(dto.getEInvoicingEnabled()));
        shop.setEWayBillEnabled(Boolean.TRUE.equals(dto.getEWayBillEnabled()));
        shop.setLowStockAlertsEnabled(Boolean.TRUE.equals(dto.getLowStockAlertsEnabled()));
        shop.setLowStockSmsAlertsEnabled(Boolean.TRUE.equals(dto.getLowStockSmsAlertsEnabled()));
        shop.setPoApprovalRequired(Boolean.TRUE.equals(dto.getPoApprovalRequired()));
        shop.setIsCompositionScheme(Boolean.TRUE.equals(dto.getIsCompositionScheme()));
        shop.setRequireMfaForAdmins(Boolean.TRUE.equals(dto.getRequireMfaForAdmins()));
        // Same lying-getter problem as the booleans: getPoApprovalThresholdAmount()
        // returns ZERO when the field is null, so a "== null" gate would never
        // fire. Set unconditionally, taking the DTO value when supplied.
        shop.setPoApprovalThresholdAmount(
                dto.getPoApprovalThresholdAmount() != null
                        ? dto.getPoApprovalThresholdAmount()
                        : java.math.BigDecimal.ZERO);
        // ─────────────────────────────────────────────────────────────────────

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

        String sid = sessionMetadata != null ? sessionMetadata.getSessionId()
                : java.util.UUID.randomUUID().toString().replace("-", "");
        RefreshTokenService.SessionMetadata effectiveMeta = sessionMetadata != null ? sessionMetadata
                : new RefreshTokenService.SessionMetadata(sid, "Unknown device", null, null);
        String newJwtToken = jwtUtil.generateAccessToken(currentUser, shop.getId(), sid);
        String refreshToken = refreshTokenService.createRefreshToken(currentUser.getUsername(), effectiveMeta).getToken();
        logger.info("Onboarding completed - Shop created: id={}, name={}, industry={}",
                shop.getId(), shop.getName(), dto.getIndustryType());

        ShopDto shopDto = shopMapper.toDto(shop);
        shopDto.setAccessToken(newJwtToken);
        shopDto.setRefreshToken(refreshToken);
        return shopDto;
    }

    private void seedDefaultCategories(Shop shop, String industryTypeRaw) {
        if (shop.getId() == null) throw new IllegalStateException("Shop has no ID");

        IndustryType industry = IndustryType.fromString(industryTypeRaw);
        logger.info("Seeding categories for industry: {} in shop {}", industry, shop.getId());

        // Create the Root Industry Node (CRITICAL for UI detection)
        Category root = createCategoryManually(industry.name(), null, shop);

        switch (industry) {
            case CLOTHING:
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

            case ELECTRONICS:
                createCategoryManually("MOBILES & TABLETS", root, shop);
                createCategoryManually("LAPTOPS & COMPUTERS", root, shop);
                createCategoryManually("HOME APPLIANCES", root, shop);
                createCategoryManually("AUDIO & ACCESSORIES", root, shop);
                createCategoryManually("WEARABLES", root, shop);
                break;

            case HARDWARE:
                createCategoryManually("ELECTRICALS", root, shop);
                createCategoryManually("PLUMBING", root, shop);
                createCategoryManually("PAINTS", root, shop);
                createCategoryManually("TOOLS & FASTENERS", root, shop);
                break;

            case GROCERY:
                createCategoryManually("DAIRY & BAKERY", root, shop);
                createCategoryManually("STAPLES", root, shop);
                createCategoryManually("SNACKS & BEVERAGES", root, shop);
                createCategoryManually("HOUSEHOLD CARE", root, shop);
                break;

            case AUTOMOBILE:
                createCategoryManually("SPARE PARTS", root, shop);
                createCategoryManually("LUBRICANTS", root, shop);
                createCategoryManually("TYRES", root, shop);
                createCategoryManually("ACCESSORIES", root, shop);
                break;

            case STATIONERY:
                createCategoryManually("OFFICE SUPPLIES", root, shop);
                createCategoryManually("SCHOOL SUPPLIES", root, shop);
                createCategoryManually("ART & CRAFT", root, shop);
                break;

            case FOOTWEAR:
                createCategoryManually("SPORTS FOOTWEAR", root, shop);
                createCategoryManually("FORMAL FOOTWEAR", root, shop);
                createCategoryManually("CASUAL FOOTWEAR", root, shop);
                break;

            case FURNITURE:
                createCategoryManually("OFFICE FURNITURE", root, shop);
                createCategoryManually("HOME FURNITURE", root, shop);
                createCategoryManually("FURNISHINGS", root, shop);
                break;

            case JEWELLERY:
                createCategoryManually("GOLD", root, shop);
                createCategoryManually("SILVER", root, shop);
                createCategoryManually("FASHION JEWELLERY", root, shop);
                break;

            // ── Phase 4 enterprise expansion ──────────────────────────

            case BUILDING_MATERIALS:
                createCategoryManually("CEMENT", root, shop);
                createCategoryManually("SAND / BALU", root, shop);
                createCategoryManually("STEEL RODS / TMT", root, shop);
                createCategoryManually("AGGREGATE / GITTI", root, shop);
                createCategoryManually("BRICKS & BLOCKS", root, shop);
                createCategoryManually("READY-MIX CONCRETE", root, shop);
                break;

            case RESTAURANT:
                createCategoryManually("STARTERS", root, shop);
                createCategoryManually("MAIN COURSE", root, shop);
                createCategoryManually("BREADS", root, shop);
                createCategoryManually("RICE & BIRYANI", root, shop);
                createCategoryManually("BEVERAGES", root, shop);
                createCategoryManually("DESSERTS", root, shop);
                break;

            case BAKERY:
                createCategoryManually("CAKES", root, shop);
                createCategoryManually("PASTRIES", root, shop);
                createCategoryManually("BREADS", root, shop);
                createCategoryManually("SWEETS", root, shop);
                createCategoryManually("SAVOURIES", root, shop);
                break;

            case DAIRY:
                createCategoryManually("MILK", root, shop);
                createCategoryManually("CURD & BUTTERMILK", root, shop);
                createCategoryManually("PANEER & CHEESE", root, shop);
                createCategoryManually("GHEE & BUTTER", root, shop);
                createCategoryManually("ICE CREAM", root, shop);
                break;

            case SUPERMARKET:
                createCategoryManually("FRESH PRODUCE", root, shop);
                createCategoryManually("PACKAGED FOOD", root, shop);
                createCategoryManually("BEVERAGES", root, shop);
                createCategoryManually("HOUSEHOLD & CLEANING", root, shop);
                createCategoryManually("PERSONAL CARE", root, shop);
                createCategoryManually("STATIONERY & OTHERS", root, shop);
                break;

            case COSMETICS:
                createCategoryManually("SKINCARE", root, shop);
                createCategoryManually("MAKEUP", root, shop);
                createCategoryManually("HAIRCARE", root, shop);
                createCategoryManually("FRAGRANCES", root, shop);
                createCategoryManually("BATH & BODY", root, shop);
                break;

            case OPTICAL:
                createCategoryManually("EYEGLASSES", root, shop);
                createCategoryManually("SUNGLASSES", root, shop);
                createCategoryManually("CONTACT LENSES", root, shop);
                createCategoryManually("LENS SOLUTIONS", root, shop);
                createCategoryManually("ACCESSORIES", root, shop);
                break;

            case AGRICULTURE:
                createCategoryManually("SEEDS", root, shop);
                createCategoryManually("FERTILIZERS", root, shop);
                createCategoryManually("PESTICIDES & HERBICIDES", root, shop);
                createCategoryManually("FARM TOOLS", root, shop);
                createCategoryManually("ANIMAL FEED", root, shop);
                break;

            case SPORTS:
                createCategoryManually("TEAM SPORTS", root, shop);
                createCategoryManually("FITNESS & GYM", root, shop);
                createCategoryManually("OUTDOOR & ADVENTURE", root, shop);
                createCategoryManually("SPORTS APPAREL", root, shop);
                createCategoryManually("NUTRITION", root, shop);
                break;

            case BOOKS:
                createCategoryManually("ACADEMIC", root, shop);
                createCategoryManually("FICTION", root, shop);
                createCategoryManually("NON-FICTION", root, shop);
                createCategoryManually("CHILDREN", root, shop);
                createCategoryManually("REFERENCE", root, shop);
                break;

            case TOYS:
                createCategoryManually("EDUCATIONAL TOYS", root, shop);
                createCategoryManually("SOFT TOYS", root, shop);
                createCategoryManually("OUTDOOR TOYS", root, shop);
                createCategoryManually("GAMES & PUZZLES", root, shop);
                createCategoryManually("REMOTE-CONTROLLED", root, shop);
                break;

            case MOBILE_ACCESSORIES:
                createCategoryManually("MOBILE PHONES", root, shop);
                createCategoryManually("CASES & COVERS", root, shop);
                createCategoryManually("CHARGERS & CABLES", root, shop);
                createCategoryManually("AUDIO ACCESSORIES", root, shop);
                createCategoryManually("SCREEN PROTECTION", root, shop);
                break;

            case HOME_APPLIANCES:
                createCategoryManually("KITCHEN APPLIANCES", root, shop);
                createCategoryManually("LARGE APPLIANCES", root, shop);
                createCategoryManually("PERSONAL CARE", root, shop);
                createCategoryManually("HOME COMFORT", root, shop);
                break;

            case KITCHENWARE:
                createCategoryManually("COOKWARE", root, shop);
                createCategoryManually("DINNERWARE", root, shop);
                createCategoryManually("STORAGE", root, shop);
                createCategoryManually("BAKEWARE", root, shop);
                createCategoryManually("UTENSILS & TOOLS", root, shop);
                break;

            case TEXTILE:
                createCategoryManually("COTTON", root, shop);
                createCategoryManually("SILK", root, shop);
                createCategoryManually("SYNTHETIC", root, shop);
                createCategoryManually("UPHOLSTERY", root, shop);
                createCategoryManually("HOME FURNISHING", root, shop);
                break;

            case PAINT:
                createCategoryManually("INTERIOR EMULSION", root, shop);
                createCategoryManually("EXTERIOR EMULSION", root, shop);
                createCategoryManually("ENAMEL & PRIMER", root, shop);
                createCategoryManually("WOOD FINISHES", root, shop);
                createCategoryManually("WATERPROOFING", root, shop);
                createCategoryManually("TOOLS & ACCESSORIES", root, shop);
                break;

            case SANITARY_TILES:
                createCategoryManually("FLOOR TILES", root, shop);
                createCategoryManually("WALL TILES", root, shop);
                createCategoryManually("SANITARY WARE", root, shop);
                createCategoryManually("FAUCETS & MIXERS", root, shop);
                createCategoryManually("BATH FITTINGS", root, shop);
                break;

            case MEDICAL_EQUIPMENT:
                createCategoryManually("DIAGNOSTIC EQUIPMENT", root, shop);
                createCategoryManually("THERAPEUTIC EQUIPMENT", root, shop);
                createCategoryManually("DISPOSABLES & CONSUMABLES", root, shop);
                createCategoryManually("MOBILITY AIDS", root, shop);
                createCategoryManually("HOSPITAL FURNITURE", root, shop);
                break;

            case PET_SUPPLIES:
                createCategoryManually("DOG SUPPLIES", root, shop);
                createCategoryManually("CAT SUPPLIES", root, shop);
                createCategoryManually("BIRD SUPPLIES", root, shop);
                createCategoryManually("AQUARIUM", root, shop);
                createCategoryManually("PET ACCESSORIES", root, shop);
                break;

            case MUSICAL_INSTRUMENTS:
                createCategoryManually("STRING INSTRUMENTS", root, shop);
                createCategoryManually("WIND INSTRUMENTS", root, shop);
                createCategoryManually("PERCUSSION", root, shop);
                createCategoryManually("KEYBOARDS", root, shop);
                createCategoryManually("SOUND EQUIPMENT", root, shop);
                break;

            case FLORIST:
                createCategoryManually("BOUQUETS", root, shop);
                createCategoryManually("FLOWER ARRANGEMENTS", root, shop);
                createCategoryManually("WEDDING DECOR", root, shop);
                createCategoryManually("EVENT DECOR", root, shop);
                createCategoryManually("GIFTS & HAMPERS", root, shop);
                break;

            case HANDICRAFTS:
                createCategoryManually("HOME DECOR", root, shop);
                createCategoryManually("WALL ART", root, shop);
                createCategoryManually("TEXTILE CRAFTS", root, shop);
                createCategoryManually("WOODEN CRAFTS", root, shop);
                createCategoryManually("METAL CRAFTS", root, shop);
                break;

            case SALON_SPA:
                createCategoryManually("HAIRCARE SERVICES", root, shop);
                createCategoryManually("SKINCARE SERVICES", root, shop);
                createCategoryManually("NAIL SERVICES", root, shop);
                createCategoryManually("MASSAGE & SPA", root, shop);
                createCategoryManually("PRODUCTS", root, shop);
                break;

            case LAUNDRY:
                createCategoryManually("WASH & FOLD", root, shop);
                createCategoryManually("WASH & IRON", root, shop);
                createCategoryManually("DRY CLEANING", root, shop);
                createCategoryManually("IRONING ONLY", root, shop);
                createCategoryManually("SPECIALTY CARE", root, shop);
                break;

            case SERVICES:
                createCategoryManually("CONSULTING", root, shop);
                createCategoryManually("PROJECT WORK", root, shop);
                createCategoryManually("MAINTENANCE", root, shop);
                createCategoryManually("RETAINERS", root, shop);
                break;

            case WHOLESALE:
                createCategoryManually("BULK GOODS", root, shop);
                createCategoryManually("DISTRIBUTION", root, shop);
                createCategoryManually("PROMOTIONS", root, shop);
                break;

            case MANUFACTURING:
                createCategoryManually("RAW MATERIALS", root, shop);
                createCategoryManually("WORK IN PROGRESS", root, shop);
                createCategoryManually("FINISHED GOODS", root, shop);
                createCategoryManually("PACKAGING", root, shop);
                break;

            case GENERAL:
            default:
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

        // Guard the FALSE → TRUE transition on requireMfaForAdmins so an OWNER
        // can't accidentally lock themselves (and their co-admins) out of
        // their own shop. If any active OWNER/ADMIN in this shop doesn't
        // have MFA enabled, refuse the save and list who needs to fix it
        // first — safer than "warn then break".
        boolean currentlyRequireMfa = Boolean.TRUE.equals(shop.getRequireMfaForAdmins());
        boolean incomingRequireMfa = Boolean.TRUE.equals(dto.getRequireMfaForAdmins());
        if (incomingRequireMfa && !currentlyRequireMfa) {
            List<String> adminsWithoutMfa = collectAdminsWithoutMfa(shopId);
            if (!adminsWithoutMfa.isEmpty()) {
                throw new ApplicationException(
                        "Enable two-factor authentication on these accounts before turning on the shop policy: "
                                + String.join(", ", adminsWithoutMfa)
                                + ". Each affected owner/admin needs to visit Account Security and finish MFA setup first.");
            }
        }

        // 2. Update basic fields (ignores logoPath/signaturePath as per our Mapper config)
        shopMapper.updateShopFromDto(dto, shop);
        // The mapper's IGNORE strategy skips null DTO fields, but on an explicit
        // boolean value the mapper writes null vs Boolean.FALSE inconsistently
        // depending on the client's serializer — pin the effective value from
        // the request explicitly so a null-vs-false confusion can't drop the flag.
        if (dto.getRequireMfaForAdmins() != null) {
            shop.setRequireMfaForAdmins(incomingRequireMfa);
        }

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

    /**
     * Returns the display identifiers (email or username) of every active
     * OWNER/ADMIN in the given shop who does NOT have MFA enabled on their
     * user account. Used by {@link #updateShop} to guard the "require MFA
     * for admins" policy flip.
     *
     * <p>Falls back to a users.role scan when the membership table is
     * empty for this shop (edge case: pre-Phase-5 shops with a single
     * OWNER row on the legacy users.shop_id path).
     */
    private List<String> collectAdminsWithoutMfa(Long shopId) {
        List<String> out = new ArrayList<>();
        List<UserShopMembership> memberships = membershipRepository.findByShopIdAndActiveTrue(shopId);
        if (memberships.isEmpty()) {
            // Legacy shape: look up users pointed at this shop directly.
            userRepository.findByShopId(shopId).forEach(u -> {
                if ((u.getRole() == Role.OWNER || u.getRole() == Role.ADMIN)
                        && !mfaService.isEnabled(u.getId())) {
                    out.add(displayId(u));
                }
            });
            return out;
        }
        for (UserShopMembership m : memberships) {
            if (!"OWNER".equalsIgnoreCase(m.getRole()) && !"ADMIN".equalsIgnoreCase(m.getRole())) continue;
            if (mfaService.isEnabled(m.getUserId())) continue;
            userRepository.findById(m.getUserId())
                    .ifPresent(u -> out.add(displayId(u)));
        }
        return out;
    }

    private String displayId(User u) {
        String email = u.getEmail();
        if (email != null && !email.isBlank()) return email;
        return u.getUsername();
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