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
        // Map.of() has a 10-entry cap — Map.ofEntries() lets the registry grow
        // safely as we add new IndustryType values.
        return Map.ofEntries(
                // Original 10
                Map.entry(IndustryType.JEWELLERY, jewellery()),
                Map.entry(IndustryType.ELECTRONICS, electronics()),
                Map.entry(IndustryType.AUTOMOBILE, automobile()),
                Map.entry(IndustryType.CLOTHING, clothing()),
                Map.entry(IndustryType.HARDWARE, hardware()),
                Map.entry(IndustryType.STATIONERY, stationery()),
                Map.entry(IndustryType.GROCERY, grocery()),
                Map.entry(IndustryType.FOOTWEAR, footwear()),
                Map.entry(IndustryType.FURNITURE, furniture()),
                Map.entry(IndustryType.GENERAL, empty()),

                // Enterprise expansion (Phase 4)
                Map.entry(IndustryType.BUILDING_MATERIALS, buildingMaterials()),
                Map.entry(IndustryType.RESTAURANT, restaurant()),
                Map.entry(IndustryType.BAKERY, bakery()),
                Map.entry(IndustryType.DAIRY, dairy()),
                Map.entry(IndustryType.SUPERMARKET, supermarket()),
                Map.entry(IndustryType.COSMETICS, cosmetics()),
                Map.entry(IndustryType.OPTICAL, optical()),
                Map.entry(IndustryType.AGRICULTURE, agriculture()),
                Map.entry(IndustryType.SPORTS, sports()),
                Map.entry(IndustryType.BOOKS, books()),
                Map.entry(IndustryType.TOYS, toys()),
                Map.entry(IndustryType.MOBILE_ACCESSORIES, mobileAccessories()),
                Map.entry(IndustryType.HOME_APPLIANCES, homeAppliances()),
                Map.entry(IndustryType.KITCHENWARE, kitchenware()),
                Map.entry(IndustryType.TEXTILE, textile()),
                Map.entry(IndustryType.PAINT, paint()),
                Map.entry(IndustryType.SANITARY_TILES, sanitaryTiles()),
                Map.entry(IndustryType.MEDICAL_EQUIPMENT, medicalEquipment()),
                Map.entry(IndustryType.PET_SUPPLIES, petSupplies()),
                Map.entry(IndustryType.MUSICAL_INSTRUMENTS, musicalInstruments()),
                Map.entry(IndustryType.FLORIST, florist()),
                Map.entry(IndustryType.HANDICRAFTS, handicrafts()),
                Map.entry(IndustryType.SALON_SPA, salonSpa()),
                Map.entry(IndustryType.LAUNDRY, laundry()),
                Map.entry(IndustryType.SERVICES, services()),
                Map.entry(IndustryType.WHOLESALE, wholesale()),
                Map.entry(IndustryType.MANUFACTURING, manufacturing())
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

    // ── Phase 4 enterprise expansion ──────────────────────────────────

    private IndustryFieldSpec buildingMaterials() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.select("productType", "Product type", true,
                                List.of("Cement", "Sand / Balu", "Steel Rod", "Aggregate / Gitti", "Brick", "TMT Bar", "Ready-mix Concrete", "Other"), "variant"),
                        FieldSpec.text("grade", "Grade / Class (e.g. OPC 43, Fe500)", false, "variant"),
                        FieldSpec.text("dimensions", "Dimensions (e.g. 12mm, 20mm)", false, "variant"),
                        FieldSpec.number("weightKg", "Weight per unit (kg)", false, 0.0, null, "variant"),
                        FieldSpec.text("packing", "Packing (bag / bundle / loose)", false, "variant")
                ),
                Map.of(
                        "attribute1", "Manufacturer / Brand",
                        "attribute2", "Origin / Source"
                )
        );
    }

    private IndustryFieldSpec restaurant() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.select("portionSize", "Portion size", false,
                                List.of("Regular", "Half", "Full", "Family"), "variant"),
                        FieldSpec.select("spiceLevel", "Spice level", false,
                                List.of("Mild", "Medium", "Spicy", "Extra Spicy"), "variant"),
                        FieldSpec.boolFlag("veg", "Vegetarian", false, "variant")
                ),
                Map.of(
                        "attribute1", "Cuisine",
                        "attribute2", "Course"
                )
        );
    }

    private IndustryFieldSpec bakery() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.number("weightGrams", "Weight (g)", false, 0.0, null, "variant"),
                        FieldSpec.boolFlag("eggless", "Eggless", false, "variant"),
                        FieldSpec.number("shelfLifeDays", "Shelf life (days)", false, 0.0, null, "variant")
                ),
                Map.of(
                        "attribute1", "Flavour",
                        "attribute2", "Occasion"
                )
        );
    }

    private IndustryFieldSpec dairy() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.number("volumeMl", "Volume (ml)", false, 0.0, null, "variant"),
                        FieldSpec.number("fatPct", "Fat (%)", false, 0.0, 100.0, "variant"),
                        FieldSpec.number("shelfLifeDays", "Shelf life (days)", false, 0.0, null, "variant")
                ),
                Map.of(
                        "attribute1", "Product form",
                        "attribute2", "Packaging"
                )
        );
    }

    private IndustryFieldSpec supermarket() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.text("barcode", "Barcode / EAN", false, "variant"),
                        FieldSpec.date("expiryDate", "Expiry date", false, "variant")
                ),
                Map.of(
                        "attribute1", "Category",
                        "attribute2", "Brand"
                )
        );
    }

    private IndustryFieldSpec cosmetics() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.text("shade", "Shade", false, "variant"),
                        FieldSpec.select("skinType", "Skin type", false,
                                List.of("All", "Oily", "Dry", "Combination", "Sensitive"), "variant"),
                        FieldSpec.date("expiryDate", "Expiry date", false, "variant")
                ),
                Map.of(
                        "attribute1", "Brand",
                        "attribute2", "Application"
                )
        );
    }

    private IndustryFieldSpec optical() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.select("frameType", "Frame type", false,
                                List.of("Full-rim", "Half-rim", "Rimless", "Sunglasses"), "variant"),
                        FieldSpec.text("frameMaterial", "Frame material", false, "variant"),
                        FieldSpec.text("lensType", "Lens type", false, "variant"),
                        FieldSpec.boolFlag("prescriptionRequired", "Prescription required", false, "variant")
                ),
                Map.of(
                        "attribute1", "Brand",
                        "attribute2", "Gender / Age group"
                )
        );
    }

    private IndustryFieldSpec agriculture() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.select("productType", "Product type", false,
                                List.of("Seed", "Fertilizer", "Pesticide", "Tool", "Feed", "Other"), "variant"),
                        FieldSpec.text("crop", "Crop / target", false, "variant"),
                        FieldSpec.number("weightKg", "Weight (kg)", false, 0.0, null, "variant"),
                        FieldSpec.date("expiryDate", "Expiry / best-before", false, "variant")
                ),
                Map.of(
                        "attribute1", "Manufacturer",
                        "attribute2", "Certification"
                )
        );
    }

    private IndustryFieldSpec sports() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.text("sport", "Sport", false, "variant"),
                        FieldSpec.text("size", "Size", false, "variant"),
                        FieldSpec.text("skillLevel", "Skill level", false, "variant")
                ),
                Map.of(
                        "attribute1", "Brand",
                        "attribute2", "Age group"
                )
        );
    }

    private IndustryFieldSpec books() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.text("isbn", "ISBN", false, "variant"),
                        FieldSpec.text("author", "Author", false, "variant"),
                        FieldSpec.text("publisher", "Publisher", false, "variant"),
                        FieldSpec.text("language", "Language", false, "variant"),
                        FieldSpec.text("edition", "Edition", false, "variant")
                ),
                Map.of(
                        "attribute1", "Genre / Subject",
                        "attribute2", "Format"
                )
        );
    }

    private IndustryFieldSpec toys() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.text("ageGroup", "Age group", false, "variant"),
                        FieldSpec.text("material", "Material", false, "variant"),
                        FieldSpec.boolFlag("batteryIncluded", "Battery included", false, "variant")
                ),
                Map.of(
                        "attribute1", "Brand",
                        "attribute2", "Category (educational / outdoor)"
                )
        );
    }

    private IndustryFieldSpec mobileAccessories() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.text("compatibleModel", "Compatible model", false, "variant"),
                        FieldSpec.text("connector", "Connector type (Type-C, Lightning)", false, "variant"),
                        FieldSpec.number("warrantyMonths", "Warranty (months)", false, 0.0, 60.0, "variant")
                ),
                Map.of(
                        "attribute1", "Brand",
                        "attribute2", "Accessory type"
                )
        );
    }

    private IndustryFieldSpec homeAppliances() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.text("energyRating", "Energy rating (stars)", false, "variant"),
                        FieldSpec.number("capacity", "Capacity", false, 0.0, null, "variant"),
                        FieldSpec.number("warrantyMonths", "Warranty (months)", false, 0.0, 240.0, "variant"),
                        FieldSpec.text("serialNumber", "Serial number", false, "variant")
                ),
                Map.of(
                        "attribute1", "Brand",
                        "attribute2", "Appliance type"
                )
        );
    }

    private IndustryFieldSpec kitchenware() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.text("material", "Material (steel / brass / non-stick)", false, "variant"),
                        FieldSpec.number("capacityLitres", "Capacity (L)", false, 0.0, null, "variant"),
                        FieldSpec.boolFlag("inductionFriendly", "Induction-friendly", false, "variant")
                ),
                Map.of(
                        "attribute1", "Brand",
                        "attribute2", "Utensil type"
                )
        );
    }

    private IndustryFieldSpec textile() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.text("fabricType", "Fabric type", false, "variant"),
                        FieldSpec.number("widthCm", "Width (cm)", false, 0.0, null, "variant"),
                        FieldSpec.number("gsm", "GSM", false, 0.0, null, "variant"),
                        FieldSpec.text("pattern", "Pattern / Print", false, "variant")
                ),
                Map.of(
                        "attribute1", "Composition",
                        "attribute2", "Colour family"
                )
        );
    }

    private IndustryFieldSpec paint() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.select("paintType", "Paint type", false,
                                List.of("Interior emulsion", "Exterior emulsion", "Enamel", "Primer", "Distemper", "Waterproofing", "Wood finish"), "variant"),
                        FieldSpec.text("shadeCode", "Shade code", false, "variant"),
                        FieldSpec.text("finish", "Finish (matte / gloss)", false, "variant"),
                        FieldSpec.number("volumeLitres", "Volume (L)", false, 0.0, null, "variant")
                ),
                Map.of(
                        "attribute1", "Brand",
                        "attribute2", "Base"
                )
        );
    }

    private IndustryFieldSpec sanitaryTiles() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.select("productType", "Product type", false,
                                List.of("Floor tile", "Wall tile", "Sanitary ware", "Faucet", "Bath fitting", "Accessory"), "variant"),
                        FieldSpec.text("size", "Size (e.g. 600x1200 mm)", false, "variant"),
                        FieldSpec.text("finish", "Finish (glossy / matte)", false, "variant"),
                        FieldSpec.number("pcsPerBox", "Pieces per box", false, 0.0, null, "variant")
                ),
                Map.of(
                        "attribute1", "Brand",
                        "attribute2", "Series / Collection"
                )
        );
    }

    private IndustryFieldSpec medicalEquipment() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.text("modelNumber", "Model number", false, "variant"),
                        FieldSpec.text("regulatoryLicense", "Regulatory license (CDSCO / BIS)", false, "variant"),
                        FieldSpec.number("warrantyMonths", "Warranty (months)", false, 0.0, 240.0, "variant"),
                        FieldSpec.date("expiryDate", "Sterility / expiry date", false, "variant")
                ),
                Map.of(
                        "attribute1", "Manufacturer",
                        "attribute2", "Category (diagnostic / therapeutic)"
                )
        );
    }

    private IndustryFieldSpec petSupplies() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.select("petType", "Pet type", false,
                                List.of("Dog", "Cat", "Bird", "Fish", "Small pet", "Other"), "variant"),
                        FieldSpec.text("lifeStage", "Life stage (puppy / adult / senior)", false, "variant"),
                        FieldSpec.date("expiryDate", "Best-before / expiry", false, "variant")
                ),
                Map.of(
                        "attribute1", "Brand",
                        "attribute2", "Product category"
                )
        );
    }

    private IndustryFieldSpec musicalInstruments() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.text("instrumentType", "Instrument type", false, "variant"),
                        FieldSpec.text("serialNumber", "Serial number", false, "variant"),
                        FieldSpec.number("warrantyMonths", "Warranty (months)", false, 0.0, 240.0, "variant")
                ),
                Map.of(
                        "attribute1", "Brand",
                        "attribute2", "Skill level"
                )
        );
    }

    private IndustryFieldSpec florist() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.text("flowerType", "Flower type", false, "variant"),
                        FieldSpec.select("occasion", "Occasion", false,
                                List.of("Wedding", "Birthday", "Anniversary", "Sympathy", "Corporate", "Everyday"), "variant"),
                        FieldSpec.number("shelfLifeDays", "Freshness (days)", false, 0.0, null, "variant")
                ),
                Map.of(
                        "attribute1", "Bouquet size",
                        "attribute2", "Colour"
                )
        );
    }

    private IndustryFieldSpec handicrafts() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.text("material", "Material", false, "variant"),
                        FieldSpec.text("origin", "Origin (state / cluster)", false, "variant"),
                        FieldSpec.boolFlag("handmade", "Handmade", false, "variant"),
                        FieldSpec.text("artisan", "Artisan / Cluster", false, "variant")
                ),
                Map.of(
                        "attribute1", "Craft technique",
                        "attribute2", "Style"
                )
        );
    }

    private IndustryFieldSpec salonSpa() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.select("serviceType", "Service type", false,
                                List.of("Haircare", "Skincare", "Nails", "Massage", "Waxing", "Other"), "variant"),
                        FieldSpec.number("durationMinutes", "Duration (min)", false, 0.0, null, "variant"),
                        FieldSpec.select("gender", "Applicable to", false,
                                List.of("Unisex", "Female", "Male"), "variant")
                ),
                Map.of(
                        "attribute1", "Service category",
                        "attribute2", "Level (basic / premium)"
                )
        );
    }

    private IndustryFieldSpec laundry() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.select("serviceType", "Service type", false,
                                List.of("Wash", "Wash + Iron", "Dry-clean", "Iron only", "Steam iron"), "variant"),
                        FieldSpec.number("turnaroundHours", "Turnaround (hours)", false, 0.0, null, "variant")
                ),
                Map.of(
                        "attribute1", "Garment type",
                        "attribute2", "Pricing basis (per piece / per kg)"
                )
        );
    }

    private IndustryFieldSpec services() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.select("billingBasis", "Billing basis", false,
                                List.of("Fixed", "Per hour", "Per day", "Per project", "Retainer"), "variant"),
                        FieldSpec.number("durationHours", "Estimated duration (h)", false, 0.0, null, "variant")
                ),
                Map.of(
                        "attribute1", "Service area",
                        "attribute2", "Skill level"
                )
        );
    }

    private IndustryFieldSpec wholesale() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.number("moqUnits", "Minimum order qty", false, 0.0, null, "variant"),
                        FieldSpec.text("packSize", "Pack size", false, "variant"),
                        FieldSpec.text("barcode", "Carton barcode", false, "variant")
                ),
                Map.of(
                        "attribute1", "Product category",
                        "attribute2", "Brand / Vendor"
                )
        );
    }

    private IndustryFieldSpec manufacturing() {
        return new IndustryFieldSpec(
                List.of(),
                List.of(
                        FieldSpec.text("skuCode", "Internal SKU", false, "variant"),
                        FieldSpec.text("batchNumber", "Batch / Lot", false, "variant"),
                        FieldSpec.number("productionCost", "Production cost", false, 0.0, null, "variant"),
                        FieldSpec.date("manufactureDate", "Manufacture date", false, "variant")
                ),
                Map.of(
                        "attribute1", "Product line",
                        "attribute2", "Finish / Grade"
                )
        );
    }
}
