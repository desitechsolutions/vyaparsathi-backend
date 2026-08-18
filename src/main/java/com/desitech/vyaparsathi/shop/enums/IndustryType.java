package com.desitech.vyaparsathi.shop.enums;

/**
 * The set of industries Vyapar Sathi supports.
 *
 * <p>Stored as {@code @Enumerated(EnumType.STRING)} on {@code Shop.industryType},
 * so the on-disk value is the enum's exact name — safe to add new industries
 * at the end of the list, unsafe to rename or reorder existing ones.
 *
 * <p><b>PHARMACY is intentionally absent</b> — support was removed in project
 * phase V46 and the remaining i18n stubs are inert. Any incoming shop with a
 * legacy "PHARMACY" value falls through {@link #fromString(String)} to
 * {@link #GENERAL} so a botched migration does not lock the tenant out.
 */
public enum IndustryType {
    // ─── Original 10 ───
    CLOTHING,
    ELECTRONICS,
    HARDWARE,
    GROCERY,
    AUTOMOBILE,
    STATIONERY,
    FOOTWEAR,
    FURNITURE,
    JEWELLERY,
    GENERAL,

    // ─── Enterprise expansion (Phase 4) ───
    // Sector-specific retail
    BUILDING_MATERIALS,   // Cement, sand, aggregates, steel rods, bricks
    RESTAURANT,           // Restaurants, dhabas, cloud kitchens, takeaway
    BAKERY,               // Bakery, sweets, confectionery
    DAIRY,                // Milk, ghee, paneer, curd
    SUPERMARKET,          // Multi-category mini-mart / hypermarket
    COSMETICS,            // Cosmetics, beauty supplies
    OPTICAL,              // Spectacles, contact lenses, sunglasses
    AGRICULTURE,          // Seeds, fertilizer, farm supplies
    SPORTS,               // Sports goods, fitness equipment
    BOOKS,                // Bookstore, educational materials
    TOYS,                 // Toys, games
    MOBILE_ACCESSORIES,   // Mobile phones + accessories (subset of ELECTRONICS)
    HOME_APPLIANCES,      // Large white goods, kitchen appliances
    KITCHENWARE,          // Utensils, cookware
    TEXTILE,              // Fabric, upholstery, wholesale textile
    PAINT,                // Paint, primer, coatings
    SANITARY_TILES,       // Tiles, bath fixtures, sanitary ware
    MEDICAL_EQUIPMENT,    // Medical devices, hospital supplies (NOT pharmacy)
    PET_SUPPLIES,         // Pet food, accessories, aquarium supplies
    MUSICAL_INSTRUMENTS,  // Instruments, sound equipment
    FLORIST,              // Flowers, bouquets, event decor
    HANDICRAFTS,          // Handmade goods, art, crafts

    // Services / crosscut
    SALON_SPA,            // Salon, spa (services + retail)
    LAUNDRY,              // Laundry, dry-cleaning (services)
    SERVICES,             // Consulting, professional services (billing time/labour)
    WHOLESALE,            // Wholesale distribution (any category)
    MANUFACTURING;        // Manufacturing / job-work

    /**
     * Lenient parser used everywhere strings cross the boundary — request
     * bodies from the frontend, config maps in application.properties,
     * legacy rows that predate the enum. Unknown or blank input maps to
     * {@link #GENERAL} rather than throwing, so a single bad row cannot
     * take down a shop's onboarding.
     */
    public static IndustryType fromString(String s) {
        if (s == null || s.isBlank()) return GENERAL;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return GENERAL;
        }
    }
}
