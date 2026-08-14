package com.desitech.vyaparsathi.product.service;

import com.desitech.vyaparsathi.inventory.entity.Item;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.product.dto.ProductDto;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ItemVariantRepository itemVariantRepository;
    private final StockMovementRepository stockMovementRepository;

    /**
     * Returns one row per active SKU. Filters out both soft-deleted
     * variants and variants whose parent item has been soft-deleted —
     * the Products browse view should only surface variants a shop
     * still stocks.
     */
    public List<ProductDto> getAllProducts() {
        List<ItemVariant> variants = itemVariantRepository.findAll().stream()
                .filter(v -> Boolean.TRUE.equals(v.getActive()))
                .filter(v -> v.getItem() != null && Boolean.TRUE.equals(v.getItem().getActive()))
                .collect(Collectors.toList());
        if (variants.isEmpty()) return List.of();

        List<Long> variantIds = variants.stream().map(ItemVariant::getId).collect(Collectors.toList());
        Map<Long, BigDecimal> stockQuantityMap = stockMovementRepository.findTotalQuantitiesByItemVariantIds(variantIds)
                .stream()
                .collect(Collectors.toMap(
                        StockMovementRepository.StockQuantity::getVariantId,
                        StockMovementRepository.StockQuantity::getTotalQuantity
                ));

        return variants.stream().map(variant -> {
            Item item = variant.getItem();
            ProductDto dto = new ProductDto();
            dto.setItemVariantId(variant.getId());
            dto.setItemName(item.getName());
            dto.setDescription(item.getDescription());
            dto.setSku(variant.getSku());
            dto.setColor(variant.getColor());
            dto.setSize(variant.getSize());
            dto.setDesign(variant.getDesign());
            dto.setFit(variant.getFit());
            dto.setUnit(variant.getUnit());
            dto.setPricePerUnit(variant.getPricePerUnit());
            dto.setMrp(variant.getMrp());
            dto.setHsn(variant.getHsn());
            dto.setGstRate(variant.getGstRate());
            dto.setPhotoPath(variant.getPhotoPath());
            dto.setLowStockThreshold(variant.getLowStockThreshold());
            dto.setBrandName(item.getBrandName());
            if (item.getCategory() != null) {
                dto.setCategoryName(item.getCategory().getName());
            }
            dto.setBatch(variant.getBatchNumber());
            dto.setAvailableQuantity(stockQuantityMap.getOrDefault(variant.getId(), BigDecimal.ZERO));
            return dto;
        }).collect(Collectors.toList());
    }
}
