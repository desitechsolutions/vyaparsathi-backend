package com.desitech.vyaparsathi.common.config;

import com.desitech.vyaparsathi.shop.enums.IndustryType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Single source of truth for which industry-specific fields each shop
 * type surfaces on its item / variant forms.
 *
 * <p>Adding a new industry is a change to {@link IndustryType} plus a
 * new entry in {@link #buildRegistry()}. The frontend fetches the spec
 * at boot via {@link IndustryConfigController} and renders whatever
 * arrives — no frontend redeploy is required for a label tweak.
 *
 * <p>Fields listed here MUST already exist on {@link
 * com.desitech.vyaparsathi.inventory.entity.ItemVariant} (or, for Phase
 * 4 custom attributes, in the JSON blob). Anything else round-trips
 * through {@code null} at the mapper layer.
 */
@Component
public class IndustryFieldRegistry {

    private final Map<IndustryType, IndustryFieldSpec> registry;

    public IndustryFieldRegistry() {
        this.registry = buildRegistry();
    }

    public IndustryFieldSpec forIndustry(IndustryType industry) {
        if (industry == null) industry = IndustryType.GENERAL;
        return registry.getOrDefault(industry, empty());
    }

    private IndustryFieldSpec empty() {
        return new IndustryFieldSpec(List.of(), List.of(), Map.of());
    }

    private Map<IndustryType, IndustryFieldSpec> buildRegistry() {
        return Map.of(
                IndustryType.JEWELLERY, jewellery(),
                IndustryType.ELECTRONICS, electronics(),
                IndustryType.AUTOMOBILE, automobile(),
                IndustryType.CLOTHING, clothing(),
                IndustryType.HARDWARE, hardware(),
                IndustryType.STATIONERY, stationery(),
                IndustryType.GROCERY, grocery(),
                IndustryType.FOOTWEAR, footwear(),
                IndustryType.FURNITURE, furniture(),
                IndustryType.GENERAL, empty()
        );
    }

    // ── Industry-specific specs ───────────────────────────────────────

    private IndustryFieldSpec jewellery() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.select("metalType", "Metal", true,
                                List.of("Gold", "Silver", "Platinum", "Diamond", "Other"), "variant"),
                        FieldSpec.text("metalPurity", "Purity (e.g. 22K, 925)", true, "variant"),
                        FieldSpec.number("weightGrams", "Gross weight (g)", true, 0.0, null, "variant"),
                        FieldSpec.number("netWeightGrams", "Net weight (g)", false, 0.0, null, "variant"),
                        FieldSpec.number("stoneWeightCarats", "Stone weight (ct)", false, 0.0, null, "variant"),
                        FieldSpec.text("hallmarkNo", "Hallmark / HUID", false, "variant"),
                        FieldSpec.number("makingChargesPerGram", "Making charges / gram", false, 0.0, null, "variant"),
                        FieldSpec.number("makingChargesPct", "Making charges (%)", false, 0.0, 100.0, "variant")
                ),
                Map.of(
                        "attribute1", "Craftsmanship",
                        "attribute2", "Occasion"
                )
        );
    }

    private IndustryFieldSpec electronics() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.number("warrantyMonths", "Warranty (months)", false, 0.0, 240.0, "variant"),
                        FieldSpec.text("serialNumber", "Serial number", false, "variant")
                ),
                Map.of(
                        "attribute1", "Model",
                        "attribute2", "Specifications"
                )
        );
    }

    private IndustryFieldSpec automobile() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.text("partNumber", "Part number", false, "variant"),
                        FieldSpec.text("vehicleCompatibility", "Vehicle compatibility", false, "variant")
                ),
                Map.of(
                        "attribute1", "Part type",
                        "attribute2", "Position (front / rear)"
                )
        );
    }

    private IndustryFieldSpec clothing() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(),
                Map.of(
                        "attribute1", "Fabric",
                        "attribute2", "Season"
                )
        );
    }

    private IndustryFieldSpec hardware() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(),
                Map.of(
                        "attribute1", "Material",
                        "attribute2", "Grade / Class"
                )
        );
    }

    private IndustryFieldSpec stationery() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(),
                Map.of(
                        "attribute1", "Material",
                        "attribute2", "Intended use"
                )
        );
    }

    private IndustryFieldSpec grocery() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(),
                Map.of(
                        "attribute1", "Packaging",
                        "attribute2", "Dietary info"
                )
        );
    }

    private IndustryFieldSpec footwear() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(),
                Map.of(
                        "attribute1", "Upper material",
                        "attribute2", "Sole type"
                )
        );
    }

    private IndustryFieldSpec furniture() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(),
                Map.of(
                        "attribute1", "Material",
                        "attribute2", "Assembly"
                )
        );
    }
}
