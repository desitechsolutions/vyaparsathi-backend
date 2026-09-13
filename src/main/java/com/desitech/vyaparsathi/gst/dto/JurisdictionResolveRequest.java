package com.desitech.vyaparsathi.gst.dto;

/**
 * Request payload for {@code POST /api/v1/gst/resolve-jurisdiction}.
 *
 * The client sends whatever state-identification it has for the counterparty.
 * The server resolves the authoritative 2-digit GSTN state code and returns
 * whether this transaction is intra-state or inter-state.
 *
 * <p>All fields are optional; the service applies a fallback chain:
 * explicit {@code stateCode} → name lookup → GSTIN prefix → intra-state default.
 */
public class JurisdictionResolveRequest {

    /** Tenant shop ID. If null the server uses the JWT-scoped shop. */
    private Long shopId;

    /**
     * Explicit Place of Supply state code (2-digit, e.g. "27" for Maharashtra).
     * Takes precedence over all other sources when present.
     */
    private String posStateCode;

    /**
     * Full 15-character GSTIN of the counterparty (buyer / supplier).
     * If {@code counterpartyStateCode} is absent, the first two digits of this
     * GSTIN are used as the state code.
     */
    private String counterpartyGstin;

    /**
     * Explicit 2-digit state code of the counterparty.
     * Overrides the GSTIN-derived code when present.
     */
    private String counterpartyStateCode;

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getPosStateCode() { return posStateCode; }
    public void setPosStateCode(String posStateCode) { this.posStateCode = posStateCode; }

    public String getCounterpartyGstin() { return counterpartyGstin; }
    public void setCounterpartyGstin(String counterpartyGstin) { this.counterpartyGstin = counterpartyGstin; }

    public String getCounterpartyStateCode() { return counterpartyStateCode; }
    public void setCounterpartyStateCode(String counterpartyStateCode) { this.counterpartyStateCode = counterpartyStateCode; }
}
