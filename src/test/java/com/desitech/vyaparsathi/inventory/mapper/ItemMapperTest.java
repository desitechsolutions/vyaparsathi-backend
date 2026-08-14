package com.desitech.vyaparsathi.inventory.mapper;

import com.desitech.vyaparsathi.gst.enums.GSTCategory;
import com.desitech.vyaparsathi.inventory.dto.ItemDto;
import com.desitech.vyaparsathi.inventory.dto.ItemVariantDto;
import com.desitech.vyaparsathi.inventory.entity.Category;
import com.desitech.vyaparsathi.inventory.entity.Item;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Verifies the DTO ↔ entity contract for {@link ItemMapper}.
 *
 * <p>The regressions this test guards against are the exact bugs the
 * V76 refactor set out to fix:
 * <ul>
 *   <li>{@code gstCategory} silently dropped on both directions.</li>
 *   <li>Industry-specific columns (jewellery, electronics, automobile)
 *       missing from the mapper entirely.</li>
 *   <li>{@code fabric} / {@code season} legacy dual-write hack — the
 *       tests below assert the DTO has no such fields any more.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ItemMapperTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ItemMapper mapper;

    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category();
        setId(category, 42L);
        category.setName("Rings");
    }

    // ── gstCategory (the silent-drop bug fix) ────────────────────────

    @Test
    @DisplayName("toDto: gstCategory is copied (previously dropped)")
    void gstCategoryRoundTripsToDto() {
        ItemVariant variant = baseVariant();
        variant.setGstCategory(GSTCategory.EXEMPT);

        ItemVariantDto dto = mapper.toDto(variant);

        assertEquals(GSTCategory.EXEMPT, dto.getGstCategory());
    }

    @Test
    @DisplayName("toEntity: gstCategory is copied; null falls back to TAXABLE default")
    void gstCategoryRoundTripsToEntity() {
        ItemVariantDto dto = baseVariantDto();
        dto.setGstCategory(GSTCategory.NIL_RATED);

        ItemVariant entity = mapper.toEntity(dto);

        assertEquals(GSTCategory.NIL_RATED, entity.getGstCategory());

        // A missing gstCategory MUST NOT persist as NULL — the column is NOT NULL.
        ItemVariantDto dtoWithoutCat = baseVariantDto();
        dtoWithoutCat.setGstCategory(null);
        ItemVariant coerced = mapper.toEntity(dtoWithoutCat);
        assertEquals(GSTCategory.TAXABLE, coerced.getGstCategory(),
                "Null gstCategory in DTO should coerce to TAXABLE, not persist null");
    }

    // ── Industry-specific fields (V76 additions) ─────────────────────

    @Test
    @DisplayName("Jewellery fields round-trip through the mapper")
    void jewelleryFieldsRoundTrip() {
        ItemVariant e = baseVariant();
        e.setMetalType("Gold");
        e.setMetalPurity("22K");
        e.setWeightGrams(new BigDecimal("15.750"));
        e.setNetWeightGrams(new BigDecimal("14.200"));
        e.setStoneWeightCarats(new BigDecimal("0.500"));
        e.setHallmarkNo("HUID-ABC123");
        e.setMakingChargesPerGram(new BigDecimal("450.00"));
        e.setMakingChargesPct(new BigDecimal("12.50"));

        ItemVariantDto dto = mapper.toDto(e);

        assertEquals("Gold", dto.getMetalType());
        assertEquals("22K", dto.getMetalPurity());
        assertEquals(0, new BigDecimal("15.750").compareTo(dto.getWeightGrams()));
        assertEquals(0, new BigDecimal("14.200").compareTo(dto.getNetWeightGrams()));
        assertEquals(0, new BigDecimal("0.500").compareTo(dto.getStoneWeightCarats()));
        assertEquals("HUID-ABC123", dto.getHallmarkNo());
        assertEquals(0, new BigDecimal("450.00").compareTo(dto.getMakingChargesPerGram()));
        assertEquals(0, new BigDecimal("12.50").compareTo(dto.getMakingChargesPct()));

        ItemVariant back = mapper.toEntity(dto);
        assertEquals("Gold", back.getMetalType());
        assertEquals("22K", back.getMetalPurity());
        assertEquals(0, new BigDecimal("15.750").compareTo(back.getWeightGrams()));
        assertEquals("HUID-ABC123", back.getHallmarkNo());
    }

    @Test
    @DisplayName("Electronics fields round-trip")
    void electronicsFieldsRoundTrip() {
        ItemVariant e = baseVariant();
        e.setWarrantyMonths(24);
        e.setSerialNumber("SN-9911");

        ItemVariantDto dto = mapper.toDto(e);
        assertEquals(24, dto.getWarrantyMonths());
        assertEquals("SN-9911", dto.getSerialNumber());

        ItemVariant back = mapper.toEntity(dto);
        assertEquals(24, back.getWarrantyMonths());
        assertEquals("SN-9911", back.getSerialNumber());
    }

    @Test
    @DisplayName("Automobile fields round-trip")
    void automobileFieldsRoundTrip() {
        ItemVariant e = baseVariant();
        e.setPartNumber("PT-12345");
        e.setVehicleCompatibility("Maruti Swift 2015-2020");

        ItemVariantDto dto = mapper.toDto(e);
        assertEquals("PT-12345", dto.getPartNumber());
        assertEquals("Maruti Swift 2015-2020", dto.getVehicleCompatibility());

        ItemVariant back = mapper.toEntity(dto);
        assertEquals("PT-12345", back.getPartNumber());
        assertEquals("Maruti Swift 2015-2020", back.getVehicleCompatibility());
    }

    // ── Batch / expiry / barcode (V30 + V50) ─────────────────────────

    @Test
    @DisplayName("Batch / expiry / MRP / barcode round-trip on the variant")
    void retailTraceabilityRoundTrip() {
        ItemVariant e = baseVariant();
        e.setBatchNumber("LOT-A11");
        e.setManufacturingDate(LocalDate.of(2026, 1, 15));
        e.setExpiryDate(LocalDate.of(2027, 1, 15));
        e.setMrp(new BigDecimal("999.00"));
        e.setBarcode("8901234567890");

        ItemVariantDto dto = mapper.toDto(e);
        assertEquals("LOT-A11", dto.getBatchNumber());
        assertEquals(LocalDate.of(2026, 1, 15), dto.getManufacturingDate());
        assertEquals(LocalDate.of(2027, 1, 15), dto.getExpiryDate());
        assertEquals(0, new BigDecimal("999.00").compareTo(dto.getMrp()));
        assertEquals("8901234567890", dto.getBarcode());
    }

    // ── Parent item flatten + attribute1/attribute2 (fabric/season gone) ──

    @Test
    @DisplayName("Parent item's attribute1 / attribute2 are flattened onto the variant DTO")
    void parentAttributesFlatten() {
        Item parent = new Item();
        setId(parent, 7L);
        parent.setName("Silk Kurta");
        parent.setBrandName("Fabindia");
        parent.setAttribute1("Silk");
        parent.setAttribute2("Winter");
        parent.setSpecifications("Hand-woven");

        ItemVariant e = baseVariant();
        e.setItem(parent);

        ItemVariantDto dto = mapper.toDto(e);

        assertEquals("Silk Kurta", dto.getItemName());
        assertEquals("Fabindia", dto.getBrand());
        assertEquals("Silk", dto.getAttribute1());
        assertEquals("Winter", dto.getAttribute2());
        assertEquals("Hand-woven", dto.getSpecifications());
    }

    @Test
    @DisplayName("ItemVariantDto no longer exposes fabric / season fields (V76 cleanup)")
    void dtoHasNoLegacyFabricSeasonFields() {
        // Grep-style guardrail — if anyone re-adds fabric/season back,
        // this fails and forces a conscious decision.
        for (Field f : ItemVariantDto.class.getDeclaredFields()) {
            assertNotEquals("fabric", f.getName(),
                    "fabric field must stay removed after V76 refactor");
            assertNotEquals("season", f.getName(),
                    "season field must stay removed after V76 refactor");
        }
        for (Field f : ItemDto.class.getDeclaredFields()) {
            assertNotEquals("fabric", f.getName(),
                    "fabric field must stay removed from ItemDto after V76 refactor");
            assertNotEquals("season", f.getName(),
                    "season field must stay removed from ItemDto after V76 refactor");
        }
    }

    // ── Item-level mapping ───────────────────────────────────────────

    @Test
    @DisplayName("Item toEntity resolves category by id and applies attribute1/2")
    void itemToEntityResolvesCategory() {
        when(categoryRepository.findById(42L)).thenReturn(Optional.of(category));

        ItemDto dto = new ItemDto();
        dto.setName("Ring");
        dto.setCategoryId(42L);
        dto.setAttribute1("22K");
        dto.setAttribute2("Traditional");

        Item entity = mapper.toEntity(dto);

        assertEquals("Ring", entity.getName());
        assertEquals("22K", entity.getAttribute1());
        assertEquals("Traditional", entity.getAttribute2());
        assertNotNull(entity.getCategory());
        assertEquals("Rings", entity.getCategory().getName());
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private ItemVariant baseVariant() {
        ItemVariant v = new ItemVariant();
        setId(v, 1L);
        v.setSku("SKU-1");
        v.setUnit("pcs");
        v.setPricePerUnit(new BigDecimal("100.00"));
        v.setGstCategory(GSTCategory.TAXABLE);
        return v;
    }

    private ItemVariantDto baseVariantDto() {
        ItemVariantDto d = new ItemVariantDto();
        d.setSku("SKU-1");
        d.setUnit("pcs");
        d.setPricePerUnit(new BigDecimal("100.00"));
        return d;
    }

    // BaseEntity.setId is protected via inheritance in most codebases;
    // reflect if that's the case here so the test doesn't depend on
    // package-private state.
    private static void setId(Object entity, Long id) {
        try {
            Class<?> c = entity.getClass();
            while (c != null && !hasField(c, "id")) c = c.getSuperclass();
            if (c == null) throw new IllegalStateException("id field not found");
            Field f = c.getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean hasField(Class<?> c, String name) {
        for (Field f : c.getDeclaredFields()) if (f.getName().equals(name)) return true;
        return false;
    }
}
