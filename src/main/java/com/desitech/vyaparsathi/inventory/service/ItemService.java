package com.desitech.vyaparsathi.inventory.service;

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
        Item existingItem = itemRepository.findByIdWithVariants(id)
                .orElseThrow(() -> new EntityNotFoundException("Item not found with id: " + id));

        existingItem.setName(itemDto.getName());
        existingItem.setDescription(itemDto.getDescription());
        existingItem.setBrandName(itemDto.getBrandName());
        existingItem.setAttribute1(itemDto.getAttribute1() != null ? itemDto.getAttribute1() : itemDto.getFabric());
        existingItem.setAttribute2(itemDto.getAttribute2() != null ? itemDto.getAttribute2() : itemDto.getSeason());
        if (itemDto.getCategoryId() != null) {
            // Check if the category has changed
            if (existingItem.getCategory() == null || !existingItem.getCategory().getId().equals(itemDto.getCategoryId())) {
                Category newCategory = categoryRepository.findById(itemDto.getCategoryId())
                        .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + itemDto.getCategoryId()));
                existingItem.setCategory(newCategory);
            }
        } else {
            existingItem.setCategory(null); // Allow un-setting the category
        }

        // Map of incoming variants by id (if exist)
        Map<Long, ItemVariantDto> incomingById = itemDto.getVariants().stream()
                .filter(v -> v.getId() != null)
                .collect(Collectors.toMap(ItemVariantDto::getId, v -> v));

        // Prepare new list of variants
        List<ItemVariant> updatedVariants = new ArrayList<>();

        // 1. Update existing variants present in DTO
        for (ItemVariant existingVariant : new ArrayList<>(existingItem.getVariants())) {
            if (incomingById.containsKey(existingVariant.getId())) {
                ItemVariantDto dto = incomingById.get(existingVariant.getId());
                updateVariantFromDto(existingVariant, dto);
                updatedVariants.add(existingVariant);
            }
            // If not present in DTO, drop from updatedVariants (effectively delete)
        }

        // 2. Add new variants from DTO (no id)
        itemDto.getVariants().stream()
                .filter(v -> v.getId() == null)
                .forEach(dto -> {
                    ItemVariant entity = mapper.toEntity(dto);
                    entity.setItem(existingItem);
                    updatedVariants.add(entity);
                });

        // 3. Assign HSN/SKU to all updatedVariants if missing
        for (ItemVariant v : updatedVariants) {
            if (v.getSku() == null || v.getSku().isEmpty()) {
                v.setSku(generateSku(mapper.toDto(existingItem), mapper.toDto(v)));
            }
            if (v.getHsn() == null || v.getHsn().isEmpty()) {
                v.setHsn(generateUniqueHsn());
            }
        }

        // 4. Replace old variants with the new list
        existingItem.getVariants().clear();
        existingItem.getVariants().addAll(updatedVariants);

        itemRepository.save(existingItem);
        return mapper.toDto(existingItem);
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
        variant.setSku(dto.getSku());
        variant.setUnit(dto.getUnit());
        variant.setPricePerUnit(dto.getPricePerUnit());
        variant.setHsn(dto.getHsn());
        variant.setGstRate(dto.getGstRate());
        variant.setPhotoPath(dto.getPhotoPath());
        variant.setColor(dto.getColor());
        variant.setSize(dto.getSize());
        variant.setDesign(dto.getDesign());
        variant.setFit(dto.getFit()); // **FIXED**: Added the missing 'fit' attribute
        variant.setLowStockThreshold(dto.getLowStockThreshold());
    }
}