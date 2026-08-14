package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.inventory.dto.BulkVariantPatchDto;
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

        // Generic retail fields (batch/expiry/MRP)
        itemVariant.setBatchNumber(dto.getBatchNumber());
        itemVariant.setManufacturingDate(dto.getManufacturingDate());
        itemVariant.setExpiryDate(dto.getExpiryDate());
        itemVariant.setMrp(dto.getMrp());

        // Barcode (POS scanner)
        itemVariant.setBarcode(dto.getBarcode());

        // Reorder rules (V79) — same setter chain the ItemService uses.
        itemVariant.setReorderPoint(dto.getReorderPoint());
        itemVariant.setReorderQty(dto.getReorderQty());
        itemVariant.setSafetyStock(dto.getSafetyStock());
        itemVariant.setMaxStock(dto.getMaxStock());
        itemVariant.setLeadTimeDays(dto.getLeadTimeDays());
        itemVariant.setPreferredSupplier(resolveSupplierRef(dto.getPreferredSupplierId()));
        itemVariant.setBackupSupplier(resolveSupplierRef(dto.getBackupSupplierId()));

        ItemVariant savedVariant = itemVariantRepository.save(itemVariant);
        return mapper.toDto(savedVariant);
    }

    /**
     * Applies a partial patch to every variant in {@code ids}. Only the
     * fields present in {@code patch} are touched; nulls are treated as
     * "clear this field" only when the caller explicitly opts in per key
     * (see {@link BulkVariantPatchDto}). Returns the number of variants
     * actually modified — silently skips IDs the caller doesn't own or
     * that no longer exist.
     *
     * <p>Used by the LowStockAlerts "Bulk edit" action to push threshold,
     * reorder-point, or preferred-supplier changes across the selection.
     */
    @Transactional
    public int bulkPatch(BulkVariantPatchDto patch) {
        if (patch == null || patch.getIds() == null || patch.getIds().isEmpty()) return 0;
        List<ItemVariant> variants = itemVariantRepository.findAllById(patch.getIds());
        if (variants.isEmpty()) return 0;

        com.desitech.vyaparsathi.supplier.entity.Supplier preferred =
                patch.getPreferredSupplierId() != null ? resolveSupplierRef(patch.getPreferredSupplierId()) : null;

        for (ItemVariant v : variants) {
            if (patch.getLowStockThreshold() != null) v.setLowStockThreshold(patch.getLowStockThreshold());
            if (patch.getReorderPoint() != null)      v.setReorderPoint(patch.getReorderPoint());
            if (patch.getReorderQty() != null)        v.setReorderQty(patch.getReorderQty());
            if (patch.getSafetyStock() != null)       v.setSafetyStock(patch.getSafetyStock());
            if (patch.getMaxStock() != null)          v.setMaxStock(patch.getMaxStock());
            if (patch.getLeadTimeDays() != null)      v.setLeadTimeDays(patch.getLeadTimeDays());
            if (patch.getPreferredSupplierId() != null) v.setPreferredSupplier(preferred);
        }
        itemVariantRepository.saveAll(variants);
        return variants.size();
    }

    @Autowired(required = false)
    private com.desitech.vyaparsathi.supplier.repository.SupplierRepository supplierRepository;

    private com.desitech.vyaparsathi.supplier.entity.Supplier resolveSupplierRef(Long id) {
        if (id == null || supplierRepository == null) return null;
        return supplierRepository.findById(id).orElse(null);
    }

    /**
     * POS Barcode / QR Scanner lookup.
     * Finds the item variant matching the scanned barcode code and populates
     * its current stock level so the POS can display price + stock immediately.
     *
     * @param barcode the raw barcode string scanned by the device
     * @return the matching ItemVariantDto with current stock populated
     */
    public ItemVariantDto lookupByBarcode(String barcode) {
        ItemVariant variant = itemVariantRepository.findByBarcode(barcode)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "No item found for barcode: " + barcode));
        ItemVariantDto dto = mapper.toDto(variant);
        dto.setCurrentStock(stockService.getCurrentStock(variant.getId()));
        return dto;
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
     *
     * <p>{@code attribute1} / {@code attribute2} replaced {@code fabric} /
     * {@code season} in V76. The parameter names on this method were
     * renamed to match the underlying columns; the controller layer maps
     * legacy query-string keys ({@code ?fabric=} / {@code ?season=}) to
     * these parameters so existing clients keep working.
     */
    public List<ItemVariantDto> searchItemVariants(
            String name, String categoryName, String color, String size, String design,
            String sku, String attribute1, String attribute2, String fit, String specifications) {

        // 1. Call the corrected repository method with all parameters
        List<ItemVariant> variants = itemVariantRepository.searchVariants(
                name, categoryName, color, size, design, sku, attribute1, attribute2, fit, specifications);

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

    /**
     * Returns variants of therapeutic substitutes for a given item.
     * Substitutes are items sharing the same composition (active ingredient).
     *
     * @param itemId the ID of the reference item
     * @return list of variant DTOs from items with the same composition
     */
    public List<ItemVariantDto> getSubstitutes(Long itemId) {
        com.desitech.vyaparsathi.inventory.entity.Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Item not found with id: " + itemId));

        if (item.getSpecifications() == null || item.getSpecifications().isBlank()) {
            return List.of();
        }

        List<ItemVariant> substitutes = itemVariantRepository.findSubstitutesByComposition(
                item.getSpecifications(), itemId);

        if (substitutes.isEmpty()) {
            return List.of();
        }

        List<ItemVariantDto> dtos = substitutes.stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());

        List<Long> variantIds = dtos.stream().map(ItemVariantDto::getId).collect(Collectors.toList());
        Map<Long, BigDecimal> stockMap = stockService.getStocksForVariants(variantIds);
        dtos.forEach(dto -> dto.setCurrentStock(stockMap.getOrDefault(dto.getId(), BigDecimal.ZERO)));

        return dtos;
    }
}