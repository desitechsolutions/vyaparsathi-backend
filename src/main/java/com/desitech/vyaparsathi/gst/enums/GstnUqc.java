package com.desitech.vyaparsathi.gst.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * GSTN-recognised Unit Quantity Codes (UQC) for the HSN Summary section
 * of GSTR-1 (Table 12) and e-invoice item payloads.
 *
 * <p>Source: GSTN HSN Summary master list (CBIC circular, revised FY 2022-23).
 * Any quantity unit not in this list must be reported as {@link #OTH}.
 *
 * <p>Usage:
 * <pre>
 *   GstnUqc uqc = GstnUqc.fromCode(itemVariant.getUqc())
 *                          .orElse(GstnUqc.OTH);
 * </pre>
 */
public enum GstnUqc {

    // ── Weight ───────────────────────────────────────────────────────────────
    KGS("KGS", "Kilograms"),
    GMS("GMS", "Grams"),
    TON("TON", "Tonnes"),
    QTL("QTL", "Quintal"),
    TGM("TGM", "Ten Grams"),
    UGS("UGS", "Micrograms"),
    MGS("MGS", "Milligrams — Note: Use GMS/KGS where possible"),

    // ── Volume ───────────────────────────────────────────────────────────────
    LTR("LTR", "Litres"),
    MLT("MLT", "Millilitres"),
    CBM("CBM", "Cubic Metres"),
    CCM("CCM", "Cubic Centimetres"),

    // ── Length / Area ────────────────────────────────────────────────────────
    MTR("MTR", "Metres"),
    CMS("CMS", "Centimetres"),
    KME("KME", "Kilometres"),
    SQM("SQM", "Square Metres"),
    SQF("SQF", "Square Feet"),
    SQY("SQY", "Square Yards"),

    // ── Count / Discrete ─────────────────────────────────────────────────────
    NOS("NOS", "Numbers"),
    PCS("PCS", "Pieces"),
    UNT("UNT", "Units"),
    DOZ("DOZ", "Dozens"),
    SET("SET", "Sets"),
    PRS("PRS", "Pairs"),

    // ── Packaging ────────────────────────────────────────────────────────────
    BOX("BOX", "Boxes"),
    BAG("BAG", "Bags"),
    BTL("BTL", "Bottles"),
    CAN("CAN", "Cans"),
    CTN("CTN", "Cartons"),
    PAC("PAC", "Packs"),
    PKT("PKT", "Packets"),
    DRM("DRM", "Drums"),
    TUB("TUB", "Tubes"),
    ROL("ROL", "Rolls"),
    BAL("BAL", "Bales"),
    BDL("BDL", "Bundles"),
    BKL("BKL", "Buckles"),
    BUN("BUN", "Bunches"),
    GYD("GYD", "Gross Yards"),
    GRS("GRS", "Gross"),
    GGK("GGK", "Great Gross (144 dozen)"),

    // ── Textile ──────────────────────────────────────────────────────────────
    YDS("YDS", "Yards"),
    THD("THD", "Threads"),
    TBS("TBS", "Tablets"),

    // ── Cloth / Fibre ────────────────────────────────────────────────────────
    KLR("KLR", "Kilolitres"),

    // ── Catch-all ────────────────────────────────────────────────────────────
    /**
     * Others — use when no GSTN-defined UQC matches the item's unit.
     * GSTN accepts this for all HSN rows; it appears in the filing as "OTH".
     */
    OTH("OTH", "Others");

    // ─────────────────────────────────────────────────────────────────────────

    /** The 3-letter code sent to GSTN in filings. */
    private final String code;

    /** Human-readable label for the UI dropdown. */
    private final String description;

    GstnUqc(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }

    /**
     * Looks up a {@link GstnUqc} by its 3-letter code (case-insensitive).
     * Returns {@link Optional#empty()} for codes not in the GSTN master list.
     */
    public static Optional<GstnUqc> fromCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        String normalised = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(u -> u.code.equals(normalised))
                .findFirst();
    }

    /**
     * Returns the GSTN UQC for a services line — GSTN mandates {@code "NA"}
     * for SAC-coded service lines in HSN Summary.
     */
    public static String gstnCodeForService() {
        return "NA";
    }

    @Override
    public String toString() {
        return code + " — " + description;
    }
}
