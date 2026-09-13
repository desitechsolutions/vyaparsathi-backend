package com.desitech.vyaparsathi.gst.service;

import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.gst.dto.JurisdictionResolveRequest;
import com.desitech.vyaparsathi.gst.dto.JurisdictionResolveResponse;
import com.desitech.vyaparsathi.gst.model.IndianState;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Central authority on Indian GST jurisdiction questions:
 *   • Is a transaction intra-state or inter-state?
 *   • If intra-state, is CGST+SGST or CGST+UTGST the correct split?
 *
 * Answers depend on 2-digit state codes (01–38, 97), not free-text state names.
 * During the transition period where existing shops/customers/suppliers may not
 * yet have {@code state_code} populated, this service falls back to a
 * case-insensitive lookup of the {@code state} name field via {@link IndianState#byName}.
 *
 * A third fallback exists for GST-registered parties: the first two characters
 * of a well-formed GSTIN are the state code. This is only consulted when both
 * {@code state_code} and {@code state} are absent.
 */
@Service
public class GstJurisdictionService {

    public enum IntraTaxRegime {
        /** Shop is in a regular state — intra-state sales split as CGST + SGST. */
        CGST_SGST,
        /** Shop is in a union territory — intra-state sales split as CGST + UTGST. */
        CGST_UTGST
    }

    /** Resolve a Shop's 2-digit state code. Returns empty if no source is available. */
    public Optional<String> resolveStateCode(Shop shop) {
        if (shop == null) return Optional.empty();
        if (isPresent(shop.getStateCode())) return Optional.of(shop.getStateCode());
        Optional<IndianState> byName = IndianState.byName(shop.getState());
        if (byName.isPresent()) return Optional.of(byName.get().getCode());
        return codeFromGstin(shop.getGstin());
    }

    /** Resolve a Customer's 2-digit state code. Returns empty if no source is available. */
    public Optional<String> resolveStateCode(Customer customer) {
        if (customer == null) return Optional.empty();
        if (isPresent(customer.getStateCode())) return Optional.of(customer.getStateCode());
        Optional<IndianState> byName = IndianState.byName(customer.getState());
        if (byName.isPresent()) return Optional.of(byName.get().getCode());
        return codeFromGstin(customer.getGstNumber());
    }

    /** Resolve a Supplier's 2-digit state code. Falls back to GSTIN prefix — the primary source for GST-registered suppliers. */
    public Optional<String> resolveStateCode(Supplier supplier) {
        if (supplier == null) return Optional.empty();
        if (isPresent(supplier.getStateCode())) return Optional.of(supplier.getStateCode());
        return codeFromGstin(supplier.getGstin());
    }

    /**
     * True when both parties are in the same state (intra-state supply).
     * If either code cannot be resolved, defaults to true (intra-state) — the
     * conservative default when data is incomplete, since CGST/SGST is the
     * more common case and inter-state is easier to correct after the fact.
     */
    public boolean isIntraState(String shopCode, String otherCode) {
        if (!isPresent(shopCode) || !isPresent(otherCode)) return true;
        return normalize(shopCode).equals(normalize(otherCode));
    }

    /** True when the given code identifies a Union Territory. */
    public boolean isUnionTerritory(String stateCode) {
        return IndianState.byCode(stateCode)
            .map(IndianState::isUnionTerritory)
            .orElse(false);
    }

    /**
     * The intra-state tax regime for a given shop state code. Non-UT shops
     * split intra-state supplies as CGST + SGST; UT shops split as CGST + UTGST.
     */
    public IntraTaxRegime getIntraTaxRegime(String shopStateCode) {
        return isUnionTerritory(shopStateCode)
            ? IntraTaxRegime.CGST_UTGST
            : IntraTaxRegime.CGST_SGST;
    }

    // ── Server-authoritative jurisdiction resolution (CA-8) ─────────────────

    /**
     * Resolves whether a transaction is intra-state or inter-state using a
     * priority chain:
     * <ol>
     *   <li>Explicit {@code posStateCode} from the request (highest priority)</li>
     *   <li>Explicit {@code counterpartyStateCode} from the request</li>
     *   <li>First two digits of {@code counterpartyGstin}</li>
     *   <li>Shop state code derived from the provided {@code shopStateCode}</li>
     * </ol>
     *
     * Falls back to intra-state when neither party can be resolved (safe default —
     * matches the behaviour in {@link #isIntraState(String, String)}).
     *
     * @param shopStateCode  the shop's 2-digit state code (resolved by caller via
     *                       {@link #resolveStateCode(Shop)} before the API call)
     * @param request        client-supplied jurisdiction hints
     * @return server-authoritative jurisdiction response with resolved codes, names,
     *         and UT flag
     */
    public JurisdictionResolveResponse resolveJurisdiction(String shopStateCode,
                                                           JurisdictionResolveRequest request) {
        // Effective Place-of-Supply code: prefer explicit POS, else fall back to shop code
        String effectivePosCode = isPresent(request.getPosStateCode())
                ? normalize(request.getPosStateCode())
                : (isPresent(shopStateCode) ? normalize(shopStateCode) : null);

        // Counterparty code: prefer explicit code, else try GSTIN prefix
        String counterpartyCode = null;
        if (isPresent(request.getCounterpartyStateCode())) {
            counterpartyCode = normalize(request.getCounterpartyStateCode());
        } else {
            Optional<String> fromGstin = codeFromGstin(request.getCounterpartyGstin());
            if (fromGstin.isPresent()) counterpartyCode = fromGstin.get();
        }

        boolean intra = isIntraState(effectivePosCode, counterpartyCode);
        boolean shopUT = effectivePosCode != null && isUnionTerritory(effectivePosCode);

        // Resolve display name for counterparty state
        String resolvedCode = counterpartyCode;
        String resolvedName = resolvedCode != null
                ? IndianState.byCode(resolvedCode).map(IndianState::getDisplayName).orElse(null)
                : null;

        return intra
                ? JurisdictionResolveResponse.intraState(resolvedCode, resolvedName, shopUT)
                : JurisdictionResolveResponse.interState(resolvedCode, resolvedName);
    }

    // ── helpers ─────────────────────────────────────────────────

    private static boolean isPresent(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String normalize(String code) {
        String trimmed = code.trim();
        return trimmed.length() == 1 ? "0" + trimmed : trimmed;
    }

    /**
     * Extract the 2-digit state code from a well-formed GSTIN. Format:
     * {@code SS PPPPPPPPPP N Z C} where SS is the state code. Returns empty
     * if the GSTIN is null, too short, or has non-numeric prefix.
     */
    private Optional<String> codeFromGstin(String gstin) {
        if (!isPresent(gstin) || gstin.length() < 2) return Optional.empty();
        String prefix = gstin.substring(0, 2);
        if (!prefix.chars().allMatch(Character::isDigit)) return Optional.empty();
        return IndianState.byCode(prefix).map(IndianState::getCode);
    }
}
