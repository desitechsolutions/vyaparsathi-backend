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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
        ItemVariant itemVariant = itemVariantRepository.findById(dto.getItemVariantId())
                .orElseThrow(() -> new EntityNotFoundAppException("Item Variant", dto.getItemVariantId()));

        BigDecimal costPerUnit = dto.getCostPerUnit() != null ? dto.getCostPerUnit() : BigDecimal.ZERO;
        BigDecimal quantity = dto.getQuantity();

        // Update pharmacy-specific fields on the variant when a new batch is received.
        // This keeps the ItemVariant's batch metadata in sync with the latest stock receipt.
        boolean variantUpdated = false;
        if (dto.getBatch() != null && !dto.getBatch().isBlank()) {
            itemVariant.setBatchNumber(dto.getBatch());
            variantUpdated = true;
        }
        if (dto.getManufacturingDate() != null) {
            itemVariant.setManufacturingDate(dto.getManufacturingDate());
            variantUpdated = true;
        }
        if (dto.getExpiryDate() != null) {
            itemVariant.setExpiryDate(dto.getExpiryDate());
            variantUpdated = true;
        }
        if (variantUpdated) {
            itemVariantRepository.save(itemVariant);
        }

        StockMovement movement = recordStockMovement(dto.getItemVariantId(), StockMovementType.ADD, quantity, costPerUnit, dto.getBatch(), "Manual Stock Addition", "Manual Entry", dto.getExpiryDate());
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
                        lp -> ((Number) lp.getVariantId()).longValue(),
                        lp -> lp.getPrice() != null ? lp.getPrice() : BigDecimal.ZERO,
                        (v1, v2) -> v1
                ));

        Map<Long, BigDecimal> wacMap = stockMovementRepository.findWacByVariantIds(variantIds)
                .stream()
                .collect(Collectors.toMap(StockMovementRepository.WacProjection::getVariantId, StockMovementRepository.WacProjection::getWac));

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
            dto.setCostPerUnit(wacMap.getOrDefault(variant.getId(), BigDecimal.ZERO));

            dto.setBatch(null);

            // Pharmacy-specific fields from ItemVariant
            dto.setBatchNumber(variant.getBatchNumber());
            dto.setExpiryDate(variant.getExpiryDate());
            dto.setMrp(variant.getMrp());
            dto.setIsLooseMedicine(variant.getIsLooseMedicine());
            dto.setPackSize(variant.getPackSize());

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
        BigDecimal currentWac = getWeightedAverageCost(itemVariantId);

        recordStockMovement(itemVariantId, StockMovementType.DEDUCT, quantityToDeduct.negate(), currentWac, null, reason, reference);
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
    /**
     * Manual stock adjustment.
     * Positive adjustments use provided cost or WAC fallback.
     * Negative adjustments always use current WAC for valuation.
     */
    @Transactional
    public StockMovementDto adjustStock(StockAdjustmentDto dto) {
        if (dto.getReason() == null || dto.getReason().trim().isEmpty()) {
            throw new BusinessValidationException("Reason is required for stock adjustment");
        }

        itemVariantRepository.findById(dto.getItemVariantId())
                .orElseThrow(() -> new EntityNotFoundAppException("Item Variant", dto.getItemVariantId()));

        BigDecimal adjustmentQty = dto.getAdjustmentQuantity();
        BigDecimal currentWac = getWeightedAverageCost(dto.getItemVariantId());
        BigDecimal finalCostPerUnit;

        if (adjustmentQty.compareTo(BigDecimal.ZERO) < 0) {
            // Negative Adjustment: Check stock and use WAC
            BigDecimal quantityToDeduct = adjustmentQty.abs();
            BigDecimal currentStock = getCurrentStock(dto.getItemVariantId());
            if (currentStock.compareTo(quantityToDeduct) < 0) {
                throw new InsufficientStockException("Cannot adjust by " + adjustmentQty + ". Only " + currentStock + " available.");
            }
            finalCostPerUnit = currentWac;
        } else {
            // Positive Adjustment: Use provided cost, fallback to WAC
            finalCostPerUnit = (dto.getCostPerUnit() != null && dto.getCostPerUnit().compareTo(BigDecimal.ZERO) > 0)
                    ? dto.getCostPerUnit()
                    : currentWac;
        }

        StockMovement movement = recordStockMovement(
                dto.getItemVariantId(),
                StockMovementType.ADJUST,
                adjustmentQty,
                finalCostPerUnit,
                dto.getBatch(),
                dto.getReason(),
                "Manual Adjustment"
        );

        return mapToStockMovementDto(movement);
    }
    // --- Helper and Passthrough Methods ---

    private StockMovement recordStockMovement(Long itemVariantId, StockMovementType movementType, BigDecimal quantity,
                                              BigDecimal costPerUnit, String batch, String reason, String reference) {
        return recordStockMovement(itemVariantId, movementType, quantity, costPerUnit, batch, reason, reference, null);
    }

    private StockMovement recordStockMovement(Long itemVariantId, StockMovementType movementType, BigDecimal quantity,
                                              BigDecimal costPerUnit, String batch, String reason, String reference,
                                              LocalDate expiryDate) {
        ItemVariant itemVariant = itemVariantRepository.findById(itemVariantId)
                .orElseThrow(() -> new EntityNotFoundAppException("Item Variant", itemVariantId));

        StockMovement movement = new StockMovement();
        movement.setItemVariant(itemVariant);
        movement.setMovementType(movementType);
        movement.setQuantity(quantity);
        movement.setCostPerUnit(costPerUnit);
        movement.setBatch(batch);
        movement.setReason(reason);
        movement.setReference(reference);
        movement.setExpiryDate(expiryDate);
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
                        lp -> ((Number) lp.getVariantId()).longValue(),
                        lp -> lp.getPrice() != null ? lp.getPrice() : BigDecimal.ZERO,
                        (v1, v2) -> v1
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
        dto.setExpiryDate(movement.getExpiryDate());
        return dto;
    }

    /**
     * Returns expiry alerts for all item variants whose expiry date falls within
     * the given number of days from today. Items already expired are also included.
     * Primarily used by pharmacy shops.
     *
     * @param daysBeforeExpiry number of days ahead to check (e.g., 90 means warn 90 days before expiry)
     * @return list of expiry alert DTOs sorted by expiry date ascending
     */
    public List<ExpiryAlertDto> getExpiryAlerts(int daysBeforeExpiry) {
        LocalDate cutoffDate = LocalDate.now().plusDays(daysBeforeExpiry);
        List<ItemVariant> expiringVariants = itemVariantRepository.findByExpiryDateOnOrBefore(cutoffDate);

        if (expiringVariants.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> variantIds = expiringVariants.stream()
                .map(ItemVariant::getId)
                .collect(Collectors.toList());

        Map<Long, BigDecimal> stockMap = getStocksForVariants(variantIds);
        LocalDate today = LocalDate.now();

        return expiringVariants.stream()
                .map(variant -> {
                    ExpiryAlertDto alert = new ExpiryAlertDto();
                    alert.setItemVariantId(variant.getId());
                    alert.setItemName(variant.getItem().getName());
                    alert.setSku(variant.getSku());
                    alert.setBatchNumber(variant.getBatchNumber());
                    alert.setExpiryDate(variant.getExpiryDate());
                    long daysLeft = ChronoUnit.DAYS.between(today, variant.getExpiryDate());
                    alert.setDaysToExpiry(daysLeft);
                    alert.setCurrentStock(stockMap.getOrDefault(variant.getId(), BigDecimal.ZERO));
                    alert.setUnit(variant.getUnit());
                    if (daysLeft < 0) {
                        alert.setAlertLevel("EXPIRED");
                    } else if (daysLeft <= 30) {
                        alert.setAlertLevel("CRITICAL");
                    } else {
                        alert.setAlertLevel("WARNING");
                    }
                    return alert;
                })
                .sorted(java.util.Comparator.comparing(ExpiryAlertDto::getExpiryDate))
                .collect(Collectors.toList());
    }

    public BigDecimal getLatestPurchaseCost(Long itemVariantId) {
        List<StockMovementRepository.LastPurchasePrice> prices = stockMovementRepository
                .findLastPurchasePricesByVariantIds(Collections.singletonList(itemVariantId));

        if (prices.isEmpty() || prices.get(0).getPrice() == null) {
            // Fallback to variant's default price or ZERO if no purchase history exists
            return BigDecimal.ZERO;
        }

        return prices.get(0).getPrice();
    }

    /**
     * Calculates WAC: (Total Cost of All Additions) / (Total Quantity of All Additions)
     */
    public BigDecimal getWeightedAverageCost(Long itemVariantId) {
        BigDecimal totalInvestment = stockMovementRepository.sumTotalCostForAddMovements(itemVariantId);
        BigDecimal totalAdded = stockMovementRepository.sumTotalQuantityForAddMovements(itemVariantId);

        // Check for nulls or zero quantity to avoid division by zero or NPE
        if (totalAdded == null || totalAdded.compareTo(BigDecimal.ZERO) <= 0 || totalInvestment == null) {
            // Fallback: If no ADD movements exist, valuation is zero
            return BigDecimal.ZERO;
        }

        return totalInvestment.divide(totalAdded, 2, RoundingMode.HALF_UP);
    }
}