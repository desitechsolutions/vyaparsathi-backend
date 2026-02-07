package com.desitech.vyaparsathi.product.service;

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

    public List<ProductDto> getAllProducts() {
        List<ItemVariant> variants = itemVariantRepository.findAll();
        if (variants.isEmpty()) {
            return List.of();
        }

        List<Long> variantIds = variants.stream().map(ItemVariant::getId).collect(Collectors.toList());

        // CHANGED: Using the method name from your repository for consistency.
        Map<Long, BigDecimal> stockQuantityMap = stockMovementRepository.findTotalQuantitiesByItemVariantIds(variantIds)
                .stream()
                .collect(Collectors.toMap(
                        StockMovementRepository.StockQuantity::getVariantId,
                        StockMovementRepository.StockQuantity::getTotalQuantity
                ));

        return variants.stream().map(variant -> {
            ProductDto dto = new ProductDto();
            dto.setItemVariantId(variant.getId());
            dto.setItemName(variant.getItem().getName());
            dto.setDescription(variant.getItem().getDescription());
            dto.setSku(variant.getSku());
            dto.setColor(variant.getColor());
            dto.setSize(variant.getSize());
            dto.setDesign(variant.getDesign());
            dto.setPricePerUnit(variant.getPricePerUnit());
            dto.setPhotoPath(variant.getPhotoPath());

            BigDecimal availableQuantity = stockQuantityMap.getOrDefault(variant.getId(), BigDecimal.ZERO);
            dto.setAvailableQuantity(availableQuantity);
            dto.setBatch(null);

            return dto;
        }).collect(Collectors.toList());
    }
}