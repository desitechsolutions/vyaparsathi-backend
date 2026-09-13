package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.common.annotations.CheckSubscriptionLimit;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Autowired
    private StockService stockService;

    @Autowired(required = false)
    private com.desitech.vyaparsathi.supplier.repository.SupplierRepository supplierRepository;

    // Atomic counters for demo; in production, consider DB-sequence driven or time-based for distributed systems
    private static final AtomicLong hsnCounter = new AtomicLong(10000000);
    private static final AtomicLong skuCounter = new AtomicLong(50000000);

    public List<ItemDto> getAllItems() {
        List<ItemDto> items = itemRepository.findAllWithVariants().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
        // Fills variant.currentStock across all items in a single query.
        stockService.enrichCurrentStockOnItems(items);
        return items;
    }

    public ItemDto getItemById(Long id) {
        Item item = itemRepository.findByIdWithVariants(id)
                .orElseThrow(() -> new EntityNotFoundException("Item not found with id: " + id));
        ItemDto dto = mapper.toDto(item);
        if (dto.getVariants() != null) {
            stockService.enrichCurrentStock(dto.getVariants());
        }
        return dto;
    }

    @CheckSubscriptionLimit("ITEMS")
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

    @CheckSubscriptionLimit("ITEMS")
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

        // 2. Update Main Item Fields
        existingItem.setName(itemDto.getName());
        existingItem.setDescription(itemDto.getDescription());
        existingItem.setBrandName(itemDto.getBrandName());
        // attribute_1 / attribute_2 are the sole source of truth since V76;
        // the fabric / season columns are gone and their frontend readers
        // moved to attribute_1 / attribute_2.
        existingItem.setAttribute1(itemDto.getAttribute1());
        existingItem.setAttribute2(itemDto.getAttribute2());
        existingItem.setSpecifications(itemDto.getSpecifications());

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
    /**
     * Soft-delete: flags the item and all its variants inactive so they
     * disappear from catalog listings but historical sales still resolve
     * the FK. Blocks the delete if any variant has current stock — the
     * user is asked to clear inventory first.
     */
    @Transactional
    public void deleteItem(Long id) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Item not found with id: " + id));
        assertNoStockForItem(item);
        softDeleteItem(item);
    }

    public Page<ItemDto> searchItems(String q, Long categoryId, Pageable pageable) {
        Page<ItemDto> page = itemRepository.searchAll(q, categoryId, pageable).map(mapper::toDto);
        stockService.enrichCurrentStockOnItems(page.getContent());
        return page;
    }

    /**
     * Soft-delete in bulk with the same stock guard applied per item.
     * A single item with stock fails the whole batch — safer than a
     * partial delete that would leave the user guessing what happened.
     */
    @Transactional
    public int deleteItemsBulk(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        List<Item> found = itemRepository.findAllById(ids);
        if (found.isEmpty()) return 0;
        for (Item item : found) {
            assertNoStockForItem(item);
        }
        found.forEach(this::softDeleteItem);
        return found.size();
    }

    private void assertNoStockForItem(Item item) {
        List<ItemVariant> variants = item.getVariants();
        if (variants == null) return;
        for (ItemVariant v : variants) {
            if (Boolean.FALSE.equals(v.getActive())) continue;
            java.math.BigDecimal stock = stockService.getCurrentStock(v.getId());
            if (stock != null && stock.compareTo(java.math.BigDecimal.ZERO) > 0) {
                throw new BusinessValidationException(
                        "Cannot delete \"" + item.getName() + "\": variant " + v.getSku() +
                        " still has " + stock + " " + v.getUnit() + " in stock. Clear inventory first."
                );
            }
        }
    }

    private void softDeleteItem(Item item) {
        item.setActive(Boolean.FALSE);
        if (item.getVariants() != null) {
            for (ItemVariant v : item.getVariants()) {
                v.setActive(Boolean.FALSE);
            }
        }
        itemRepository.save(item);
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
        java.math.BigDecimal stock = stockService.getCurrentStock(variant.getId());
        if (stock != null && stock.compareTo(java.math.BigDecimal.ZERO) > 0) {
            throw new BusinessValidationException(
                    "Cannot delete variant " + variant.getSku() +
                    ": still has " + stock + " " + variant.getUnit() + " in stock. Clear inventory first."
            );
        }
        variant.setActive(Boolean.FALSE);
        itemVariantRepository.save(variant);
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
        // Preserve TAXABLE default if the client omits gstCategory rather
        // than nulling a NOT NULL column.
        if (dto.getGstCategory() != null) {
            variant.setGstCategory(dto.getGstCategory());
        }
        variant.setLowStockThreshold(dto.getLowStockThreshold());

        // 3. Visual & Attributes
        variant.setPhotoPath(dto.getPhotoPath());
        variant.setColor(dto.getColor());
        variant.setSize(dto.getSize());
        variant.setDesign(dto.getDesign());
        variant.setFit(dto.getFit());

        // 4. Generic batch/expiry/MRP/barcode fields
        variant.setBatchNumber(dto.getBatchNumber());
        variant.setManufacturingDate(dto.getManufacturingDate());
        variant.setExpiryDate(dto.getExpiryDate());
        variant.setMrp(dto.getMrp());
        variant.setBarcode(dto.getBarcode());

        // 5. Industry-specific fields (V76)
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

        // 6. Reorder rules + supplier assignment (V79)
        variant.setReorderPoint(dto.getReorderPoint());
        variant.setReorderQty(dto.getReorderQty());
        variant.setSafetyStock(dto.getSafetyStock());
        variant.setMaxStock(dto.getMaxStock());
        variant.setLeadTimeDays(dto.getLeadTimeDays());
        variant.setPreferredSupplier(resolveSupplierRef(dto.getPreferredSupplierId()));
        variant.setBackupSupplier(resolveSupplierRef(dto.getBackupSupplierId()));
    }

    /**
     * Resolves a supplier ID to a managed reference. Unknown IDs return
     * null (no exception) — a stale FE payload can't overwrite an existing
     * good assignment; the field just goes untouched relative to what the
     * caller sent (which the setter chain treats as "clear"). Callers that
     * need stricter behavior should validate at the controller layer.
     */
    private com.desitech.vyaparsathi.supplier.entity.Supplier resolveSupplierRef(Long id) {
        if (id == null) return null;
        return supplierRepository == null ? null : supplierRepository.findById(id).orElse(null);
    }
}