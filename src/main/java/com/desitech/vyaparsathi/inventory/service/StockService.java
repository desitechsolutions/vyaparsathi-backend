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

    @Autowired
    private com.desitech.vyaparsathi.sales.repository.SaleItemRepository saleItemRepository;

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

        // Sync the ItemVariant's batch metadata (batch/mfg/expiry) with the latest stock receipt.
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
        // Exclude soft-deleted variants and variants whose parent item was
        // deactivated — the Stock overview should only reflect the current
        // active catalog. Historical sale-item joins still resolve through
        // the FK (see ItemService.softDeleteItem javadoc).
        List<ItemVariant> itemVariants = itemVariantRepository.findAll().stream()
                .filter(v -> Boolean.TRUE.equals(v.getActive()))
                .filter(v -> v.getItem() != null && Boolean.TRUE.equals(v.getItem().getActive()))
                .collect(Collectors.toList());
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

            // Generic retail fields from ItemVariant
            dto.setBatchNumber(variant.getBatchNumber());
            dto.setExpiryDate(variant.getExpiryDate());
            dto.setMrp(variant.getMrp());

            // Enterprise-redesign additions (parent-item + variant metadata)
            dto.setFit(variant.getFit());
            dto.setLowStockThreshold(variant.getLowStockThreshold());
            dto.setPhotoPath(variant.getPhotoPath());
            dto.setHsn(variant.getHsn());
            dto.setGstRate(variant.getGstRate());
            if (variant.getItem() != null) {
                dto.setBrandName(variant.getItem().getBrandName());
                if (variant.getItem().getCategory() != null) {
                    dto.setCategoryName(variant.getItem().getCategory().getName());
                }
            }

            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Returns per-batch stock breakdown for all item variants.
     * <p>
     * Unlike {@link #getCurrentStock()}, which collapses all batches of the same
     * ItemVariant into a single total, this method returns one entry per
     * (variant, batch, expiryDate) combination so the UI can display, e.g.:
     * <ul>
     *   <li>Paracetamol 500mg – Batch A – 20 strips (exp Jun-2025)</li>
     *   <li>Paracetamol 500mg – Batch B – 30 strips (exp Dec-2025)</li>
     * </ul>
     * Only batches with a net-positive remaining quantity are included.
     * </p>
     */
    public List<BatchStockDto> getBatchWiseStock() {
        List<ItemVariant> itemVariants = itemVariantRepository.findAll();
        if (itemVariants.isEmpty()) {
            return Collections.emptyList();
        }

        // Build lookup map for variant metadata
        Map<Long, ItemVariant> variantMap = itemVariants.stream()
                .collect(Collectors.toMap(ItemVariant::getId, v -> v));

        List<Long> variantIds = new java.util.ArrayList<>(variantMap.keySet());

        List<StockMovementRepository.BatchStockProjection> projections =
                stockMovementRepository.findBatchWiseStockByVariantIds(variantIds);

        return projections.stream().map(p -> {
            BatchStockDto dto = new BatchStockDto();
            dto.setItemVariantId(p.getVariantId());
            dto.setBatchNumber(p.getBatchNumber());
            dto.setExpiryDate(p.getExpiryDate());
            dto.setQuantity(p.getTotalQuantity() != null ? p.getTotalQuantity() : BigDecimal.ZERO);
            dto.setCostPerUnit(p.getWacCost() != null ? p.getWacCost() : BigDecimal.ZERO);

            ItemVariant variant = variantMap.get(p.getVariantId());
            if (variant != null) {
                dto.setItemName(variant.getItem().getName());
                dto.setSku(variant.getSku());
                dto.setUnit(variant.getUnit());
                dto.setMrp(variant.getMrp());
            }
            return dto;
        }).collect(Collectors.toList());
    }


    /**
     * Deducts stock via FEFO (First-Expired-First-Out) when batches are
     * present. Non-perishable items fall back to a flat total deduction —
     * behavior identical to the legacy path. Reservations count against
     * sellable stock, so a partial hold prevents over-selling.
     */
    @Transactional
    public void deductStock(Long itemVariantId, BigDecimal quantityToDeduct, String reason, String reference) {
        if (quantityToDeduct.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessValidationException("Quantity to deduct must be positive.");
        }

        // Acquire a pessimistic write lock on the ItemVariant row before reading stock.
        // This serialises concurrent deductions: Tx B blocks here until Tx A commits.
        // When Tx B finally reads the SUM it sees Tx A's committed DEDUCT movement,
        // preventing two concurrent offline sales from overselling the same product.
        itemVariantRepository.findByIdForUpdate(itemVariantId)
                .orElseThrow(() -> new EntityNotFoundAppException("Item Variant", itemVariantId));

        BigDecimal currentStock = getCurrentStock(itemVariantId);
        // Subtract active reservations so a soft-hold shields the sellable pool.
        BigDecimal reserved = reservedFor(itemVariantId);
        BigDecimal sellable = currentStock.subtract(reserved);
        if (sellable.compareTo(quantityToDeduct) < 0) {
            logger.warn("Insufficient sellable stock for variant {} (requested: {}, available: {}, reserved: {})",
                    itemVariantId, quantityToDeduct, sellable, reserved);
            throw new InsufficientStockException("Insufficient stock for item variant " + itemVariantId);
        }

        // FEFO pick: enumerate batches with earliest expiry first.
        List<com.desitech.vyaparsathi.inventory.dto.BatchStockDto> batches = getBatchWiseStock().stream()
                .filter(b -> b.getItemVariantId() != null && b.getItemVariantId().equals(itemVariantId))
                .filter(b -> b.getQuantity() != null && b.getQuantity().signum() > 0)
                .filter(b -> b.getExpiryDate() != null) // Only expiry-aware pick for perishables.
                .sorted(java.util.Comparator.comparing(
                        com.desitech.vyaparsathi.inventory.dto.BatchStockDto::getExpiryDate,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .collect(java.util.stream.Collectors.toList());

        if (batches.isEmpty()) {
            // Flat path — legacy behavior for non-batch items.
            BigDecimal currentWac = getWeightedAverageCost(itemVariantId);
            recordStockMovement(itemVariantId, StockMovementType.DEDUCT, quantityToDeduct.negate(), currentWac, null, reason, reference);
            return;
        }

        BigDecimal remaining = quantityToDeduct;
        for (com.desitech.vyaparsathi.inventory.dto.BatchStockDto b : batches) {
            if (remaining.signum() <= 0) break;
            BigDecimal available = b.getQuantity();
            BigDecimal take = available.min(remaining);
            BigDecimal cost = b.getCostPerUnit() != null ? b.getCostPerUnit() : getWeightedAverageCost(itemVariantId);
            recordStockMovement(itemVariantId, StockMovementType.DEDUCT, take.negate(), cost, b.getBatchNumber(),
                    reason + " · FEFO batch " + b.getBatchNumber(), reference);
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            // Any leftover comes from the un-batched pool.
            BigDecimal currentWac = getWeightedAverageCost(itemVariantId);
            recordStockMovement(itemVariantId, StockMovementType.DEDUCT, remaining.negate(), currentWac, null,
                    reason + " · pool", reference);
        }
    }

    /**
     * Reservations count against the sellable pool. Wired through an
     * @Autowired field-injection so this method stays test-friendly (no
     * constructor change) and reservations are optional.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.desitech.vyaparsathi.inventory.service.InventoryReservationService reservationService;

    private BigDecimal reservedFor(Long variantId) {
        if (reservationService == null) return BigDecimal.ZERO;
        try { return reservationService.reservedFor(variantId); }
        catch (Exception e) { return BigDecimal.ZERO; }
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

    // ─── DTO enrichment helpers ───────────────────────────────────────
    // ItemMapper leaves ItemVariantDto.currentStock = ZERO on purpose so
    // it stays repo-free. Callers on list/detail read paths route DTOs
    // through these helpers to fill in real numbers with a single batched
    // query, no N+1s.

    /**
     * Populate {@code currentStock} on a single variant DTO.
     */
    public void enrichCurrentStock(com.desitech.vyaparsathi.inventory.dto.ItemVariantDto dto) {
        if (dto == null || dto.getId() == null) return;
        dto.setCurrentStock(getCurrentStock(dto.getId()));
    }

    /**
     * Populate {@code currentStock} on every variant DTO in the list with
     * a single stock query — safe for large item lists.
     */
    public void enrichCurrentStock(List<com.desitech.vyaparsathi.inventory.dto.ItemVariantDto> variants) {
        if (variants == null || variants.isEmpty()) return;
        List<Long> ids = variants.stream()
                .map(com.desitech.vyaparsathi.inventory.dto.ItemVariantDto::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        Map<Long, BigDecimal> stockMap = getStocksForVariants(ids);
        for (com.desitech.vyaparsathi.inventory.dto.ItemVariantDto v : variants) {
            if (v.getId() != null) {
                v.setCurrentStock(stockMap.getOrDefault(v.getId(), BigDecimal.ZERO));
            }
        }
    }

    /**
     * Populate {@code currentStock} on every variant nested inside a list
     * of {@link com.desitech.vyaparsathi.inventory.dto.ItemDto} — the
     * shape returned by the catalog list endpoint. Single stock query
     * across every variant of every item.
     */
    public void enrichCurrentStockOnItems(List<com.desitech.vyaparsathi.inventory.dto.ItemDto> items) {
        if (items == null || items.isEmpty()) return;
        List<com.desitech.vyaparsathi.inventory.dto.ItemVariantDto> allVariants = items.stream()
                .filter(i -> i.getVariants() != null)
                .flatMap(i -> i.getVariants().stream())
                .collect(Collectors.toList());
        enrichCurrentStock(allVariants);
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

    // Velocity window — 30 days of sales. Kept as a constant here rather than
    // a config property because the entire enterprise reorder module assumes
    // this window (days-of-supply, suggested qty). Changing it means auditing
    // the frontend labels too.
    private static final int VELOCITY_WINDOW_DAYS = 30;

    // Cover the shop for this many days when the velocity-based suggestion
    // beats the threshold-gap suggestion. Matches Zoho's default reorder
    // cover; tune per-shop if a preference emerges.
    private static final int SUGGESTED_COVER_DAYS = 14;

    // Trend window — compare last 15 days vs the 15 days before that.
    // Half of the 30-day velocity window keeps signals directly comparable.
    private static final int TREND_HALF_WINDOW_DAYS = 15;

    // Ratio thresholds for the categorical trend bucket. A recent/previous
    // ratio outside [0.85, 1.15] is treated as a real direction change;
    // anything inside is "flat" to keep the badge from flip-flopping on
    // noise. Kept tight enough that a 20% sustained shift always shows.
    private static final double TREND_UP_THRESHOLD = 1.15;
    private static final double TREND_DOWN_THRESHOLD = 0.85;

    // ABC classification (Tier 3) — window and Pareto cutoffs.
    // 90 days matches the standard S&OP horizon; long enough to smooth
    // seasonal noise for retail but short enough that a lapsed hero still
    // slides out of A.
    private static final int ABC_WINDOW_DAYS = 90;
    private static final double ABC_A_CUMULATIVE_CUTOFF = 0.80; // A = variants covering first 80% of revenue
    private static final double ABC_B_CUMULATIVE_CUTOFF = 0.95; // A+B = 95%; rest = C

    @Transactional(readOnly = true)
    public List<LowStockAlertDto> getLowStockAlerts() {
        // V79: also include variants that only set reorderPoint (no legacy
        // lowStockThreshold). See ItemVariantRepository.findAllForLowStockAlerting.
        Long shopId = com.desitech.vyaparsathi.common.configs.TenantContext.getCurrentShopId();
        List<ItemVariant> variantsWithThreshold = itemVariantRepository.findAllForLowStockAlerting(shopId);
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

        // 4. Get 30-day sales velocity — powers days-of-supply and the
        //    velocity branch of the suggested-qty formula.
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusDays(VELOCITY_WINDOW_DAYS);
        Map<Long, BigDecimal> velocityMap = stockMovementRepository
                .findSalesVelocityByVariantIds(variantIds, since)
                .stream()
                .collect(Collectors.toMap(
                        StockMovementRepository.SalesVelocityProjection::getVariantId,
                        v -> v.getUnitsSold() != null ? v.getUnitsSold() : BigDecimal.ZERO
                ));

        // 4b. Split the same window into "recent 15d" and derive "prior 15d"
        //     for the trend badge. We query the recent half explicitly; the
        //     prior half falls out of the 30-day total minus the recent half.
        LocalDateTime sinceRecent = now.minusDays(TREND_HALF_WINDOW_DAYS);
        Map<Long, BigDecimal> recentHalfMap = stockMovementRepository
                .findSalesVelocityByVariantIds(variantIds, sinceRecent)
                .stream()
                .collect(Collectors.toMap(
                        StockMovementRepository.SalesVelocityProjection::getVariantId,
                        v -> v.getUnitsSold() != null ? v.getUnitsSold() : BigDecimal.ZERO
                ));

        // 4c. Revenue-based Pareto bucket (ABC). We pull 90-day revenue for
        //     ALL variants that had sales in the window, not only the ones
        //     currently in the alert set — the buckets must be computed
        //     against the whole catalog's revenue distribution or "A" would
        //     mean "top of the low-stock alerts" instead of "top of the shop".
        LocalDateTime abcSince = now.minusDays(ABC_WINDOW_DAYS);
        Map<Long, String> abcMap = classifyAbc(
                saleItemRepository.findRevenueByVariantSince(abcSince));

        return variantsWithThreshold.stream()
                .map(variant -> {
                    BigDecimal currentStock = stockMap.getOrDefault(variant.getId(), BigDecimal.ZERO);

                    // Effective reorder point: explicit reorderPoint (V79) wins,
                    // falls back to lowStockThreshold for shops that haven't
                    // adopted the richer rules yet.
                    BigDecimal effectivePoint = variant.getReorderPoint() != null
                            ? variant.getReorderPoint()
                            : variant.getLowStockThreshold();

                    if (currentStock.compareTo(effectivePoint) <= 0) {
                        LowStockAlertDto alert = new LowStockAlertDto();
                        alert.setItemVariantId(variant.getId());
                        alert.setItemName(variant.getItem().getName());
                        alert.setSku(variant.getSku());
                        alert.setCurrentStock(currentStock);
                        alert.setThreshold(effectivePoint);
                        alert.setUnit(variant.getUnit());
                        alert.setAlertLevel(currentStock.compareTo(BigDecimal.ZERO) <= 0 ? "CRITICAL" : "LOW");

                        // Preferred supplier explicit assignment wins over the
                        // "infer from last PO" fallback (Zoho-style).
                        com.desitech.vyaparsathi.supplier.entity.Supplier preferred = variant.getPreferredSupplier();
                        if (preferred != null) {
                            alert.setPreferredSupplierId(preferred.getId());
                            alert.setPreferredSupplierName(preferred.getName());
                            alert.setSupplierId(preferred.getId());
                            alert.setSupplierName(preferred.getName());
                        } else {
                            PurchaseOrderItemRepository.LastSupplierInfo supplierInfo = supplierInfoMap.get(variant.getId());
                            if (supplierInfo != null) {
                                alert.setSupplierName(supplierInfo.getSupplierName());
                                alert.setSupplierId(supplierInfo.getSupplierId());
                            }
                        }

                        BigDecimal onOrder = onOrderMap.getOrDefault(variant.getId(), BigDecimal.ZERO);
                        alert.setLastPurchasePrice(priceMap.get(variant.getId()));
                        alert.setQuantityOnOrder(onOrder);

                        // Expose the raw reorder-rule fields so the FE can
                        // render the "why this suggestion" popover.
                        alert.setReorderPoint(variant.getReorderPoint());
                        alert.setReorderQty(variant.getReorderQty());
                        alert.setSafetyStock(variant.getSafetyStock());
                        alert.setMaxStock(variant.getMaxStock());
                        alert.setLeadTimeDays(variant.getLeadTimeDays());

                        // Velocity + days-of-supply + suggested qty
                        BigDecimal unitsSold = velocityMap.getOrDefault(variant.getId(), BigDecimal.ZERO);
                        BigDecimal avgDaily = unitsSold.compareTo(BigDecimal.ZERO) > 0
                                ? unitsSold.divide(BigDecimal.valueOf(VELOCITY_WINDOW_DAYS), 3, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;
                        alert.setAvgDailySales(avgDaily);

                        if (avgDaily.compareTo(BigDecimal.ZERO) > 0) {
                            long days = currentStock.max(BigDecimal.ZERO)
                                    .divide(avgDaily, 0, RoundingMode.FLOOR).longValue();
                            alert.setDaysOfSupply(days);
                        }

                        alert.setSuggestedOrderQty(computeSuggestedQty(variant, currentStock, onOrder, avgDaily));

                        // Trend bucket — compares last-15d units to prior-15d units.
                        BigDecimal recent = recentHalfMap.getOrDefault(variant.getId(), BigDecimal.ZERO);
                        BigDecimal previous = unitsSold.subtract(recent);
                        alert.setSalesTrend(bucketTrend(recent, previous));

                        // ABC bucket — from the 90-day revenue Pareto over the
                        // whole catalog. Null when the variant had no sales
                        // in the window.
                        alert.setAbcClass(abcMap.get(variant.getId()));

                        return alert;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Builds the ABC bucket map over a set of (variantId, revenue) rows.
     * Sorts by revenue descending, walks cumulative share, tags the top
     * cluster covering 80% of revenue as A, the next cluster to 95% as B,
     * and everything remaining as C. Variants not present in the input
     * (zero recent sales) are simply absent from the result — the caller
     * treats "missing" as "unclassified" rather than "worst".
     */
    private Map<Long, String> classifyAbc(
            List<com.desitech.vyaparsathi.sales.repository.SaleItemRepository.VariantRevenueProjection> rows) {
        if (rows == null || rows.isEmpty()) return java.util.Collections.emptyMap();

        // Filter zero-revenue rows so they don't dilute the Pareto denominator.
        List<com.desitech.vyaparsathi.sales.repository.SaleItemRepository.VariantRevenueProjection> ranked =
                rows.stream()
                        .filter(r -> r.getRevenue() != null && r.getRevenue().signum() > 0)
                        .sorted((a, b) -> b.getRevenue().compareTo(a.getRevenue()))
                        .collect(Collectors.toList());
        if (ranked.isEmpty()) return java.util.Collections.emptyMap();

        BigDecimal total = ranked.stream()
                .map(r -> r.getRevenue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) return java.util.Collections.emptyMap();

        Map<Long, String> out = new java.util.HashMap<>();
        BigDecimal running = BigDecimal.ZERO;
        for (var row : ranked) {
            running = running.add(row.getRevenue());
            double share = running.divide(total, 6, RoundingMode.HALF_UP).doubleValue();
            String bucket;
            if (share <= ABC_A_CUMULATIVE_CUTOFF)      bucket = "A";
            else if (share <= ABC_B_CUMULATIVE_CUTOFF) bucket = "B";
            else                                       bucket = "C";
            out.put(row.getVariantId(), bucket);
        }
        return out;
    }

    /**
     * Categorises a "recent-half / previous-half" units-sold pair into a
     * trend badge. Both halves at zero → null (no signal). One half at
     * zero → the non-zero direction. Otherwise ratio-based buckets keep
     * small variance from flipping the badge every refresh.
     */
    private String bucketTrend(BigDecimal recent, BigDecimal previous) {
        BigDecimal r = recent != null ? recent : BigDecimal.ZERO;
        BigDecimal p = previous != null ? previous : BigDecimal.ZERO;
        if (r.signum() == 0 && p.signum() == 0) return null;
        if (p.signum() == 0) return "UP";     // zero → positive
        if (r.signum() == 0) return "DOWN";   // positive → zero
        double ratio = r.doubleValue() / p.doubleValue();
        if (ratio >= TREND_UP_THRESHOLD)   return "UP";
        if (ratio <= TREND_DOWN_THRESHOLD) return "DOWN";
        return "FLAT";
    }

    /**
     * Recommended units to reorder. Resolution order (Zoho-style):
     * <ol>
     *   <li>If {@code reorder_qty} is set — return that fixed quantity
     *       (the buyer has said "always buy N at a time").</li>
     *   <li>Otherwise apply the ROP formula:
     *       {@code max(reorder_point + safety_stock + demand_during_lead_time − current − onOrder, 0)}
     *       where {@code demand_during_lead_time = avgDaily × leadTimeDays},
     *       falling back to {@code SUGGESTED_COVER_DAYS} if lead time is unset.</li>
     *   <li>If the shop has no velocity data yet, we fall back to the simple
     *       threshold gap so a brand-new SKU still gets a reasonable number.</li>
     * </ol>
     * Never negative, always ceilinged to a whole unit.
     */
    private BigDecimal computeSuggestedQty(ItemVariant variant, BigDecimal current,
                                           BigDecimal onOrder, BigDecimal avgDaily) {
        // 1. Fixed reorder quantity wins.
        if (variant.getReorderQty() != null && variant.getReorderQty().compareTo(BigDecimal.ZERO) > 0) {
            return variant.getReorderQty().setScale(0, RoundingMode.CEILING);
        }

        // 2. Resolve inputs — reorder point falls back to lowStockThreshold,
        //    safety stock and lead time to zero / SUGGESTED_COVER_DAYS.
        BigDecimal reorderPoint = variant.getReorderPoint() != null
                ? variant.getReorderPoint()
                : (variant.getLowStockThreshold() != null ? variant.getLowStockThreshold() : BigDecimal.ZERO);
        BigDecimal safety = variant.getSafetyStock() != null ? variant.getSafetyStock() : BigDecimal.ZERO;
        int leadDays = variant.getLeadTimeDays() != null && variant.getLeadTimeDays() > 0
                ? variant.getLeadTimeDays()
                : SUGGESTED_COVER_DAYS;

        // Simple gap — used as the floor of the suggestion.
        BigDecimal gap = reorderPoint.subtract(current).subtract(onOrder);
        BigDecimal suggested = gap;

        // 3. Velocity branch — add lead-time cover on top of the reorder
        //    point and safety stock. Requires a non-zero velocity.
        if (avgDaily != null && avgDaily.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal cover = avgDaily.multiply(BigDecimal.valueOf(leadDays));
            BigDecimal target = reorderPoint.add(safety).add(cover);
            BigDecimal velocityBased = target.subtract(current).subtract(onOrder);
            if (velocityBased.compareTo(suggested) > 0) suggested = velocityBased;
        }

        // 4. Never suggest more than max_stock allows (advisory cap).
        if (variant.getMaxStock() != null && variant.getMaxStock().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal capAtMax = variant.getMaxStock().subtract(current).subtract(onOrder);
            if (capAtMax.compareTo(BigDecimal.ZERO) < 0) capAtMax = BigDecimal.ZERO;
            if (suggested.compareTo(capAtMax) > 0) suggested = capAtMax;
        }

        if (suggested.compareTo(BigDecimal.ZERO) < 0) suggested = BigDecimal.ZERO;
        return suggested.setScale(0, RoundingMode.CEILING);
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
     * Used by shops that track perishables (FMCG, food, cosmetics).
     *
     * @param daysBeforeExpiry number of days ahead to check (e.g., 90 means warn 90 days before expiry)
     * @return list of expiry alert DTOs sorted by expiry date ascending
     */
    public List<ExpiryAlertDto> getExpiryAlerts(int daysBeforeExpiry) {
        LocalDate cutoffDate = LocalDate.now().plusDays(daysBeforeExpiry);
        Long shopId = com.desitech.vyaparsathi.common.configs.TenantContext.getCurrentShopId();
        List<ItemVariant> expiringVariants = itemVariantRepository.findByExpiryDateOnOrBefore(shopId, cutoffDate);

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