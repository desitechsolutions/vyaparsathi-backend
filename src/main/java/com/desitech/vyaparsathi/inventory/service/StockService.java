package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.common.exception.InsufficientStockException;
import com.desitech.vyaparsathi.inventory.enums.StockMovementType;
import com.desitech.vyaparsathi.inventory.dto.*;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.entity.StockMovement;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.inventory.repository.StockMovementRepository;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class StockService {

    private static final Logger logger = LoggerFactory.getLogger(StockService.class);

    @Autowired
    private ItemVariantRepository itemVariantRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private PurchaseOrderItemRepository purchaseOrderItemRepository;

    /**
     * Adds stock by creating a new 'ADD' movement. Returns the created movement.
     */
    @Transactional
    public StockMovementDto addStockFromDto(StockAddDto dto) {
        // Validation
        itemVariantRepository.findById(dto.getItemVariantId())
                .orElseThrow(() -> new EntityNotFoundAppException("Item Variant", dto.getItemVariantId()));

        BigDecimal costPerUnit = dto.getCostPerUnit() != null ? dto.getCostPerUnit() : BigDecimal.ZERO;
        BigDecimal quantity = dto.getQuantity();

        StockMovement movement = recordStockMovement(dto.getItemVariantId(), StockMovementType.ADD, quantity, costPerUnit, dto.getBatch(), "Manual Stock Addition", "Manual Entry");
        return mapToStockMovementDto(movement);
    }

    /**
     * Gets current stock levels for all items by summing movements.
     */
    public List<CurrentStockDto> getCurrentStock() {
        List<ItemVariant> itemVariants = itemVariantRepository.findAll();
        if (itemVariants.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> variantIds = itemVariants.stream()
                .map(ItemVariant::getId)
                .collect(Collectors.toList());

        // 1. Get current stock quantities (Sum of movements)
        Map<Long, BigDecimal> stockMap = getStocksForVariants(variantIds);

        // 2. Get the latest purchase costs using your existing repo method
        List<StockMovementRepository.LastPurchasePrice> lastPrices =
                stockMovementRepository.findLastPurchasePricesByVariantIds(variantIds);

        // Convert list to a Map for quick lookup during the stream
        Map<Long, BigDecimal> costMap = lastPrices.stream()
                .collect(Collectors.toMap(
                        StockMovementRepository.LastPurchasePrice::getVariantId,
                        StockMovementRepository.LastPurchasePrice::getPrice,
                        (v1, v2) -> v1 // In case of duplicates, keep the first
                ));

        return itemVariants.stream().map(variant -> {
            CurrentStockDto dto = new CurrentStockDto();
            dto.setItemVariantId(variant.getId());
            dto.setItemName(variant.getItem().getName());
            dto.setSku(variant.getSku());
            dto.setUnit(variant.getUnit());
            dto.setColor(variant.getColor());
            dto.setSize(variant.getSize());
            dto.setDesign(variant.getDesign());
            dto.setPricePerUnit(variant.getPricePerUnit());
            dto.setTotalQuantity(stockMap.getOrDefault(variant.getId(), BigDecimal.ZERO));

            // Map the purchase price from our new map
            dto.setCostPerUnit(costMap.getOrDefault(variant.getId(), BigDecimal.ZERO));

            dto.setBatch(null);
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Deducts stock by creating a new 'DEDUCT' movement.
     */
    @Transactional
    public void deductStock(Long itemVariantId, BigDecimal quantityToDeduct, String reason, String reference) {
        if (quantityToDeduct.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessValidationException("Quantity to deduct must be positive.");
        }

        BigDecimal currentStock = getCurrentStock(itemVariantId);
        if (currentStock.compareTo(quantityToDeduct) < 0) {
            logger.warn("Insufficient stock for item variant {} (requested: {}, available: {})", itemVariantId, quantityToDeduct, currentStock);
            throw new InsufficientStockException("Insufficient stock for item variant " + itemVariantId);
        }

        // The entire complex FIFO logic is replaced by this single line.
        recordStockMovement(itemVariantId, StockMovementType.DEDUCT, quantityToDeduct.negate(), null, null, reason, reference);
    }

    public boolean isStockAvailable(Long itemVariantId, BigDecimal quantity) {
        BigDecimal currentStock = getCurrentStock(itemVariantId);
        return currentStock.compareTo(quantity) >= 0;
    }

    /**
     * Gets the current stock for a single item by summing its movements.
     */
    public BigDecimal getCurrentStock(Long itemVariantId) {
        // This is the single source of truth for current stock.
        BigDecimal total = stockMovementRepository.sumQuantityByItemVariantId(itemVariantId);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Efficiently gets current stock for a list of variants.
     */
    public Map<Long, BigDecimal> getStocksForVariants(List<Long> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Map.of();
        }
        List<StockMovementRepository.StockQuantity> stockQuantities = stockMovementRepository.findTotalQuantitiesByItemVariantIds(variantIds);

        return stockQuantities.stream()
                .collect(Collectors.toMap(
                        StockMovementRepository.StockQuantity::getVariantId,
                        sq -> sq.getTotalQuantity() != null ? sq.getTotalQuantity() : BigDecimal.ZERO
                ));
    }

    /**
     * Manual stock adjustment.
     */
    @Transactional
    public StockMovementDto adjustStock(StockAdjustmentDto dto) {
        if (dto.getReason() == null || dto.getReason().trim().isEmpty()) {
            throw new BusinessValidationException("Reason is required for stock adjustment");
        }

        itemVariantRepository.findById(dto.getItemVariantId())
                .orElseThrow(() -> new EntityNotFoundAppException("Item Variant", dto.getItemVariantId()));

        // For negative adjustments, check for sufficient stock
        if (dto.getAdjustmentQuantity().compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal quantityToDeduct = dto.getAdjustmentQuantity().abs();
            BigDecimal currentStock = getCurrentStock(dto.getItemVariantId());
            if (currentStock.compareTo(quantityToDeduct) < 0) {
                throw new InsufficientStockException("Cannot adjust by " + dto.getAdjustmentQuantity() + ". Only " + currentStock + " available.");
            }
        }

        StockMovement movement = recordStockMovement(dto.getItemVariantId(), StockMovementType.ADJUST, dto.getAdjustmentQuantity(),
                dto.getCostPerUnit(), dto.getBatch(), dto.getReason(), "Manual Adjustment");

        return mapToStockMovementDto(movement);
    }

    // --- Helper and Passthrough Methods ---

    private StockMovement recordStockMovement(Long itemVariantId, StockMovementType movementType, BigDecimal quantity,
                                              BigDecimal costPerUnit, String batch, String reason, String reference) {
        ItemVariant itemVariant = itemVariantRepository.findById(itemVariantId)
                .orElseThrow(() -> new EntityNotFoundAppException("Item Variant", itemVariantId));

        StockMovement movement = new StockMovement();
        movement.setItemVariant(itemVariant);
        movement.setMovementType(movementType);
        movement.setQuantity(quantity);
        // Only set cost for ADD movements
        movement.setCostPerUnit(StockMovementType.ADD.equals(movementType) ? costPerUnit : null);
        movement.setBatch(batch);
        movement.setReason(reason);
        movement.setReference(reference);
        movement.setTimestamp(LocalDateTime.now());

        return stockMovementRepository.save(movement);
    }

    public List<StockMovementDto> getStockMovements(Long itemVariantId) {
        List<StockMovement> movements = stockMovementRepository.findByItemVariantIdOrderByTimestampDesc(itemVariantId);
        return movements.stream().map(this::mapToStockMovementDto).collect(Collectors.toList());
    }

    public List<StockMovementDto> getStockMovements(LocalDateTime startDate, LocalDateTime endDate) {
        List<StockMovement> movements = stockMovementRepository.findByTimestampBetweenOrderByTimestampDesc(startDate, endDate);
        return movements.stream().map(this::mapToStockMovementDto).collect(Collectors.toList());
    }

    public List<LowStockAlertDto> getLowStockAlerts() {
        List<ItemVariant> variantsWithThreshold = itemVariantRepository.findAllByLowStockThresholdIsNotNull();
        if (variantsWithThreshold.isEmpty()) {
            return List.of();
        }

        List<Long> variantIds = variantsWithThreshold.stream().map(ItemVariant::getId).collect(Collectors.toList());

        // 1. Get current stock levels
        Map<Long, BigDecimal> stockMap = getStocksForVariants(variantIds);

        // 2. Get quantities on pending purchase orders
        Map<Long, BigDecimal> onOrderMap = purchaseOrderItemRepository.findOnOrderQuantitiesByItemVariantIds(variantIds)
                .stream()
                .collect(Collectors.toMap(
                        PurchaseOrderItemRepository.OnOrderQuantity::getVariantId,
                        PurchaseOrderItemRepository.OnOrderQuantity::getTotalOnOrder
                ));

        // 3. Get last purchase prices
        Map<Long, BigDecimal> priceMap = stockMovementRepository.findLastPurchasePricesByVariantIds(variantIds)
                .stream()
                .collect(Collectors.toMap(
                        StockMovementRepository.LastPurchasePrice::getVariantId,
                        StockMovementRepository.LastPurchasePrice::getPrice
                ));

        Map<Long, PurchaseOrderItemRepository.LastSupplierInfo> supplierInfoMap = purchaseOrderItemRepository.findLastSuppliersByVariantIds(variantIds)
                .stream()
                .collect(Collectors.toMap(
                        PurchaseOrderItemRepository.LastSupplierInfo::getVariantId,
                        info -> info
                ));

        return variantsWithThreshold.stream()
                .map(variant -> {
                    BigDecimal currentStock = stockMap.getOrDefault(variant.getId(), BigDecimal.ZERO);

                    if (currentStock.compareTo(variant.getLowStockThreshold()) <= 0) {
                        LowStockAlertDto alert = new LowStockAlertDto();
                        alert.setItemVariantId(variant.getId());
                        alert.setItemName(variant.getItem().getName());
                        alert.setSku(variant.getSku());
                        alert.setCurrentStock(currentStock);
                        alert.setThreshold(variant.getLowStockThreshold());
                        alert.setUnit(variant.getUnit());
                        alert.setAlertLevel(currentStock.compareTo(BigDecimal.ZERO) <= 0 ? "CRITICAL" : "LOW");

                        // Populate new fields using our corrected map
                        PurchaseOrderItemRepository.LastSupplierInfo supplierInfo = supplierInfoMap.get(variant.getId());
                        if (supplierInfo != null) {
                            alert.setSupplierName(supplierInfo.getSupplierName());
                            alert.setSupplierId(supplierInfo.getSupplierId());
                        }

                        alert.setLastPurchasePrice(priceMap.get(variant.getId()));
                        alert.setQuantityOnOrder(onOrderMap.getOrDefault(variant.getId(), BigDecimal.ZERO));

                        return alert;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    private StockMovementDto mapToStockMovementDto(StockMovement movement) {
        StockMovementDto dto = new StockMovementDto();
        dto.setId(movement.getId());
        dto.setItemVariantId(movement.getItemVariant().getId());
        dto.setItemName(movement.getItemVariant().getItem().getName());
        dto.setSku(movement.getItemVariant().getSku());
        dto.setMovementType(movement.getMovementType());
        dto.setQuantity(movement.getQuantity());
        dto.setCostPerUnit(movement.getCostPerUnit());
        dto.setBatch(movement.getBatch());
        dto.setReason(movement.getReason());
        dto.setReference(movement.getReference());
        dto.setTimestamp(movement.getTimestamp());
        return dto;
    }

    // All other methods (like addStock overloads, calculateCOGSFifo) that were dependent on StockEntry are removed.
}