package com.desitech.vyaparsathi.gst.dto;

/**
 * Response from {@code POST /api/v1/gst/resolve-jurisdiction}.
 *
 * The frontend uses this to determine which GST columns to display
 * (CGST+SGST vs IGST), to label the Place of Supply correctly, and to
 * route the transaction to the right GSTR-1 table (B2B/B2CL/B2CS/EXP).
 */
public class JurisdictionResolveResponse {

    /** True when shop and counterparty are in the same state. */
    private boolean interState;

    /** "INTRA_STATE" or "INTER_STATE" — for use in UI display and routing. */
    private String taxType;

    /**
     * The authoritative 2-digit GST state code for the counterparty
     * (derived via fallback chain from explicit code / name / GSTIN prefix).
     * Null when no state could be resolved.
     */
    private String resolvedStateCode;

    /** Human-readable display name of the resolved state (null when unresolved). */
    private String resolvedStateName;

    /**
     * True when the shop is in a Union Territory and intra-territory supplies
     * must use CGST+UTGST (not CGST+SGST).
     */
    private boolean shopInUnionTerritory;

    /**
     * Which GST component applies for intra-state supplies:
     * "CGST_SGST" for regular states, "CGST_UTGST" for Union Territories.
     * Always "IGST" for inter-state.
     */
    private String intraTaxRegime;

    // ── static factory ────────────────────────────────────────────────────────

    public static JurisdictionResolveResponse interState(String resolvedStateCode, String resolvedStateName) {
        JurisdictionResolveResponse r = new JurisdictionResolveResponse();
        r.interState = true;
        r.taxType = "INTER_STATE";
        r.resolvedStateCode = resolvedStateCode;
        r.resolvedStateName = resolvedStateName;
        r.shopInUnionTerritory = false;
        r.intraTaxRegime = "IGST";
        return r;
    }

    public static JurisdictionResolveResponse intraState(String resolvedStateCode, String resolvedStateName,
                                                         boolean shopInUnionTerritory) {
        JurisdictionResolveResponse r = new JurisdictionResolveResponse();
        r.interState = false;
        r.taxType = "INTRA_STATE";
        r.resolvedStateCode = resolvedStateCode;
        r.resolvedStateName = resolvedStateName;
        r.shopInUnionTerritory = shopInUnionTerritory;
        r.intraTaxRegime = shopInUnionTerritory ? "CGST_UTGST" : "CGST_SGST";
        return r;
    }

    // ── getters / setters ─────────────────────────────────────────────────────

    public boolean isInterState() { return interState; }
    public void setInterState(boolean interState) { this.interState = interState; }

    public String getTaxType() { return taxType; }
    public void setTaxType(String taxType) { this.taxType = taxType; }

    public String getResolvedStateCode() { return resolvedStateCode; }
    public void setResolvedStateCode(String resolvedStateCode) { this.resolvedStateCode = resolvedStateCode; }

    public String getResolvedStateName() { return resolvedStateName; }
    public void setResolvedStateName(String resolvedStateName) { this.resolvedStateName = resolvedStateName; }

    public boolean isShopInUnionTerritory() { return shopInUnionTerritory; }
    public void setShopInUnionTerritory(boolean shopInUnionTerritory) { this.shopInUnionTerritory = shopInUnionTerritory; }

    public String getIntraTaxRegime() { return intraTaxRegime; }
    public void setIntraTaxRegime(String intraTaxRegime) { this.intraTaxRegime = intraTaxRegime; }
}
