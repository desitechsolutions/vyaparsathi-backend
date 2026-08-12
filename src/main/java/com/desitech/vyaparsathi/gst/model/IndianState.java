package com.desitech.vyaparsathi.gst.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The 37 Indian jurisdictions recognized by the GSTN for state-code purposes:
 * 28 states + 8 union territories (post-2020 reorganization) + special code "97"
 * for "Other Territory" (unassigned areas / exclusive economic zone).
 *
 * The 2-digit code prefixes every GSTIN and drives the CGST/SGST/IGST/UTGST split.
 *
 * Codes 34 (Puducherry-old — retained), 35–38: post-2019 reorganization codes.
 * Notes on gaps in the numbering:
 *   - Code 26 was reassigned in 2020 when Dadra & Nagar Haveli merged with Daman & Diu.
 *     The merged UT uses "26" and legacy code "25" is retired but retained here for
 *     historical data safety.
 *   - Code 37 (Andhra Pradesh — New) was used post-Telangana split; original 28 was
 *     retired but retained here for legacy GSTINs.
 *   - Code 38 (Ladakh) was introduced in 2019 when it was carved out of J&K.
 */
public enum IndianState {

    // ── STATES (28) ─────────────────────────────────────────────
    ANDHRA_PRADESH        ("Andhra Pradesh",           "37", false),
    ANDHRA_PRADESH_OLD    ("Andhra Pradesh (Old)",     "28", false), // legacy — retained for pre-2014 GSTINs
    ARUNACHAL_PRADESH     ("Arunachal Pradesh",        "12", false),
    ASSAM                 ("Assam",                    "18", false),
    BIHAR                 ("Bihar",                    "10", false),
    CHHATTISGARH          ("Chhattisgarh",             "22", false),
    GOA                   ("Goa",                      "30", false),
    GUJARAT               ("Gujarat",                  "24", false),
    HARYANA               ("Haryana",                  "06", false),
    HIMACHAL_PRADESH      ("Himachal Pradesh",         "02", false),
    JHARKHAND             ("Jharkhand",                "20", false),
    KARNATAKA             ("Karnataka",                "29", false),
    KERALA                ("Kerala",                   "32", false),
    MADHYA_PRADESH        ("Madhya Pradesh",           "23", false),
    MAHARASHTRA           ("Maharashtra",              "27", false),
    MANIPUR               ("Manipur",                  "14", false),
    MEGHALAYA             ("Meghalaya",                "17", false),
    MIZORAM               ("Mizoram",                  "15", false),
    NAGALAND              ("Nagaland",                 "13", false),
    ODISHA                ("Odisha",                   "21", false),
    PUNJAB                ("Punjab",                   "03", false),
    RAJASTHAN             ("Rajasthan",                "08", false),
    SIKKIM                ("Sikkim",                   "11", false),
    TAMIL_NADU            ("Tamil Nadu",               "33", false),
    TELANGANA             ("Telangana",                "36", false),
    TRIPURA               ("Tripura",                  "16", false),
    UTTARAKHAND           ("Uttarakhand",              "05", false),
    UTTAR_PRADESH         ("Uttar Pradesh",            "09", false),
    WEST_BENGAL           ("West Bengal",              "19", false),

    // ── UNION TERRITORIES (8) ───────────────────────────────────
    ANDAMAN_AND_NICOBAR   ("Andaman and Nicobar Islands", "35", true),
    CHANDIGARH            ("Chandigarh",                  "04", true),
    DADRA_AND_NAGAR_HAVELI("Dadra and Nagar Haveli and Daman and Diu", "26", true),
    DADRA_LEGACY          ("Dadra and Nagar Haveli (Legacy)",          "25", true), // retired 2020, kept for legacy data
    DELHI                 ("Delhi",                       "07", true),
    JAMMU_AND_KASHMIR     ("Jammu and Kashmir",           "01", true),
    LADAKH                ("Ladakh",                      "38", true),
    LAKSHADWEEP           ("Lakshadweep",                 "31", true),
    PUDUCHERRY            ("Puducherry",                  "34", true),

    // ── SPECIAL ─────────────────────────────────────────────────
    OTHER_TERRITORY       ("Other Territory",             "97", false); // EEZ / offshore / unassigned

    private final String displayName;
    private final String code;
    private final boolean unionTerritory;

    IndianState(String displayName, String code, boolean unionTerritory) {
        this.displayName = displayName;
        this.code = code;
        this.unionTerritory = unionTerritory;
    }

    public String getDisplayName() { return displayName; }
    public String getCode() { return code; }
    public boolean isUnionTerritory() { return unionTerritory; }

    // Lookup by 2-digit code. Returns Optional.empty() for null / unknown / bad-length input.
    private static final Map<String, IndianState> BY_CODE = Arrays.stream(values())
        .collect(Collectors.toMap(IndianState::getCode, s -> s, (a, b) -> a));

    public static Optional<IndianState> byCode(String code) {
        if (code == null) return Optional.empty();
        String normalized = code.trim();
        if (normalized.length() == 1) normalized = "0" + normalized;
        return Optional.ofNullable(BY_CODE.get(normalized));
    }

    /**
     * Case-insensitive lookup by human-readable name. Trims whitespace and matches
     * the canonical display name only. Callers with non-canonical inputs (e.g. "New
     * Delhi", "Bombay") should normalize before calling — this method does not do
     * fuzzy matching by design.
     */
    private static final Map<String, IndianState> BY_NAME = Arrays.stream(values())
        .collect(Collectors.toMap(
            s -> s.getDisplayName().toLowerCase(Locale.ROOT),
            s -> s,
            (a, b) -> a
        ));

    public static Optional<IndianState> byName(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(BY_NAME.get(name.trim().toLowerCase(Locale.ROOT)));
    }

    /**
     * Full list for dropdowns. Excludes legacy duplicates (ANDHRA_PRADESH_OLD,
     * DADRA_LEGACY) so users don't accidentally pick a retired code for a new record.
     * Legacy codes remain reachable via byCode() for historical data reads.
     */
    public static List<IndianState> pickableForDropdown() {
        return Collections.unmodifiableList(Arrays.stream(values())
            .filter(s -> s != ANDHRA_PRADESH_OLD && s != DADRA_LEGACY)
            .sorted((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()))
            .collect(Collectors.toList()));
    }
}
