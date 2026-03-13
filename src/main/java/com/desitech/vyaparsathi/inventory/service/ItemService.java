package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.DuplicateItemException;
import com.desitech.vyaparsathi.inventory.dto.ItemDto;
import com.desitech.vyaparsathi.inventory.dto.ItemVariantDto;
import com.desitech.vyaparsathi.inventory.entity.Category;
import com.desitech.vyaparsathi.inventory.entity.Item;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.mapper.ItemMapper;
import com.desitech.vyaparsathi.inventory.repository.CategoryRepository;
import com.desitech.vyaparsathi.inventory.repository.ItemRepository;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class ItemService {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemVariantRepository itemVariantRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ItemMapper mapper;

    // Atomic counters for demo; in production, consider DB-sequence driven or time-based for distributed systems
    private static final AtomicLong hsnCounter = new AtomicLong(10000000);
    private static final AtomicLong skuCounter = new AtomicLong(50000000);

    public List<ItemDto> getAllItems() {
        return itemRepository.findAllWithVariants().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    public ItemDto getItemById(Long id) {
        Item item = itemRepository.findByIdWithVariants(id)
                .orElseThrow(() -> new EntityNotFoundException("Item not found with id: " + id));
        return mapper.toDto(item);
    }

    @Transactional
    public ItemDto createItem(ItemDto itemDto) {
        if (itemRepository.existsByNameAndBrandNameAndShopId(
                itemDto.getName(),
                itemDto.getBrandName(),
                TenantContext.getCurrentShopId())) {
            throw new DuplicateItemException("Item '" + itemDto.getName() +
                    "' with brand '" + itemDto.getBrandName() + "' already exists in this shop.");
        }
        assignHsnAndSkuCodes(itemDto);
        // Ensure all variants are treated as new (id = null)
        if (itemDto.getVariants() != null) {
            itemDto.getVariants().forEach(v -> v.setId(null));
        }
        Item item = mapper.toEntity(itemDto);
        item = itemRepository.save(item);
        return mapper.toDto(item);
    }

    @Transactional
    public List<ItemDto> createItems(List<ItemDto> items) {
        items.forEach(itemDto -> {
            assignHsnAndSkuCodes(itemDto);
            if (itemDto.getVariants() != null) {
                itemDto.getVariants().forEach(v -> v.setId(null));
            }
        });
        List<Item> entities = items.stream()
                .map(mapper::toEntity)
                .collect(Collectors.toList());
        List<Item> savedEntities = itemRepository.saveAll(entities);
        return savedEntities.stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ItemDto updateItem(Long id, ItemDto itemDto) {
        // 1. Fetch existing item with variants (join fetch recommended in repo)
        Item existingItem = itemRepository.findByIdWithVariants(id)
                .orElseThrow(() -> new EntityNotFoundException("Item not found with id: " + id));

        // 2. Update Main Item Fields & Sync Legacy Attributes
        existingItem.setName(itemDto.getName());
        existingItem.setDescription(itemDto.getDescription());
        existingItem.setBrandName(itemDto.getBrandName());

        // Sync logic: ensures both generic and specific columns stay identical
        String attr1 = itemDto.getAttribute1() != null ? itemDto.getAttribute1() : itemDto.getFabric();
        String attr2 = itemDto.getAttribute2() != null ? itemDto.getAttribute2() : itemDto.getSeason();
        existingItem.setAttribute1(attr1);
        existingItem.setFabric(attr1);
        existingItem.setAttribute2(attr2);
        existingItem.setSeason(attr2);

        // Pharmacy fields
        existingItem.setDrugSchedule(itemDto.getDrugSchedule());
        existingItem.setRequiresPrescription(itemDto.getRequiresPrescription());
        existingItem.setComposition(itemDto.getComposition());
        existingItem.setStorageRequirement(itemDto.getStorageRequirement());

        // 3. Category Update
        if (itemDto.getCategoryId() != null) {
            if (existingItem.getCategory() == null || !existingItem.getCategory().getId().equals(itemDto.getCategoryId())) {
                Category newCategory = categoryRepository.findById(itemDto.getCategoryId())
                        .orElseThrow(() -> new EntityNotFoundException("Category not found: " + itemDto.getCategoryId()));
                existingItem.setCategory(newCategory);
            }
        } else {
            existingItem.setCategory(null);
        }

        // 4. Manage Variants without replacing the collection reference
        Map<Long, ItemVariantDto> incomingById = itemDto.getVariants().stream()
                .filter(v -> v.getId() != null)
                .collect(Collectors.toMap(ItemVariantDto::getId, v -> v));

        // A. Remove variants not present in the incoming DTO (Orphan Removal triggers here)
        existingItem.getVariants().removeIf(variant -> !incomingById.containsKey(variant.getId()));

        // B. Update existing managed variants
        for (ItemVariant existingVariant : existingItem.getVariants()) {
            ItemVariantDto dto = incomingById.get(existingVariant.getId());
            updateVariantFromDto(existingVariant, dto);
        }

        // C. Add new variants
        itemDto.getVariants().stream()
                .filter(v -> v.getId() == null)
                .forEach(dto -> {
                    // Ensure identifiers are generated before converting to entity
                    if (dto.getSku() == null || dto.getSku().isEmpty()) {
                        dto.setSku(generateSku(itemDto, dto));
                    }
                    if (dto.getHsn() == null || dto.getHsn().isEmpty()) {
                        dto.setHsn(generateUniqueHsn());
                    }

                    ItemVariant newVariant = mapper.toEntity(dto);
                    newVariant.setItem(existingItem); // Maintain back-reference
                    existingItem.getVariants().add(newVariant);
                });

        // 5. Save and return
        Item savedItem = itemRepository.save(existingItem);
        return mapper.toDto(savedItem);
    }
    @Transactional
    public void deleteItem(Long id) {
        if (!itemRepository.existsById(id)) {
            throw new EntityNotFoundException("Item not found with id: " + id);
        }
        itemRepository.deleteById(id);
    }

    public List<ItemVariantDto> getAllItemVariants() {
        return itemVariantRepository.findAll().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    public ItemVariantDto getItemVariantById(Long id) {
        ItemVariant variant = itemVariantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Item Variant not found with id: " + id));
        return mapper.toDto(variant);
    }

    @Transactional
    public ItemVariantDto createItemVariant(Long itemId, ItemVariantDto variantDto) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Item not found with id: " + itemId));
        if (variantDto.getSku() == null || variantDto.getSku().isEmpty()) {
            ItemDto itemDto = mapper.toDto(item);
            variantDto.setSku(generateSku(itemDto, variantDto));
        } else if (itemVariantRepository.findBySku(variantDto.getSku()).isPresent()) {
            throw new IllegalArgumentException("SKU already exists: " + variantDto.getSku());
        }
        if (variantDto.getHsn() == null || variantDto.getHsn().isEmpty()) {
            variantDto.setHsn(generateUniqueHsn());
        }
        ItemVariant variant = mapper.toEntity(variantDto);
        variant.setItem(item);
        itemVariantRepository.save(variant);
        return mapper.toDto(variant);
    }

    @Transactional
    public void deleteItemVariant(Long id) {
        ItemVariant variant = itemVariantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Item Variant not found with id: " + id));
        itemVariantRepository.delete(variant);
    }

    // ---------- HSN & SKU Generation ------------

    public void assignHsnAndSkuCodes(ItemDto itemDto) {
        Set<String> usedHsns = new HashSet<>();
        Set<String> usedSkus = new HashSet<>();
        for (ItemVariantDto variant : itemDto.getVariants()) {
            if (variant.getHsn() == null || variant.getHsn().trim().isEmpty()) {
                String hsn;
                do {
                    hsn = String.valueOf(hsnCounter.getAndIncrement());
                } while (usedHsns.contains(hsn) || itemVariantRepository.existsByHsn(hsn));
                variant.setHsn(hsn);
                usedHsns.add(hsn);
            }
            if (variant.getSku() == null || variant.getSku().trim().isEmpty()) {
                String sku;
                do {
                    sku = generateSku(itemDto, variant);
                } while (usedSkus.contains(sku) || itemVariantRepository.findBySku(sku).isPresent());
                variant.setSku(sku);
                usedSkus.add(sku);
            }
        }
    }

    private String generateUniqueHsn() {
        String hsn;
        do {
            hsn = String.valueOf(hsnCounter.getAndIncrement());
        } while (itemVariantRepository.existsByHsn(hsn));
        return hsn;
    }

    private String generateSku(ItemDto itemDto, ItemVariantDto variant) {
        String prefix = (itemDto.getCategoryName() != null ? itemDto.getCategoryName().replaceAll("\\s+", "").toUpperCase() : "ITEM")
                + "-"
                + (itemDto.getBrandName() != null ? itemDto.getBrandName().replaceAll("\\s+", "").toUpperCase() : "BRAND");
        String variantPart = (variant.getSize() != null ? variant.getSize().toUpperCase() : "")
                + "-"+(variant.getColor() != null ? variant.getColor().toUpperCase() : "");
        String sku = prefix + "-" + variantPart + "-" + skuCounter.getAndIncrement();
        return sku;
    }

    private void updateVariantFromDto(ItemVariant variant, ItemVariantDto dto) {
        // 1. Critical Identifier Guard
        // Only update SKU/HSN if they are provided and different to prevent accidental nulling
        if (dto.getSku() != null && !dto.getSku().isBlank()) {
            variant.setSku(dto.getSku());
        }
        if (dto.getHsn() != null && !dto.getHsn().isBlank()) {
            variant.setHsn(dto.getHsn());
        }

        // 2. Core Pricing & Inventory
        variant.setUnit(dto.getUnit());
        variant.setPricePerUnit(dto.getPricePerUnit());
        variant.setGstRate(dto.getGstRate());
        variant.setLowStockThreshold(dto.getLowStockThreshold());

        // 3. Visual & Attributes
        variant.setPhotoPath(dto.getPhotoPath());
        variant.setColor(dto.getColor());
        variant.setSize(dto.getSize());
        variant.setDesign(dto.getDesign());
        variant.setFit(dto.getFit());

        // 4. Pharmacy fields
        variant.setBatchNumber(dto.getBatchNumber());
        variant.setManufacturingDate(dto.getManufacturingDate());
        variant.setExpiryDate(dto.getExpiryDate());
        variant.setMrp(dto.getMrp());
        variant.setIsLooseMedicine(dto.getIsLooseMedicine());
        variant.setPackSize(dto.getPackSize());
    }
}