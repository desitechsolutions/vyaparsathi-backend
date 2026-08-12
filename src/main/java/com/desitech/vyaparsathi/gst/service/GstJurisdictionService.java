package com.desitech.vyaparsathi.gst.service;

import com.desitech.vyaparsathi.customer.entity.Customer;
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
