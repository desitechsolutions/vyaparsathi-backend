package com.desitech.vyaparsathi.inventory.mapper;

import com.desitech.vyaparsathi.inventory.dto.ItemDto;
import com.desitech.vyaparsathi.inventory.dto.ItemVariantDto;
import com.desitech.vyaparsathi.inventory.entity.Category;
import com.desitech.vyaparsathi.inventory.entity.Item;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.repository.CategoryRepository;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import com.desitech.vyaparsathi.supplier.repository.SupplierRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.stream.Collectors;

/**
 * Hand-rolled DTO ↔ entity mapper for Item and ItemVariant.
 *
 * <p>Kept off MapStruct because the parent-item fields are flattened onto
 * the variant DTO for the UI, and the multi-industry field set makes a
 * generated mapper harder to audit than a plain one.
 *
 * <p><b>Migration V76 side-effects:</b>
 * <ul>
 *   <li>{@code gstCategory} is now mapped in both directions (previously
 *       silently dropped).</li>
 *   <li>{@code fabric} / {@code season} were dropped from the {@code item}
 *       table. The DTO no longer carries those fields — {@code attribute_1}
 *       / {@code attribute_2} are the sole source of truth.</li>
 *   <li>12 new industry-specific columns on {@code item_variant} round-trip.</li>
 * </ul>
 *
 * <p>{@code currentStock} on the variant DTO is populated to {@link BigDecimal#ZERO}
 * here as a safe default. Live stock is enriched by
 * {@code StockService.enrichCurrentStock(dto)} at the service layer; callers
 * that need real numbers must run through that helper. The mapper stays
 * repo-free so a batch of thousands of variants doesn't fan out into
 * thousands of stock queries.
 */
@Component
public class ItemMapper {

    @Autowired
    private CategoryRepository categoryRepository;

    // V79 — needed to resolve preferred/backup supplier IDs on write.
    // Optional-autowired so unit tests that construct the mapper without
    // Spring don't have to stub a supplier repo just to map a variant.
    @Autowired(required = false)
    private SupplierRepository supplierRepository;

    // ─── Item ─────────────────────────────────────────────────────────

    public ItemDto toDto(Item item) {
        if (item == null) return null;

        ItemDto dto = new ItemDto();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setDescription(item.getDescription());
        dto.setBrandName(item.getBrandName());
        dto.setAttribute1(item.getAttribute1());
        dto.setAttribute2(item.getAttribute2());
        dto.setSpecifications(item.getSpecifications());

        if (item.getCategory() != null) {
            dto.setCategoryId(item.getCategory().getId());
            dto.setCategoryName(item.getCategory().getName());
        }

        if (item.getVariants() != null) {
            dto.setVariants(item.getVariants().stream()
                    .map(this::toDto)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    public Item toEntity(ItemDto dto) {
        if (dto == null) return null;

        Item item = new Item();
        item.setId(dto.getId());
        item.setName(dto.getName());
        item.setDescription(dto.getDescription());
        item.setBrandName(dto.getBrandName());
        item.setAttribute1(dto.getAttribute1());
        item.setAttribute2(dto.getAttribute2());
        item.setSpecifications(dto.getSpecifications());

        if (dto.getCategoryId() != null) {
            Category category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new EntityNotFoundException("Category not found: " + dto.getCategoryId()));
            item.setCategory(category);
        }

        if (dto.getVariants() != null) {
            item.setVariants(dto.getVariants().stream()
                    .map(variantDto -> {
                        ItemVariant variant = toEntity(variantDto);
                        variant.setItem(item); // Crucial for JPA relationship
                        return variant;
                    })
                    .collect(Collectors.toList()));
        }

        return item;
    }

    // ─── ItemVariant ──────────────────────────────────────────────────

    public ItemVariantDto toDto(ItemVariant variant) {
        if (variant == null) return null;

        ItemVariantDto dto = new ItemVariantDto();
        dto.setId(variant.getId());
        dto.setSku(variant.getSku());
        dto.setUnit(variant.getUnit());
        dto.setPricePerUnit(variant.getPricePerUnit());
        dto.setHsn(variant.getHsn());
        dto.setGstRate(variant.getGstRate());
        dto.setGstCategory(variant.getGstCategory());   // FIX: was silently dropped
        dto.setPhotoPath(variant.getPhotoPath());
        dto.setColor(variant.getColor());
        dto.setSize(variant.getSize());
        dto.setDesign(variant.getDesign());
        dto.setFit(variant.getFit());
        dto.setLowStockThreshold(variant.getLowStockThreshold());

        // Batch / expiry / MRP / barcode (V30 + V50)
        dto.setBatchNumber(variant.getBatchNumber());
        dto.setManufacturingDate(variant.getManufacturingDate());
        dto.setExpiryDate(variant.getExpiryDate());
        dto.setMrp(variant.getMrp());
        dto.setBarcode(variant.getBarcode());

        // Industry-specific fields (V76)
        dto.setMetalType(variant.getMetalType());
        dto.setMetalPurity(variant.getMetalPurity());
        dto.setWeightGrams(variant.getWeightGrams());
        dto.setNetWeightGrams(variant.getNetWeightGrams());
        dto.setStoneWeightCarats(variant.getStoneWeightCarats());
        dto.setHallmarkNo(variant.getHallmarkNo());
        dto.setMakingChargesPerGram(variant.getMakingChargesPerGram());
        dto.setMakingChargesPct(variant.getMakingChargesPct());
        dto.setWarrantyMonths(variant.getWarrantyMonths());
        dto.setSerialNumber(variant.getSerialNumber());
        dto.setPartNumber(variant.getPartNumber());
        dto.setVehicleCompatibility(variant.getVehicleCompatibility());
        dto.setCustomAttributes(variant.getCustomAttributes());

        // Reorder rules (V79) — round-trip through the DTO so the item form
        // can display and edit them.
        dto.setReorderPoint(variant.getReorderPoint());
        dto.setReorderQty(variant.getReorderQty());
        dto.setSafetyStock(variant.getSafetyStock());
        dto.setMaxStock(variant.getMaxStock());
        dto.setLeadTimeDays(variant.getLeadTimeDays());
        if (variant.getPreferredSupplier() != null) {
            dto.setPreferredSupplierId(variant.getPreferredSupplier().getId());
            dto.setPreferredSupplierName(variant.getPreferredSupplier().getName());
        }
        if (variant.getBackupSupplier() != null) {
            dto.setBackupSupplierId(variant.getBackupSupplier().getId());
            dto.setBackupSupplierName(variant.getBackupSupplier().getName());
        }

        if (variant.getItem() != null) {
            Item parent = variant.getItem();
            dto.setItemId(parent.getId());
            dto.setItemName(parent.getName());
            dto.setBrand(parent.getBrandName());
            dto.setDescription(parent.getDescription());
            dto.setAttribute1(parent.getAttribute1());
            dto.setAttribute2(parent.getAttribute2());
            dto.setSpecifications(parent.getSpecifications());

            if (parent.getCategory() != null) {
                dto.setCategoryId(parent.getCategory().getId());
                dto.setCategoryName(parent.getCategory().getName());
            }
        }

        // Live stock is enriched separately by StockService.enrichCurrentStock.
        // Setting ZERO here rather than leaving null so downstream toString /
        // JSON serialisation never blows up on a null field.
        dto.setCurrentStock(BigDecimal.ZERO);
        return dto;
    }

    public ItemVariant toEntity(ItemVariantDto dto) {
        if (dto == null) return null;

        ItemVariant variant = new ItemVariant();
        variant.setId(dto.getId());
        variant.setSku(dto.getSku());
        variant.setUnit(dto.getUnit());
        variant.setPricePerUnit(dto.getPricePerUnit());
        variant.setHsn(dto.getHsn());
        variant.setGstRate(dto.getGstRate());
        // Fallback to TAXABLE mirrors the entity default — never persist
        // a null gstCategory since the column is NOT NULL.
        variant.setGstCategory(dto.getGstCategory() != null
                ? dto.getGstCategory()
                : com.desitech.vyaparsathi.gst.enums.GSTCategory.TAXABLE);
        variant.setPhotoPath(dto.getPhotoPath());
        variant.setColor(dto.getColor());
        variant.setSize(dto.getSize());
        variant.setDesign(dto.getDesign());
        variant.setFit(dto.getFit());
        variant.setLowStockThreshold(dto.getLowStockThreshold());

        // Batch / expiry / MRP / barcode
        variant.setBatchNumber(dto.getBatchNumber());
        variant.setManufacturingDate(dto.getManufacturingDate());
        variant.setExpiryDate(dto.getExpiryDate());
        variant.setMrp(dto.getMrp());
        variant.setBarcode(dto.getBarcode());

        // Industry-specific fields (V76)
        variant.setMetalType(dto.getMetalType());
        variant.setMetalPurity(dto.getMetalPurity());
        variant.setWeightGrams(dto.getWeightGrams());
        variant.setNetWeightGrams(dto.getNetWeightGrams());
        variant.setStoneWeightCarats(dto.getStoneWeightCarats());
        variant.setHallmarkNo(dto.getHallmarkNo());
        variant.setMakingChargesPerGram(dto.getMakingChargesPerGram());
        variant.setMakingChargesPct(dto.getMakingChargesPct());
        variant.setWarrantyMonths(dto.getWarrantyMonths());
        variant.setSerialNumber(dto.getSerialNumber());
        variant.setPartNumber(dto.getPartNumber());
        variant.setVehicleCompatibility(dto.getVehicleCompatibility());
        variant.setCustomAttributes(dto.getCustomAttributes());

        // Reorder rules (V79). Supplier IDs are resolved through the
        // repository; unknown IDs are silently ignored so a stale FE
        // payload can't null out an existing valid assignment.
        variant.setReorderPoint(dto.getReorderPoint());
        variant.setReorderQty(dto.getReorderQty());
        variant.setSafetyStock(dto.getSafetyStock());
        variant.setMaxStock(dto.getMaxStock());
        variant.setLeadTimeDays(dto.getLeadTimeDays());
        variant.setPreferredSupplier(resolveSupplier(dto.getPreferredSupplierId()));
        variant.setBackupSupplier(resolveSupplier(dto.getBackupSupplierId()));

        return variant;
    }

    private Supplier resolveSupplier(Long id) {
        if (id == null || supplierRepository == null) return null;
        return supplierRepository.findById(id).orElse(null);
    }
}
