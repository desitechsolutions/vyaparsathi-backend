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
        if (item == null) {
            return null;
        }

        ItemDto dto = new ItemDto();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setDescription(item.getDescription());
        dto.setBrandName(item.getBrandName());
        // Map new attributes
        dto.setFabric(item.getFabric());
        dto.setSeason(item.getSeason());

        // Map the category relationship
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
        if (dto == null) {
            return null;
        }

        Item item = new Item();
        item.setId(dto.getId());
        item.setName(dto.getName());
        item.setDescription(dto.getDescription());
        item.setBrandName(dto.getBrandName());
        // Map new attributes
        item.setFabric(dto.getFabric());
        item.setSeason(dto.getSeason());

        // Map the category relationship using the repository
        if (dto.getCategoryId() != null) {
            Category category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + dto.getCategoryId()));
            item.setCategory(category);
        }

        if (dto.getVariants() != null) {
            item.setVariants(dto.getVariants().stream()
                    .map(variantDto -> {
                        ItemVariant variant = toEntity(variantDto);
                        variant.setItem(item);
                        return variant;
                    })
                    .collect(Collectors.toList()));
        }

        return item;
    }

    /**
     * Converts a single ItemVariant entity to a flattened ItemVariantDto.
     * This DTO is rich with parent item info, useful for lists and sales pages.
     */
    public ItemVariantDto toDto(ItemVariant itemVariant) {
        if (itemVariant == null) {
            return null;
        }

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
        dto.setFit(itemVariant.getFit()); // Map new 'fit' attribute
        dto.setLowStockThreshold(itemVariant.getLowStockThreshold());

        if (itemVariant.getItem() != null) {
            Item parentItem = itemVariant.getItem();
            dto.setItemName(parentItem.getName());
            dto.setBrand(parentItem.getBrandName());
            dto.setItemId(parentItem.getId());
            dto.setDescription(parentItem.getDescription());
            // Map new parent attributes
            dto.setFabric(parentItem.getFabric());
            dto.setSeason(parentItem.getSeason());

            // Map parent category relationship
            if (parentItem.getCategory() != null) {
                dto.setCategoryId(parentItem.getCategory().getId());
                dto.setCategoryName(parentItem.getCategory().getName());
            }
        }

        // This should be populated by a separate stock service/query
        dto.setCurrentStock(BigDecimal.ZERO);
        return dto;
    }

    /**
     * Converts a single ItemVariantDto to an ItemVariant entity.
     */
    public ItemVariant toEntity(ItemVariantDto dto) {
        if (dto == null) {
            return null;
        }

        ItemVariant itemVariant = new ItemVariant();
        itemVariant.setId(dto.getId());
        itemVariant.setSku(dto.getSku());
        itemVariant.setUnit(dto.getUnit());
        itemVariant.setPricePerUnit(dto.getPricePerUnit());
        itemVariant.setHsn(dto.getHsn());
        itemVariant.setGstRate(dto.getGstRate());
        itemVariant.setPhotoPath(dto.getPhotoPath());
        itemVariant.setColor(dto.getColor());
        itemVariant.setSize(dto.getSize());
        itemVariant.setDesign(dto.getDesign());
        itemVariant.setFit(dto.getFit()); // Map new 'fit' attribute
        itemVariant.setLowStockThreshold(dto.getLowStockThreshold());

        return itemVariant;
    }
}