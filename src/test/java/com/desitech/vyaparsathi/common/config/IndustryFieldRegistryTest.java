package com.desitech.vyaparsathi.common.config;

import com.desitech.vyaparsathi.shop.enums.IndustryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sanity checks on {@link IndustryFieldRegistry} — every enum value has
 * a spec, jewellery gets its metal + weight fields, GENERAL is empty,
 * and field {@code type} values stay in the small allowed set the
 * frontend renderer knows how to handle.
 */
class IndustryFieldRegistryTest {

    private static final Set<String> ALLOWED_TYPES = Set.of("text", "number", "date", "select", "boolean");

    private final IndustryFieldRegistry registry = new IndustryFieldRegistry();

    @Test
    @DisplayName("Every IndustryType value returns a non-null spec")
    void everyIndustryHasSpec() {
        for (IndustryType t : IndustryType.values()) {
            IndustryFieldSpec spec = registry.forIndustry(t);
            assertNotNull(spec, "Missing spec for " + t);
            assertNotNull(spec.item(), "spec.item() null for " + t);
            assertNotNull(spec.variant(), "spec.variant() null for " + t);
            assertNotNull(spec.labels(), "spec.labels() null for " + t);
        }
    }

    @Test
    @DisplayName("GENERAL industry has no extra fields and no label overrides")
    void generalIsEmpty() {
        IndustryFieldSpec spec = registry.forIndustry(IndustryType.GENERAL);
        assertTrue(spec.item().isEmpty());
        assertTrue(spec.variant().isEmpty());
        assertTrue(spec.labels().isEmpty());
    }

    @Test
    @DisplayName("Jewellery declares the four load-bearing fields the frontend needs")
    void jewelleryHasCoreFields() {
        IndustryFieldSpec spec = registry.forIndustry(IndustryType.JEWELLERY);
        Set<String> keys = spec.variant().stream().map(FieldSpec::key).collect(Collectors.toSet());

        assertTrue(keys.contains("metalType"),   "Jewellery must expose metalType");
        assertTrue(keys.contains("metalPurity"), "Jewellery must expose metalPurity");
        assertTrue(keys.contains("weightGrams"), "Jewellery must expose weightGrams");
        assertTrue(keys.contains("hallmarkNo"),  "Jewellery must expose hallmarkNo");
    }

    @Test
    @DisplayName("Electronics + Automobile expose their signature fields")
    void industrySignatureFields() {
        Set<String> elec = registry.forIndustry(IndustryType.ELECTRONICS).variant()
                .stream().map(FieldSpec::key).collect(Collectors.toSet());
        assertTrue(elec.contains("warrantyMonths"));

        Set<String> auto = registry.forIndustry(IndustryType.AUTOMOBILE).variant()
                .stream().map(FieldSpec::key).collect(Collectors.toSet());
        assertTrue(auto.contains("partNumber"));
    }

    @Test
    @DisplayName("Clothing overrides attribute1/2 labels to Fabric/Season without adding extra fields")
    void clothingRelabelsAttributes() {
        IndustryFieldSpec spec = registry.forIndustry(IndustryType.CLOTHING);
        assertEquals("Fabric", spec.labels().get("attribute1"));
        assertEquals("Season", spec.labels().get("attribute2"));
        // No extra fields — Clothing uses the shared attribute columns
        assertTrue(spec.variant().isEmpty(),
                "Clothing variant fields should stay empty; extras belong in future revisions");
    }

    @Test
    @DisplayName("Every FieldSpec.type is one of the widget types the frontend knows")
    void allFieldTypesAreRenderable() {
        for (IndustryType t : IndustryType.values()) {
            IndustryFieldSpec spec = registry.forIndustry(t);
            for (FieldSpec f : spec.variant()) {
                assertTrue(ALLOWED_TYPES.contains(f.type()),
                        "Unknown widget type '" + f.type() + "' on " + t + "." + f.key());
            }
            for (FieldSpec f : spec.item()) {
                assertTrue(ALLOWED_TYPES.contains(f.type()),
                        "Unknown widget type '" + f.type() + "' on " + t + "." + f.key());
            }
        }
    }

    @Test
    @DisplayName("Null / unknown industry falls back to GENERAL (empty spec)")
    void unknownIndustryFallsBackToGeneral() {
        IndustryFieldSpec spec = registry.forIndustry(null);
        assertTrue(spec.item().isEmpty());
        assertTrue(spec.variant().isEmpty());
        assertTrue(spec.labels().isEmpty());
    }
}
