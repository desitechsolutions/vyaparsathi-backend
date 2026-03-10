package com.desitech.vyaparsathi.inventory.mapper;

import com.desitech.vyaparsathi.inventory.dto.ItemDto;
import com.desitech.vyaparsathi.inventory.dto.ItemVariantDto;
import com.desitech.vyaparsathi.inventory.entity.Category;
import com.desitech.vyaparsathi.inventory.entity.Item;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.repository.CategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.stream.Collectors;

@Component
public class ItemMapper {

    @Autowired
    private CategoryRepository categoryRepository;

    /**
     * Converts an Item entity to an ItemDto.
     */
    public ItemDto toDto(Item item) {
        if (item == null) return null;

        ItemDto dto = new ItemDto();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setDescription(item.getDescription());
        dto.setBrandName(item.getBrandName());

        // Syncing the legacy fields with generic attributes
        // We ensure the DTO gets the value regardless of which column the DB used
        dto.setFabric(item.getFabric() != null ? item.getFabric() : item.getAttribute1());
        dto.setSeason(item.getSeason() != null ? item.getSeason() : item.getAttribute2());
        dto.setAttribute1(item.getAttribute1());
        dto.setAttribute2(item.getAttribute2());

        // Pharmacy fields
        dto.setDrugSchedule(item.getDrugSchedule());
        dto.setRequiresPrescription(item.getRequiresPrescription());
        dto.setComposition(item.getComposition());
        dto.setStorageRequirement(item.getStorageRequirement());

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

    /**
     * Converts an ItemDto to an Item entity.
     */
    public Item toEntity(ItemDto dto) {
        if (dto == null) return null;

        Item item = new Item();
        item.setId(dto.getId());
        item.setName(dto.getName());
        item.setDescription(dto.getDescription());
        item.setBrandName(dto.getBrandName());

        // If the shop is CLOTHING, 'fabric' and 'attribute1' are the same thing.
        // We set BOTH to ensure the database stays consistent.
        String val1 = dto.getAttribute1() != null ? dto.getAttribute1() : dto.getFabric();
        String val2 = dto.getAttribute2() != null ? dto.getAttribute2() : dto.getSeason();

        item.setAttribute1(val1);
        item.setFabric(val1);
        item.setAttribute2(val2);
        item.setSeason(val2);

        // Pharmacy fields
        item.setDrugSchedule(dto.getDrugSchedule());
        item.setRequiresPrescription(dto.getRequiresPrescription());
        item.setComposition(dto.getComposition());
        item.setStorageRequirement(dto.getStorageRequirement());

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

    public ItemVariantDto toDto(ItemVariant itemVariant) {
        if (itemVariant == null) return null;

        ItemVariantDto dto = new ItemVariantDto();
        dto.setId(itemVariant.getId());
        dto.setSku(itemVariant.getSku());
        dto.setUnit(itemVariant.getUnit());
        dto.setPricePerUnit(itemVariant.getPricePerUnit());
        dto.setHsn(itemVariant.getHsn());
        dto.setGstRate(itemVariant.getGstRate());
        dto.setPhotoPath(itemVariant.getPhotoPath());
        dto.setColor(itemVariant.getColor());
        dto.setSize(itemVariant.getSize());
        dto.setDesign(itemVariant.getDesign());
        dto.setFit(itemVariant.getFit());
        dto.setLowStockThreshold(itemVariant.getLowStockThreshold());

        // Pharmacy fields
        dto.setBatchNumber(itemVariant.getBatchNumber());
        dto.setManufacturingDate(itemVariant.getManufacturingDate());
        dto.setExpiryDate(itemVariant.getExpiryDate());
        dto.setMrp(itemVariant.getMrp());
        dto.setIsLooseMedicine(itemVariant.getIsLooseMedicine());
        dto.setPackSize(itemVariant.getPackSize());

        if (itemVariant.getItem() != null) {
            Item parent = itemVariant.getItem();
            dto.setItemId(parent.getId());
            dto.setItemName(parent.getName());
            dto.setBrand(parent.getBrandName());
            dto.setDescription(parent.getDescription());

            dto.setAttribute1(parent.getAttribute1() != null ? parent.getAttribute1() : parent.getFabric());
            dto.setAttribute2(parent.getAttribute2() != null ? parent.getAttribute2() : parent.getSeason());
            dto.setComposition(parent.getComposition());

            // Pharmacy fields from parent item
            dto.setDrugSchedule(parent.getDrugSchedule());
            dto.setRequiresPrescription(parent.getRequiresPrescription());

            if (parent.getCategory() != null) {
                dto.setCategoryId(parent.getCategory().getId());
                dto.setCategoryName(parent.getCategory().getName());
            }
        }

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
        variant.setPhotoPath(dto.getPhotoPath());
        variant.setColor(dto.getColor());
        variant.setSize(dto.getSize());
        variant.setDesign(dto.getDesign());
        variant.setFit(dto.getFit());
        variant.setLowStockThreshold(dto.getLowStockThreshold());

        // Pharmacy fields
        variant.setBatchNumber(dto.getBatchNumber());
        variant.setManufacturingDate(dto.getManufacturingDate());
        variant.setExpiryDate(dto.getExpiryDate());
        variant.setMrp(dto.getMrp());
        variant.setIsLooseMedicine(dto.getIsLooseMedicine() != null ? dto.getIsLooseMedicine() : false);
        variant.setPackSize(dto.getPackSize());

        return variant;
    }
}