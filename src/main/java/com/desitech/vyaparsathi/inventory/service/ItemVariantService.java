package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.inventory.dto.ItemVariantDto;
import com.desitech.vyaparsathi.inventory.entity.Item;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.mapper.ItemMapper;
import com.desitech.vyaparsathi.inventory.repository.ItemRepository;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ItemVariantService {

    @Autowired
    private ItemVariantRepository itemVariantRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private ItemMapper mapper;
    @Autowired
    private StockService stockService;

    @Transactional
    public ItemVariantDto create(ItemVariantDto dto) {
        // 1. Ensure the parent item exists
        Item parentItem = itemRepository.findById(dto.getItemId())
                .orElseThrow(() -> new EntityNotFoundException("Cannot create variant: Item not found with id " + dto.getItemId()));

        // 2. Convert DTO to entity
        ItemVariant itemVariant = mapper.toEntity(dto);

        // 3. Manually set the parent item relationship
        itemVariant.setItem(parentItem);

        // 4. Save and return mapped DTO
        ItemVariant savedVariant = itemVariantRepository.save(itemVariant);
        return mapper.toDto(savedVariant);
    }

    @Transactional
    public ItemVariantDto update(Long id, ItemVariantDto dto) {
        ItemVariant itemVariant = itemVariantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Item Variant not found with id: " + id));

        // Update all mutable fields from the DTO
        itemVariant.setSku(dto.getSku());
        itemVariant.setPricePerUnit(dto.getPricePerUnit());
        itemVariant.setGstRate(dto.getGstRate());
        itemVariant.setHsn(dto.getHsn());
        itemVariant.setUnit(dto.getUnit());
        itemVariant.setColor(dto.getColor());
        itemVariant.setSize(dto.getSize());
        itemVariant.setDesign(dto.getDesign());
        itemVariant.setFit(dto.getFit());
        itemVariant.setLowStockThreshold(dto.getLowStockThreshold());
        itemVariant.setPhotoPath(dto.getPhotoPath());

        ItemVariant savedVariant = itemVariantRepository.save(itemVariant);
        return mapper.toDto(savedVariant);
    }

    public Page<ItemVariantDto> list(Pageable pageable) {
        Page<ItemVariant> variantsPage = itemVariantRepository.findAll(pageable);
        // Note: This list method will also have an N+1 on stock. For high-traffic pages,
        // the same optimization from the search method should be applied here.
        return variantsPage.map(mapper::toDto);
    }

    public ItemVariantDto get(Long id) {
        ItemVariantDto dto = itemVariantRepository.findById(id)
                .map(mapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Item Variant not found with id: " + id));
        // Fetch stock for the single item
        dto.setCurrentStock(stockService.getCurrentStock(id));
        return dto;
    }

    /**
     * Searches for variants and efficiently populates their current stock levels.
     */
    public List<ItemVariantDto> searchItemVariants(
            String name, String categoryName, String color, String size, String design,
            String sku, String fabric, String season, String fit) {

        // 1. Call the corrected repository method with all parameters
        List<ItemVariant> variants = itemVariantRepository.searchVariants(
                name, categoryName, color, size, design, sku, fabric, season, fit);

        if (variants.isEmpty()) {
            return List.of();
        }

        // 2. Convert all variants to DTOs. The mapper handles all field mapping.
        List<ItemVariantDto> dtos = variants.stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());

        // 3. **PERFORMANCE OPTIMIZATION**: Fetch all stock levels in a single query.
        List<Long> variantIds = dtos.stream().map(ItemVariantDto::getId).collect(Collectors.toList());
        Map<Long, BigDecimal> stockMap = stockService.getStocksForVariants(variantIds); // Assumes this method exists in StockService

        // 4. Populate the DTOs with the fetched stock levels.
        dtos.forEach(dto -> dto.setCurrentStock(stockMap.getOrDefault(dto.getId(), BigDecimal.ZERO)));

        return dtos;
    }
}