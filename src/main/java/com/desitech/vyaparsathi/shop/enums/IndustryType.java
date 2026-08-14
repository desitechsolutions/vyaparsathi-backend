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
    CLOTHING,
    ELECTRONICS,
    HARDWARE,
    GROCERY,
    AUTOMOBILE,
    STATIONERY,
    FOOTWEAR,
    FURNITURE,
    JEWELLERY,
    GENERAL;

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
