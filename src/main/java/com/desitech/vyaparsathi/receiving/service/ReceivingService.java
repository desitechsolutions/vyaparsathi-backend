package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.inventory.enums.StockMovementType;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.entity.StockMovement;
import com.desitech.vyaparsathi.inventory.repository.StockMovementRepository;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import com.desitech.vyaparsathi.purchaseorder.enums.EventType;
import com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus;
import com.desitech.vyaparsathi.purchaseorder.events.PurchaseOrderEvent;
import com.desitech.vyaparsathi.purchaseorder.events.PurchaseOrderProducer;
import com.desitech.vyaparsathi.purchaseorder.events.dto.PurchaseOrderEventDto;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderItemRepository;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import com.desitech.vyaparsathi.receiving.dto.*;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.entity.ReceivingItem;
import com.desitech.vyaparsathi.receiving.entity.ReceivingTicket;
import com.desitech.vyaparsathi.receiving.entity.ReceivingTicketAttachment;
import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.receiving.enums.ReceivingItemStatus;
import com.desitech.vyaparsathi.receiving.enums.ReceivingStatus;
import com.desitech.vyaparsathi.receiving.enums.ReceivingTicketStatus;
import com.desitech.vyaparsathi.receiving.mapper.ReceivingMapper;
import com.desitech.vyaparsathi.receiving.entity.ReceivingStatusHistory;
import com.desitech.vyaparsathi.receiving.repository.ReceivingRepository;
import com.desitech.vyaparsathi.receiving.repository.ReceivingStatusHistoryRepository;
import com.desitech.vyaparsathi.receiving.repository.ReceivingTicketRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReceivingService {
    private static final Logger logger = LoggerFactory.getLogger(ReceivingService.class);

    private final ReceivingRepository receivingRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final ShopRepository shopRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ReceivingTicketRepository receivingTicketRepository;
    private final ReceivingStatusHistoryRepository statusHistoryRepository;
    private final ReceivingMapper receivingMapper;
    private final GrnNumberService grnNumberService;
    private final UserRepository userRepository;
    private final PurchaseOrderProducer purchaseOrderProducer;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ReceivingNotificationService notificationService;

    /** Auto-ticket threshold — shortage as % of ordered qty. Configurable per shop later. */
    @Value("${app.receiving.auto-ticket.shortage-pct:20}")
    private BigDecimal autoTicketShortagePct;

    @Value("${app.default.shop.id:1}")  // Configurable default shop ID
    private Long defaultShopId;

    /**
     * Minimum days of shelf life a batch must have to be accepted. Callers
     * can override via {@code app.receiving.min-shelf-life-days} in application.yml.
     * Default 7 days keeps supermarket-style receipts safe while allowing
     * near-expiry acceptance with an explicit override reason on the line.
     */
    @Value("${app.receiving.min-shelf-life-days:7}")
    private int minShelfLifeDays;

    // Constructor injection for better testability
    public ReceivingService(ReceivingRepository receivingRepository,
                            PurchaseOrderRepository purchaseOrderRepository,
                            PurchaseOrderItemRepository purchaseOrderItemRepository,
                            ShopRepository shopRepository,
                            StockMovementRepository stockMovementRepository,
                            ReceivingTicketRepository receivingTicketRepository,
                            ReceivingStatusHistoryRepository statusHistoryRepository,
                            ReceivingMapper receivingMapper,
                            GrnNumberService grnNumberService,
                            UserRepository userRepository,
                            PurchaseOrderProducer purchaseOrderProducer) {
        this.receivingRepository = receivingRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.shopRepository = shopRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.receivingTicketRepository = receivingTicketRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.receivingMapper = receivingMapper;
        this.grnNumberService = grnNumberService;
        this.userRepository = userRepository;
        this.purchaseOrderProducer = purchaseOrderProducer;
    }

    /**
     * Records a single status transition on the audit log. Runs in the caller's
     * transaction so a rollback voids the history entry too. Silent no-op when
     * from == to; enterprise dashboards only care about actual transitions.
     */
    private void recordStatusChange(Receiving r, ReceivingStatus fromStatus, ReceivingStatus toStatus,
                                    String changedBy, String note) {
        if (fromStatus == toStatus) return;
        ReceivingStatusHistory h = new ReceivingStatusHistory();
        h.setReceivingId(r.getId());
        h.setShop(r.getShop());
        h.setFromStatus(fromStatus != null ? fromStatus.name() : null);
        h.setToStatus(toStatus.name());
        h.setChangedBy(changedBy != null ? changedBy : "system");
        h.setChangedAt(LocalDateTime.now());
        h.setNote(note);
        statusHistoryRepository.save(h);
    }

    /**
     * Retrieves all receivings for the current shop as a flat list (no pagination).
     * The frontend expects a simple array, not a Spring Page wrapper.
     */
    public List<ReceivingDto> getAllReceivings() {
        Long shopId = TenantUtils.getCurrentShopId();
        return receivingRepository.findAllByShopId(shopId).stream()
                .map(receivingMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a receiving by ID.
     */
    public ReceivingDto getReceivingById(Long id) {
        Receiving receiving = receivingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receiving not found with ID: " + id));
        return receivingMapper.toDto(receiving);
    }

    @Transactional
    public ReceivingDto createReceiving(ReceivingDto receivingDto) {
        validateDtoForCreateOrUpdate(receivingDto, false);

        PurchaseOrder purchaseOrder = getPurchaseOrder(receivingDto.getPurchaseOrderId());
        if (!PurchaseOrderStatus.SUBMITTED.equals(purchaseOrder.getStatus()) && !PurchaseOrderStatus.PARTIALLY_RECEIVED.equals(purchaseOrder.getStatus())) {
            throw new BusinessValidationException("Cannot create a receiving record for a PO that is not in SUBMITTED or PARTIALLY_RECEIVED status.");
        }
        Shop shop = getShop(receivingDto.getShopId());

        Receiving receiving = createPendingReceiving(purchaseOrder, receivingDto.getNotes(), receivingDto.getReceivedBy());
        receiving.setShop(shop);  // Override default if specified
        receiving.setSupplierInvoiceNo(receivingDto.getSupplierInvoiceNo());
        receiving.setSupplierInvoiceDate(receivingDto.getSupplierInvoiceDate());
        receiving.setVehicleNo(receivingDto.getVehicleNo());
        receiving.setDeliveryChallanNo(receivingDto.getDeliveryChallanNo());
        applyStatutoryFields(receiving, receivingDto);

        // Process detailed items with validation
        List<ReceivingItem> receivingItems = processReceivingItems(receivingDto.getReceivingItems(), receiving, true);
        receiving.setItems(receivingItems);

        Receiving finalReceiving = receivingRepository.save(receiving);

        // For create, old is empty
        adjustStockDeltas(finalReceiving, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

        updateReceivingStatus(finalReceiving);
        updatePOStatus(purchaseOrder);

        logger.info("Created receiving ID {} for PO ID {}", finalReceiving.getId(), purchaseOrder.getId());
        return receivingMapper.toDto(finalReceiving);
    }

    @Transactional
    public Optional<ReceivingDto> updateReceiving(Long id, ReceivingDto receivingDto) {
        return receivingRepository.findById(id)
                .map(existingReceiving -> {
                    validateDtoForCreateOrUpdate(receivingDto, true);

                    // Collect old state for deltas before changes
                    Map<Long, Integer> oldReceivedQtys = existingReceiving.getItems().stream()
                            .collect(Collectors.toMap(ReceivingItem::getId, item -> Optional.ofNullable(item.getReceivedQty()).orElse(0)));
                    Map<Long, ItemVariant> oldVariants = existingReceiving.getItems().stream()
                            .collect(Collectors.toMap(ReceivingItem::getId, item -> item.getPurchaseOrderItem().getItemVariant()));
                    Map<Long, BigDecimal> oldCosts = existingReceiving.getItems().stream()
                            .collect(Collectors.toMap(ReceivingItem::getId, item -> item.getPurchaseOrderItem().getUnitCost()));

                    // Update allowed fields
                    existingReceiving.setNotes(receivingDto.getNotes());
                    existingReceiving.setSupplierInvoiceNo(receivingDto.getSupplierInvoiceNo());
                    existingReceiving.setSupplierInvoiceDate(receivingDto.getSupplierInvoiceDate());
                    existingReceiving.setVehicleNo(receivingDto.getVehicleNo());
                    existingReceiving.setDeliveryChallanNo(receivingDto.getDeliveryChallanNo());
                    applyStatutoryFields(existingReceiving, receivingDto);

                    // Process items with update logic (add/remove/update)
                    List<ReceivingItem> updatedItems = processReceivingItems(receivingDto.getReceivingItems(), existingReceiving, false);
                    Set<Long> updatedItemIds = updatedItems.stream()
                            .map(ReceivingItem::getId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());

                    // Remove items that are no longer in the DTO
                    existingReceiving.getItems().removeIf(item -> !updatedItemIds.contains(item.getId()));

                    // Add/update items
                    for (ReceivingItem updatedItem : updatedItems) {
                        if (updatedItem.getId() == null) {
                            existingReceiving.getItems().add(updatedItem);
                        }
                    }

                    // DRAFT edits are pure metadata — stock movements + PO progress
                    // are deferred until confirmReceiving flips DRAFT → PENDING.
                    boolean isDraft = ReceivingStatus.DRAFT.equals(existingReceiving.getStatus());
                    Receiving savedReceiving = receivingRepository.save(existingReceiving);

                    if (!isDraft) {
                        adjustStockDeltas(savedReceiving, oldReceivedQtys, oldVariants, oldCosts);
                        updateReceivingStatus(savedReceiving);
                        updatePOStatus(savedReceiving.getPurchaseOrder());
                    }

                    logger.info("Updated receiving ID {} (draft={}) for PO ID {}",
                            id, isDraft, savedReceiving.getPurchaseOrder().getId());
                    return receivingMapper.toDto(savedReceiving);
                });
    }

    /**
     * Validates ReceivingDto for create/update, including non-negative quantities
     * and (V90 addition) expiry-window enforcement for perishables. Serial-number
     * / batch fields are lenient — validation is triggered when the line supplies
     * an expiry date at all.
     */
    private void validateDtoForCreateOrUpdate(ReceivingDto dto, boolean isUpdate) {
        if (CollectionUtils.isEmpty(dto.getReceivingItems())) {
            throw new BusinessValidationException("Receiving items cannot be empty");
        }
        LocalDate expiryFloor = LocalDate.now().plusDays(minShelfLifeDays);
        for (ReceivingItemDto itemDto : dto.getReceivingItems()) {
            int recv   = Optional.ofNullable(itemDto.getReceivedQty()).orElse(0);
            int dmg    = Optional.ofNullable(itemDto.getDamagedQty()).orElse(0);
            int rej    = Optional.ofNullable(itemDto.getRejectedQty()).orElse(0);
            int putaway = Optional.ofNullable(itemDto.getPutawayQty()).orElse(0);
            if (recv < 0 || dmg < 0 || rej < 0 || putaway < 0) {
                throw new BusinessValidationException("Quantities cannot be negative");
            }
            // Perishable guard: block items dated inside the shelf-life floor
            // unless the line explicitly flags an override rationale (reuses
            // the overage-reason field — near-expiry is a policy exception).
            if (itemDto.getExpiryDate() != null
                    && recv > 0
                    && itemDto.getExpiryDate().isBefore(expiryFloor)
                    && (itemDto.getOverageReason() == null || itemDto.getOverageReason().isBlank())) {
                throw new BusinessValidationException(
                        "Item expires on " + itemDto.getExpiryDate()
                        + " which is inside the " + minShelfLifeDays
                        + "-day shelf-life floor. Provide an override reason to accept.");
            }
        }
    }

    /**
     * Processes ReceivingItems, calculates status, and performs quantity validation.
     */
    private List<ReceivingItem> processReceivingItems(List<ReceivingItemDto> itemDtos, Receiving receiving, boolean isCreate) {
        List<ReceivingItem> items = new ArrayList<>();
        Map<Long, ReceivingItem> existingMap = isCreate ? new HashMap<>() : receiving.getItems().stream()
                .collect(Collectors.toMap(ReceivingItem::getId, i -> i));

        for (ReceivingItemDto itemDto : itemDtos) {
            ReceivingItem item;
            if (itemDto.getId() != null && existingMap.containsKey(itemDto.getId())) {
                item = existingMap.get(itemDto.getId());
            } else {
                item = new ReceivingItem();
                item.setReceiving(receiving);
            }

            PurchaseOrderItem poItem = getPurchaseOrderItem(itemDto.getPurchaseOrderItemId());

            validateItemQuantities(poItem, itemDto, item);

            item.setPurchaseOrderItem(poItem);
            item.setExpectedQty(poItem.getQuantity());
            item.setReceivedQty(Optional.ofNullable(itemDto.getReceivedQty()).orElse(0));
            item.setDamagedQty(Optional.ofNullable(itemDto.getDamagedQty()).orElse(0));
            item.setDamageReason(itemDto.getDamageReason());
            item.setRejectedQty(Optional.ofNullable(itemDto.getRejectedQty()).orElse(0));
            item.setRejectReason(itemDto.getRejectReason());
            item.setPutawayQty(Optional.ofNullable(itemDto.getPutawayQty()).orElse(0));
            item.setPutAwayStatus(itemDto.getPutAwayStatus());
            item.setNotes(itemDto.getNotes());
            item.setStatus(determineReceivingItemStatus(item));

            // Overage tracking
            item.setIsOveraged(Boolean.TRUE.equals(itemDto.getIsOveraged()));
            item.setOverageReason(itemDto.getOverageReason());
            item.setOverageNotes(itemDto.getOverageNotes());

            // Batch / expiry tracking (FMCG, food perishables, cosmetics)
            item.setBatchNumber(itemDto.getBatchNumber());
            item.setManufacturingDate(itemDto.getManufacturingDate());
            item.setExpiryDate(itemDto.getExpiryDate());

            // Electronics fields
            item.setSerialNumber(itemDto.getSerialNumber());
            item.setWarrantyStartDate(itemDto.getWarrantyStartDate());

            // Automobile fields
            item.setPartReference(itemDto.getPartReference());

            items.add(item);
        }
        return items;
    }

    /**
     * Validates quantities and throws an exception if the total exceeds the original PO quantity or is negative.
     */
    private void validateItemQuantities(PurchaseOrderItem poItem, ReceivingItemDto newItemDto, ReceivingItem existingItem) {
        int currentlyInThisRecord = existingItem != null ?
                (Optional.ofNullable(existingItem.getReceivedQty()).orElse(0) +
                        Optional.ofNullable(existingItem.getDamagedQty()).orElse(0) +
                        Optional.ofNullable(existingItem.getRejectedQty()).orElse(0)) : 0;

        ReceivingQtySummary summary = receivingRepository.getQtySummaryForPOItem(poItem.getId(), TenantUtils.getCurrentShopId());

        // Calculate what was received in OTHER records
        long previouslyReceivedInOtherRecords = summary.received() + summary.damaged() + summary.rejected() - currentlyInThisRecord;

        int totalNewRequest = newItemDto.getReceivedQty() + newItemDto.getDamagedQty() + newItemDto.getRejectedQty();
        long cumulativeTotal = previouslyReceivedInOtherRecords + totalNewRequest;

        // Check if this is an overage
        if (cumulativeTotal > poItem.getQuantity()) {
            // Instead of throwing an error, we ensure the DTO has provided a reason
            if (newItemDto.getOverageReason() == null || newItemDto.getOverageReason().isBlank()) {
                throw new BusinessValidationException(
                        "Overage detected for item " + poItem.getId() + ". A justification reason is required to exceed PO quantity."
                );
            }
            logger.warn("Overage recorded for PO Item {}: Ordered {}, Receiving {}",
                    poItem.getId(), poItem.getQuantity(), cumulativeTotal);
        }
    }
    /**
     * Helper to determine the status of a single ReceivingItem.
     */
    private ReceivingItemStatus determineReceivingItemStatus(ReceivingItem item) {
        int totalReceived = Optional.ofNullable(item.getReceivedQty()).orElse(0) +
                Optional.ofNullable(item.getDamagedQty()).orElse(0) +
                Optional.ofNullable(item.getRejectedQty()).orElse(0);

        if (totalReceived == 0) return ReceivingItemStatus.PENDING;

        // Status is RECEIVED if we met OR exceeded the expected quantity
        if (totalReceived >= item.getExpectedQty()) {
            return ReceivingItemStatus.RECEIVED;
        }
        return ReceivingItemStatus.PARTIALLY_RECEIVED;
    }

    /**
     * Helper to update the overall Receiving status based on its items.
     */
    private void updateReceivingStatus(Receiving receiving) {
        List<ReceivingItem> items = receiving.getItems();
        // No items to classify: an empty GRN can't be "all-received" (that
        // would trip Stream.allMatch's vacuous-true default). Keep it PENDING
        // so the confirm-then-fill path stays in a sane state.
        if (CollectionUtils.isEmpty(items)) {
            receiving.setStatus(ReceivingStatus.PENDING);
            return;
        }
        boolean allReceived = items.stream()
                .allMatch(item -> item.getStatus() == ReceivingItemStatus.RECEIVED);
        boolean anyReceived = items.stream()
                .anyMatch(item -> item.getStatus() == ReceivingItemStatus.RECEIVED || item.getStatus() == ReceivingItemStatus.PARTIALLY_RECEIVED);

        if (allReceived) {
            receiving.setStatus(ReceivingStatus.COMPLETED);
        } else if (anyReceived) {
            receiving.setStatus(ReceivingStatus.PARTIALLY_RECEIVED);
        } else {
            receiving.setStatus(ReceivingStatus.PENDING);
        }
    }

    /**
     * Helper to fetch PurchaseOrder with custom exception.
     */
    private PurchaseOrder getPurchaseOrder(Long id) {
        logger.info("Fetching PO with ID: {}", id);
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + id));
    }

    /**
     * Helper to fetch Shop.
     */
    private Shop getShop(Long id) {
        return shopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found with ID: " + id));
    }

    /**
     * Helper to fetch PurchaseOrderItem.
     */
    private PurchaseOrderItem getPurchaseOrderItem(Long id) {
        return purchaseOrderItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order Item not found with ID: " + id));
    }

    /**
     * Extracted method to create a pending Receiving entity with default items from PO.
     * Reduces duplication in createReceiving, receiveGoods, and processPurchaseOrderEvent.
     */
    private Receiving createPendingReceiving(PurchaseOrder po, String notes, String receivedBy) {
        Shop defaultShop = getDefaultShop();

        Receiving receiving = new Receiving();
        // V87: sequential, human-readable GRN number (GRN/YY-YY/NNNNN) via the
        // per-shop sequence. Retires the timestamp-based scheme that was
        // collision-prone and unfriendly to print.
        receiving.setGrNumber(grnNumberService.nextGrnNumber(
                defaultShop.getId(), java.time.LocalDate.now()));
        receiving.setPurchaseOrder(po);
        receiving.setStatus(ReceivingStatus.PENDING);
        receiving.setNotes(Optional.ofNullable(notes).orElse("Auto-created receiving record."));
        if (receivedBy != null) {
            receiving.setReceivedBy(receivedBy);
        }
        receiving.setReceivedAt(LocalDateTime.now());
        receiving.setShop(defaultShop);

        List<ReceivingItem> receivingItems = po.getItems().stream()
                .map(poItem -> createPendingReceivingItem(poItem, receiving))
                .collect(Collectors.toList());
        receiving.setItems(receivingItems);

        return receiving;
    }

    /**
     * Gets the current tenant's shop using TenantContext as the primary source.
     *
     * <p>Phase 0.7 fix: TenantContext.getCurrentShopId() is now the authoritative source.
     * The {@code defaultShopId} config property is used ONLY when the context is not set
     * (e.g. during application startup or scheduled jobs), not for normal web requests.
     * This prevents cross-tenant stock assignments when events are processed on behalf of a PO.
     */
    private Shop getDefaultShop() {
        // 1. Prefer TenantContext (set by JWT filter for all web requests)
        Long tenantShopId = com.desitech.vyaparsathi.common.configs.TenantContext.getCurrentShopId();
        if (tenantShopId != null) {
            return shopRepository.findById(tenantShopId)
                    .orElseThrow(() -> new ResourceNotFoundException("Shop not found for current tenant: " + tenantShopId));
        }

        // 2. Fallback: use configured defaultShopId (for event-driven / scheduled processing)
        if (defaultShopId != null) {
            return shopRepository.findById(defaultShopId)
                    .orElseThrow(() -> new ResourceNotFoundException("Default shop not found with ID: " + defaultShopId));
        }

        // 3. Last resort: reject rather than guessing, to prevent cross-tenant contamination
        throw new IllegalStateException(
                "Cannot determine shop for receiving operation: TenantContext is not set and defaultShopId is not configured.");
    }

    /**
     * Helper to create a pending ReceivingItem from PurchaseOrderItem.
     */
    private ReceivingItem createPendingReceivingItem(PurchaseOrderItem poItem, Receiving receiving) {
        ReceivingItem item = new ReceivingItem();
        item.setPurchaseOrderItem(poItem);
        item.setStatus(ReceivingItemStatus.PENDING);
        item.setExpectedQty(poItem.getQuantity());  // Safe casting
        item.setReceivedQty(0);
        item.setDamagedQty(0);
        item.setNotes(null);
        item.setReceiving(receiving);
        return item;
    }

    /**
     * Adjusts stock levels based on deltas between old and new states.
     */
    private void adjustStockDeltas(Receiving newReceiving, Map<Long, Integer> oldReceivedQtys,
                                   Map<Long, ItemVariant> oldVariants, Map<Long, BigDecimal> oldCosts) {
        if (CollectionUtils.isEmpty(newReceiving.getItems()) && oldReceivedQtys.isEmpty()) {
            return;
        }

        // Handle added and updated items
        for (ReceivingItem item : newReceiving.getItems()) {
            Long itemId = item.getId();
            int oldQty = oldReceivedQtys.getOrDefault(itemId, 0);
            int currentAccepted = item.getAcceptedQty();
            int delta = currentAccepted - oldQty;
            if (delta != 0) {
                ItemVariant variant = item.getPurchaseOrderItem().getItemVariant();
                BigDecimal cost = item.getUnitCost() != null ? item.getUnitCost() : item.getPurchaseOrderItem().getUnitCost();
                StockMovementType type = delta > 0 ? StockMovementType.ADD : StockMovementType.DEDUCT;
                createStockMovement(variant, cost, type, Math.abs(delta), newReceiving.getId().toString(),
                        item.getBatchNumber(), item.getExpiryDate());
            }
        }

        // Handle removed items
        Set<Long> newItemIds = newReceiving.getItems().stream()
                .map(ReceivingItem::getId)
                .collect(Collectors.toSet());
        for (Long removedId : oldReceivedQtys.keySet()) {
            if (!newItemIds.contains(removedId)) {
                int oldQty = oldReceivedQtys.get(removedId);
                if (oldQty > 0) {
                    ItemVariant variant = oldVariants.get(removedId);
                    BigDecimal cost = oldCosts.get(removedId);
                    createStockMovement(variant, cost, StockMovementType.DEDUCT, oldQty, newReceiving.getId().toString(), null, null);
                }
            }
        }
    }

    /**
     * Creates a stock movement, including optional batch and expiry (FMCG / food perishables).
     */
    private void createStockMovement(ItemVariant variant, BigDecimal cost, StockMovementType type, int quantity, String reference,
                                     String batchNumber, LocalDate expiryDate) {
        StockMovement stockMovement = new StockMovement();
        stockMovement.setItemVariant(variant);
        stockMovement.setMovementType(type);
        BigDecimal qty = BigDecimal.valueOf(quantity);
        stockMovement.setQuantity(type == StockMovementType.DEDUCT ? qty.negate() : qty);
        stockMovement.setCostPerUnit(cost);
        stockMovement.setBatch(batchNumber);
        stockMovement.setExpiryDate(expiryDate);
        stockMovement.setReason("Purchase Order Receiving Adjustment");
        stockMovement.setReference(reference);
        stockMovementRepository.save(stockMovement);
    }

    /**
     * Updates PO status based on all receivings AND materialises the cumulative
     * received quantity onto each {@link PurchaseOrderItem} so the FE can render
     * an accurate per-line progress bar (V81 addition; wired in Phase 2).
     *
     * <p>Prior to this write-back, the PO detail page fell back to zero for every
     * line even after a full receipt — status flipped to RECEIVED but the "0/10"
     * counters lied. Now the PO carries authoritative per-line receipts.
     */
    private void updatePOStatus(PurchaseOrder po) {
        if (CollectionUtils.isEmpty(po.getItems())) {
            logger.warn("PO {} has no items. Cannot update status.", po.getId());
            return;
        }

        boolean allPoItemsReceived = true;
        for (PurchaseOrderItem poItem : po.getItems()) {
            ReceivingQtySummary qtySummary = receivingRepository.getQtySummaryForPOItem(poItem.getId(), TenantUtils.getCurrentShopId());
            // Summary fields are long — keep them long through the arithmetic
            // and only narrow when we compare to poItem.getQuantity() (Integer).
            long received = qtySummary.received() + qtySummary.damaged() + qtySummary.rejected();
            // received/damaged/rejected all count as "arrived from the line" —
            // matches the same math the completeness check below uses.
            poItem.setReceivedQuantity(java.math.BigDecimal.valueOf(received));
            if (poItem.getQuantity().longValue() > received) {
                allPoItemsReceived = false;
            }
        }

        // Capture prior status BEFORE mutation so we only emit RECEIVED on the
        // transition edge — matches how PurchaseOrderService fires APPROVED /
        // REJECTED / REVISED exactly once per state change.
        PurchaseOrderStatus prevStatus = po.getStatus();
        PurchaseOrderStatus nextStatus = allPoItemsReceived
                ? PurchaseOrderStatus.RECEIVED
                : PurchaseOrderStatus.PARTIALLY_RECEIVED;
        po.setStatus(nextStatus);
        PurchaseOrder saved = purchaseOrderRepository.save(po); // CascadeType.ALL on items — flushes receivedQuantity too.
        logger.info("Updated PO {} status to {}", saved.getPoNumber(), saved.getStatus());

        if (nextStatus == PurchaseOrderStatus.RECEIVED && prevStatus != PurchaseOrderStatus.RECEIVED) {
            // Fire once when the PO first fully lands. Analytics, inventory
            // reconciliation, and supplier-performance listeners subscribe to
            // this — previously the flag flipped silently and nothing knew.
            purchaseOrderProducer.sendMessage(EventType.RECEIVED, PurchaseOrderEventDto.fromEntity(saved));
        }
    }

    @Transactional
    public void deleteReceiving(Long id) {
        Optional<Receiving> opt = receivingRepository.findById(id);
        if (opt.isPresent()) {
            Receiving receiving = opt.get();
            PurchaseOrder po = receiving.getPurchaseOrder();

            // Collect old state for reversal
            Map<Long, Integer> oldReceivedQtys = receiving.getItems().stream()
                    .collect(Collectors.toMap(ReceivingItem::getId, item -> Optional.ofNullable(item.getReceivedQty()).orElse(0)));
            Map<Long, ItemVariant> oldVariants = receiving.getItems().stream()
                    .collect(Collectors.toMap(ReceivingItem::getId, item -> item.getPurchaseOrderItem().getItemVariant()));
            Map<Long, BigDecimal> oldCosts = receiving.getItems().stream()
                    .collect(Collectors.toMap(ReceivingItem::getId, item -> item.getPurchaseOrderItem().getUnitCost()));

            receivingRepository.deleteById(id);

            // Adjust as if new is empty
            Receiving dummy = new Receiving();
            dummy.setItems(Collections.emptyList());
            dummy.setId(id);  // For reference
            adjustStockDeltas(dummy, oldReceivedQtys, oldVariants, oldCosts);

            updatePOStatus(po);

            logger.info("Deleted receiving ID {}", id);
        } else {
            throw new ResourceNotFoundException("Receiving not found with ID: " + id);
        }
    }

    @Transactional
    public ReceivingTicket createReceivingTicket(ReceivingTicketDTO receivingTicketDTO) {
        ReceivingDto receivingDto = getReceivingById(receivingTicketDTO.getReceivingId());  // Reuse getter for consistency
        Receiving receiving = receivingMapper.toEntity(receivingDto);

        ReceivingTicket receivingTicket = new ReceivingTicket();
        receivingTicket.setReceiving(receiving);
        receivingTicket.setReason(receivingTicketDTO.getReason());
        receivingTicket.setDescription(receivingTicketDTO.getDescription());
        receivingTicket.setStatusEnum(ReceivingTicketStatus.OPEN);
        receivingTicket.setRaisedAt(LocalDateTime.now());
        receivingTicket.setRaisedBy(receivingTicketDTO.getRaisedBy());

        return receivingTicketRepository.save(receivingTicket);
    }

    public Optional<ReceivingTicket> getReceivingTicketById(Long id) {
        return receivingTicketRepository.findById(id);
    }

    public List<ReceivingTicket> getReceivingTicketByReceivingId(Long id) {
        return receivingTicketRepository.findByReceiving_Id(id);
    }

    @Transactional
    public Optional<ReceivingTicket> updateReceivingTicket(Long id, ReceivingTicketDTO dto) {
        return receivingTicketRepository.findById(id)
                .map(ticket -> {
                    if (dto.getReason() != null) ticket.setReason(dto.getReason());
                    if (dto.getDescription() != null) ticket.setDescription(dto.getDescription());
                    if (dto.getRaisedBy() != null) ticket.setRaisedBy(dto.getRaisedBy());
                    // Status transitions honored here so the "Mark in progress" / "Close"
                    // buttons on the tickets list actually persist. Terminal transitions
                    // (RESOLVED) still route through resolveReceivingTicket so the resolver
                    // audit stamp is captured — reject that shortcut here.
                    if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
                        ReceivingTicketStatus next = ReceivingTicketStatus.fromString(dto.getStatus());
                        if (next == ReceivingTicketStatus.RESOLVED) {
                            throw new BusinessValidationException(
                                    "Use /tickets/{id}/resolve to resolve a ticket — stamps the resolver.");
                        }
                        ticket.setStatusEnum(next);
                    }
                    return receivingTicketRepository.save(ticket);
                });
    }

    @Transactional
    public void deleteReceivingTicket(Long id) {
        receivingTicketRepository.findById(id).ifPresent(ticket -> {
            ticket.getAttachments().clear();  // Cleanup attachments (assumes cascade remove)
            receivingTicketRepository.delete(ticket);
        });
    }

    @Transactional
    public ReceivingTicket addAttachmentToTicket(Long ticketId, MultipartFile file) {
        ReceivingTicket ticket = receivingTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiving Ticket not found with ID: " + ticketId));

        ReceivingTicketAttachment attachment = new ReceivingTicketAttachment();
        attachment.setReceivingTicket(ticket);
        attachment.setFileName(file.getOriginalFilename());
        attachment.setFileType(file.getContentType());
        attachment.setFilePath("/uploads/" + file.getOriginalFilename());  // Assume storage service; expand as needed

        ticket.getAttachments().add(attachment);
        return receivingTicketRepository.save(ticket);
    }

    /**
     * Handles {@code POST /api/receiving/receive-goods}.
     *
     * <p><b>Create mode</b> (no {@code receivingId} in dto): creates a new PENDING receiving
     * record pre-populated with all PO items (zero quantities).</p>
     *
     * <p><b>Update mode</b> ({@code receivingId} present + {@code receivingItems}): updates
     * the existing receiving with actual received/damaged/rejected quantities, adjusts stock,
     * and updates PO status. This is the path used by the ReceiveGoodsForm wizard.</p>
     */
    @Transactional
    public ReceivingDto createInitialReceivingRecord(CreateReceivingDto createReceivingDto) {

        // ── UPDATE MODE ─────────────────────────────────────────────────────────
        if (createReceivingDto.getReceivingId() != null
                && !CollectionUtils.isEmpty(createReceivingDto.getReceivingItems())) {

            // Build a ReceivingDto from the CreateReceivingDto and delegate to updateReceiving
            ReceivingDto updateDto = new ReceivingDto();
            updateDto.setNotes(createReceivingDto.getNotes());
            updateDto.setReceivingItems(createReceivingDto.getReceivingItems());
            if (createReceivingDto.getShopId() != null) {
                updateDto.setShopId(createReceivingDto.getShopId());
            }

            return updateReceiving(createReceivingDto.getReceivingId(), updateDto)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Receiving not found with ID: " + createReceivingDto.getReceivingId()));
        }

        // ── CREATE MODE ─────────────────────────────────────────────────────────
        if (createReceivingDto.getPurchaseOrderId() == null) {
            throw new BusinessValidationException("Either receivingId (update mode) or purchaseOrderId (create mode) must be provided");
        }

        logger.info("Attempting to create receiving record for PO ID: {}", createReceivingDto.getPurchaseOrderId());

        PurchaseOrder po = getPurchaseOrder(createReceivingDto.getPurchaseOrderId());

        if (!PurchaseOrderStatus.SUBMITTED.equals(po.getStatus())
                && !PurchaseOrderStatus.PARTIALLY_RECEIVED.equals(po.getStatus())) {
            throw new BusinessValidationException(
                    "Cannot create a receiving record for a PO that is not in SUBMITTED or PARTIALLY_RECEIVED status.");
        }

        // Already-exists path — use a single query to avoid TOCTOU race condition
        List<Receiving> existingForPo = receivingRepository.findAllByPurchaseOrderId(po.getId());
        if (!existingForPo.isEmpty()) {
            logger.info("Receiving already exists for PO ID {}, returning existing record.", po.getId());
            return receivingMapper.toDto(existingForPo.get(0));
        }

        Receiving receiving = createPendingReceiving(po,
                createReceivingDto.getNotes() != null ? createReceivingDto.getNotes() : "Manually created receiving record.",
                createReceivingDto.getReceivedBy());
        if (createReceivingDto.getReceivedDate() != null) {
            receiving.setReceivedAt(createReceivingDto.getReceivedDate());
        }
        // The GrnCreatePage wizard invokes this create-only endpoint before
        // pushing quantities via updateReceiving. Persist as DRAFT so the
        // subsequent update stays metadata-only and stock commit is gated on
        // the explicit confirmReceiving click.
        receiving.setStatus(ReceivingStatus.DRAFT);
        Receiving savedReceiving = receivingRepository.save(receiving);
        logger.info("Successfully created DRAFT receiving record ID {} for PO ID {}",
                savedReceiving.getId(), po.getId());

        if (notificationService != null) {
            try { notificationService.onGrnCreated(savedReceiving); }
            catch (Exception e) { logger.warn("GRN_CREATED notification failed: {}", e.getMessage()); }
        }
        return receivingMapper.toDto(savedReceiving);
    }

    @Transactional
    public void processPurchaseOrderEvent(PurchaseOrderEvent event) {
        if (event.getEventType() != EventType.SUBMITTED) {
            logger.debug("Skipping event, type is not SUBMITTED: {}", event.getEventType());
            return;
        }

        PurchaseOrderEventDto poDto = event.getPurchaseOrder();
        if (poDto == null) {
            logger.warn("Received SUBMITTED event with null purchase order data.");
            return;
        }
        PurchaseOrder po = purchaseOrderRepository.findById(poDto.getId())
                .orElse(null);
        if (po == null) {
            logger.error("Could not find PurchaseOrder with ID {} from event.", poDto.getId());
            return;
        }

        if (!PurchaseOrderStatus.SUBMITTED.equals(po.getStatus())) {
            logger.warn("PO with ID {} is not in SUBMITTED status, skipping receiving creation.", po.getId());
            return;
        }

        if (receivingRepository.existsByPurchaseOrder(po)) {
            logger.info("Receiving record already exists for PO ID {}, skipping creation.", po.getId());
            return;
        }

        Receiving receiving = createPendingReceiving(po, "Auto-created from PO event", "system-user");
        receivingRepository.save(receiving);
        logger.info("Auto-created receiving for PO ID {} from event.", po.getId());
    }

    public List<ReceivingDto> getAllByPurchaseOrderId(Long poId) {
        List<Receiving> receivingList = receivingRepository.findAllByPurchaseOrderId(poId);
        return receivingList.stream().map(receivingMapper::toDto).collect(Collectors.toList());
    }

    public List<ReceivingDto> getAllByPoNumber(String poNumber) {
        List<Receiving> receivingList = receivingRepository.findAllByPoNumber(poNumber, TenantUtils.getCurrentShopId());
        return receivingList.stream().map(receivingMapper::toDto).collect(Collectors.toList());
    }

    public List<ReceivingTicket> getAllReceivingTickets() {
        // findAll() is auto-scoped by ShopFilterAspect (ticket extends
        // ShopAwareEntity), so this returns only the current tenant's tickets
        // as long as a request-level TenantContext is set — which is guaranteed
        // by the controller's @PreAuthorize.
        return receivingTicketRepository.findAll();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 6.1 — state-machine entry points for the redesigned Receiving UI
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Transitions a DRAFT GRN to PENDING (or its downstream completed states).
     * The current create-flow commits stock on save, so DRAFT is opt-in from
     * the redesigned wizard: callers save a DRAFT via {@link #createInitialReceivingRecord}
     * (without receivingItems), fill quantities via {@link #updateReceiving},
     * then invoke this to commit. It is idempotent for non-DRAFT statuses so a
     * retried FE click doesn't double-commit stock.
     */
    @Transactional
    public ReceivingDto confirmReceiving(Long id, String note) {
        Receiving receiving = receivingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receiving not found with ID: " + id));

        if (!ReceivingStatus.DRAFT.equals(receiving.getStatus())) {
            logger.info("confirmReceiving is a no-op for GRN {} in status {} — already committed",
                    receiving.getGrNumber(), receiving.getStatus());
            return receivingMapper.toDto(receiving);
        }

        if (note != null && !note.isBlank()) {
            String existing = Optional.ofNullable(receiving.getNotes()).orElse("");
            receiving.setNotes(existing.isBlank() ? note : (existing + "\n" + note));
        }

        // Flip status first so item-status recomputation classifies each line
        // by its committed state (PENDING / PARTIALLY_RECEIVED / COMPLETED).
        receiving.setStatus(ReceivingStatus.PENDING);
        updateReceivingStatus(receiving);
        Receiving saved = receivingRepository.save(receiving);

        // V93: if landed cost is opted-in, distribute freight into per-line
        // valuation before stock is committed so movements carry the right
        // cost. No-op when the flag is off or actual freight is zero/null.
        applyReceivingLandedCost(saved);

        // Draft rows carry zero committed stock — treat "old" as empty so this
        // pass emits ADD movements for every accepted quantity, exactly once.
        adjustStockDeltas(saved, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        updatePOStatus(saved.getPurchaseOrder());
        recordStatusChange(saved, ReceivingStatus.DRAFT, saved.getStatus(), saved.getReceivedBy(), note);
        detectCostVariance(saved);
        maybeAutoRaiseTicket(saved);

        logger.info("Confirmed GRN {} (id={}) — status now {}",
                saved.getGrNumber(), saved.getId(), saved.getStatus());
        return receivingMapper.toDto(saved);
    }

    /**
     * V93 receiving-side landed cost — distribute {@code freightActual} across
     * accepted-qty × unit-cost line values into {@code landedUnitCost}. Runs
     * only when the receiving-side flag is on so historical rows stay untouched.
     */
    private void applyReceivingLandedCost(Receiving r) {
        if (!r.isLandedCostEnabled() || r.getItems() == null || r.getItems().isEmpty()) {
            if (r.getItems() != null) r.getItems().forEach(i -> i.setLandedUnitCost(null));
            return;
        }
        BigDecimal freight = Optional.ofNullable(r.getFreightActual()).orElse(BigDecimal.ZERO);
        BigDecimal totalValue = BigDecimal.ZERO;
        for (ReceivingItem it : r.getItems()) {
            int qty = it.getAcceptedQty();
            BigDecimal cost = it.getUnitCost() != null
                    ? it.getUnitCost()
                    : Optional.ofNullable(it.getPurchaseOrderItem() != null ? it.getPurchaseOrderItem().getUnitCost() : null)
                            .orElse(BigDecimal.ZERO);
            totalValue = totalValue.add(cost.multiply(BigDecimal.valueOf(qty)));
        }
        if (totalValue.signum() == 0 || freight.signum() == 0) {
            r.getItems().forEach(it -> it.setLandedUnitCost(it.getUnitCost()));
            return;
        }
        for (ReceivingItem it : r.getItems()) {
            int qty = it.getAcceptedQty();
            if (qty == 0) { it.setLandedUnitCost(it.getUnitCost()); continue; }
            BigDecimal cost = it.getUnitCost() != null ? it.getUnitCost() : BigDecimal.ZERO;
            BigDecimal lineValue = cost.multiply(BigDecimal.valueOf(qty));
            BigDecimal share = lineValue.divide(totalValue, 6, java.math.RoundingMode.HALF_UP).multiply(freight);
            BigDecimal perUnit = share.divide(BigDecimal.valueOf(qty), 4, java.math.RoundingMode.HALF_UP);
            it.setLandedUnitCost(cost.add(perUnit).setScale(4, java.math.RoundingMode.HALF_UP));
        }
    }

    /**
     * Flags the GRN when any line's cost drifts more than 5% from the PO price.
     * The FE surfaces this as a chip on the detail page; downstream the flag is
     * a signal to route to 3-way match review before AP payment.
     */
    private void detectCostVariance(Receiving r) {
        if (r.getItems() == null) return;
        for (ReceivingItem it : r.getItems()) {
            if (it.getUnitCost() == null || it.getPurchaseOrderItem() == null) continue;
            BigDecimal poCost = it.getPurchaseOrderItem().getUnitCost();
            if (poCost == null || poCost.signum() == 0) continue;
            BigDecimal delta = it.getUnitCost().subtract(poCost).abs()
                    .divide(poCost, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            if (delta.compareTo(new BigDecimal("5")) > 0) {
                r.setCostVarianceFlag(true);
                logger.warn("Cost variance on GRN {}: line cost {} vs PO {} ({}%)",
                        r.getGrNumber(), it.getUnitCost(), poCost, delta);
                return;
            }
        }
    }

    /**
     * Auto-raise a dispute ticket when a line's shortage exceeds the shop's
     * configured threshold. Runs once per confirmed GRN (idempotency guarded
     * by the {@code auto_ticket_raised} flag).
     */
    private void maybeAutoRaiseTicket(Receiving r) {
        if (r.isAutoTicketRaised()) return;
        if (r.getItems() == null || r.getItems().isEmpty()) return;
        BigDecimal thresholdPct = autoTicketShortagePct != null ? autoTicketShortagePct : new BigDecimal("20");
        StringBuilder desc = new StringBuilder();
        for (ReceivingItem it : r.getItems()) {
            int ordered = it.getExpectedQty() != null ? it.getExpectedQty() : 0;
            if (ordered <= 0) continue;
            int accepted = it.getAcceptedQty();
            int shortage = ordered - accepted;
            if (shortage <= 0) continue;
            BigDecimal pct = BigDecimal.valueOf(shortage)
                    .divide(BigDecimal.valueOf(ordered), 4, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            if (pct.compareTo(thresholdPct) > 0) {
                if (desc.length() > 0) desc.append("; ");
                desc.append("Line ").append(it.getId())
                    .append(" short ").append(shortage).append("/").append(ordered)
                    .append(" (").append(pct.setScale(1, java.math.RoundingMode.HALF_UP)).append("%)");
            }
        }
        if (desc.length() == 0) return;

        try {
            ReceivingTicketDTO auto = new ReceivingTicketDTO();
            auto.setReceivingId(r.getId());
            auto.setReason("AUTO_SHORTAGE");
            auto.setDescription("Auto-raised: " + desc);
            auto.setRaisedBy("system");
            createReceivingTicket(auto);
            r.setAutoTicketRaised(true);
            receivingRepository.save(r);
        } catch (Exception e) {
            logger.warn("Auto-ticket creation failed for GRN {}: {}", r.getGrNumber(), e.getMessage());
        }
    }

    /**
     * Voids a GRN and reverses any stock committed by it. Cancellation reason
     * is mandatory — used in the supplier communication + audit log. Draft
     * GRNs are simply deleted (no stock to reverse), so this endpoint is only
     * relevant once the GRN is committed.
     */
    @Transactional
    public ReceivingDto cancelReceiving(Long id, String reason, Long userId) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessValidationException("Cancellation reason is required.");
        }
        Receiving receiving = receivingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receiving not found with ID: " + id));

        if (receiving.getStatus() == ReceivingStatus.CANCELLED) {
            throw new BusinessValidationException("GRN " + receiving.getGrNumber() + " is already cancelled.");
        }
        if (receiving.getStatus() == ReceivingStatus.DRAFT) {
            throw new BusinessValidationException("Delete draft GRNs instead of cancelling — no stock movements to reverse.");
        }

        // Capture current accepted quantities as the "old" state, then wipe
        // items to "new" state = zeros. adjustStockDeltas already emits DEDUCT
        // movements for that delta pattern.
        Map<Long, Integer> oldReceivedQtys = receiving.getItems().stream()
                .collect(Collectors.toMap(ReceivingItem::getId, item -> Optional.ofNullable(item.getReceivedQty()).orElse(0)));
        Map<Long, ItemVariant> oldVariants = receiving.getItems().stream()
                .collect(Collectors.toMap(ReceivingItem::getId, item -> item.getPurchaseOrderItem().getItemVariant()));
        Map<Long, BigDecimal> oldCosts = receiving.getItems().stream()
                .collect(Collectors.toMap(ReceivingItem::getId, item -> item.getPurchaseOrderItem().getUnitCost()));

        ReceivingStatus fromStatus = receiving.getStatus();
        receiving.setStatus(ReceivingStatus.CANCELLED);
        receiving.setCancellationReason(reason);
        receiving.setCancelledAt(LocalDateTime.now());
        receiving.setCancelledByUserId(userId);

        // Zero out the accepted qty by flipping received/damaged/rejected to 0.
        // Keeps the record inspectable — you still see what was originally
        // logged, but the effective accepted qty is 0 for stock and PO math.
        for (ReceivingItem it : receiving.getItems()) {
            it.setReceivedQty(0);
            it.setDamagedQty(0);
            it.setRejectedQty(0);
            it.setStatus(ReceivingItemStatus.PENDING);
        }

        Receiving saved = receivingRepository.save(receiving);
        adjustStockDeltas(saved, oldReceivedQtys, oldVariants, oldCosts);
        updatePOStatus(saved.getPurchaseOrder());
        recordStatusChange(saved, fromStatus, ReceivingStatus.CANCELLED,
                userId != null ? String.valueOf(userId) : saved.getReceivedBy(), reason);

        logger.info("Cancelled GRN {} (id={}) — reason: {}", saved.getGrNumber(), saved.getId(), reason);
        return receivingMapper.toDto(saved);
    }

    /** Read-side helper for the FE timeline widget. */
    @Transactional(readOnly = true)
    public List<ReceivingStatusHistory> getStatusHistory(Long receivingId) {
        // Existence check up front so a 404 is returned rather than an empty list.
        receivingRepository.findById(receivingId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiving not found with ID: " + receivingId));
        return statusHistoryRepository.findByReceivingIdOrderByChangedAtAsc(receivingId);
    }

    /**
     * Stamps approval metadata on a committed GRN. Only committed statuses
     * (PENDING / PARTIALLY_RECEIVED / COMPLETED) can be approved — DRAFT rows
     * must be confirmed first so stock movements exist for audit.
     */
    @Transactional
    public ReceivingDto approveReceiving(Long id, String note, Long approverUserId) {
        Receiving receiving = receivingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receiving not found with ID: " + id));

        if (!receiving.getStatus().isCommitted()) {
            throw new BusinessValidationException(
                    "GRN must be confirmed before approval (current status: " + receiving.getStatus() + ")");
        }
        if (receiving.getApprovedByUser() != null) {
            throw new BusinessValidationException("GRN " + receiving.getGrNumber() + " is already approved");
        }
        // Guard: don't sign off on an empty GRN. An auto-created PENDING has
        // all lines at zero qty until an operator records the physical arrival —
        // approving before that is a rubber-stamp on nothing.
        int totalAccepted = receiving.getItems() == null ? 0
                : receiving.getItems().stream().mapToInt(ReceivingItem::getAcceptedQty).sum();
        if (totalAccepted <= 0) {
            throw new BusinessValidationException(
                    "GRN " + receiving.getGrNumber() + " has no received quantities — record the goods received before approving.");
        }

        if (approverUserId != null) {
            User approver = userRepository.findById(approverUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Approver user not found: " + approverUserId));
            receiving.setApprovedByUser(approver);
        }
        receiving.setApprovedAt(LocalDateTime.now());
        receiving.setApprovalNote(note);

        Receiving saved = receivingRepository.save(receiving);
        // Approval stamps a history entry even though status doesn't shift —
        // pass same-status so recordStatusChange creates a synthetic "APPROVED"
        // marker for the timeline. Bypasses the from==to guard by using a
        // sentinel value in the note.
        ReceivingStatusHistory h = new ReceivingStatusHistory();
        h.setReceivingId(saved.getId());
        h.setShop(saved.getShop());
        h.setFromStatus(saved.getStatus().name());
        h.setToStatus("APPROVED");
        h.setChangedBy(approverUserId != null ? String.valueOf(approverUserId) : "system");
        h.setChangedAt(LocalDateTime.now());
        h.setNote(note);
        statusHistoryRepository.save(h);
        logger.info("Approved GRN {} (id={}) by userId={}",
                saved.getGrNumber(), saved.getId(), approverUserId);
        return receivingMapper.toDto(saved);
    }

    /**
     * Opens a top-level dispute on a GRN — thin wrapper over
     * {@link #createReceivingTicket} that stamps the caller as raiser and
     * defaults the reason to "DISPUTE". Kept separate so the FE can call a
     * clear /dispute endpoint without hand-rolling a ticket DTO.
     */
    @Transactional
    public ReceivingTicket disputeReceiving(Long receivingId, String note, String raisedByUsername) {
        // Existence check up front — createReceivingTicket dereferences via getReceivingById.
        receivingRepository.findById(receivingId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiving not found with ID: " + receivingId));

        ReceivingTicketDTO dto = new ReceivingTicketDTO();
        dto.setReceivingId(receivingId);
        dto.setReason("DISPUTE");
        dto.setDescription(note);
        dto.setRaisedBy(raisedByUsername);
        return createReceivingTicket(dto);
    }

    /**
     * Moves a dispute ticket to RESOLVED with resolver + timestamp + note.
     * Idempotent for already-terminal tickets so a retried FE click is safe.
     */
    @Transactional
    public ReceivingTicket resolveReceivingTicket(Long ticketId, String note, Long resolverUserId) {
        ReceivingTicket ticket = receivingTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiving Ticket not found with ID: " + ticketId));

        if (ticket.getStatusEnum().isTerminal()) {
            logger.info("resolveReceivingTicket no-op — ticket {} already {}", ticketId, ticket.getStatus());
            return ticket;
        }

        ticket.setStatusEnum(ReceivingTicketStatus.RESOLVED);
        ticket.setResolvedBy(resolverUserId);
        ticket.setResolvedAt(LocalDateTime.now());
        ticket.setResolutionNote(note);
        return receivingTicketRepository.save(ticket);
    }

    /**
     * V99 statutory-field copy — shared by create + update paths. Splits
     * "27-Maharashtra"-style PoS into state / state-code and stamps supply
     * type / reverse charge / address snapshots. All fields are optional;
     * absent fields on the DTO leave the entity's values untouched.
     */
    private static void applyStatutoryFields(Receiving r, CreateReceivingDto dto) {
        applyStatutoryFields(r, dto.getPlaceOfSupply(), dto.getSupplyType(),
                dto.getReverseCharge(), dto.getBillToAddress(), dto.getShipToAddress());
    }

    private static void applyStatutoryFields(Receiving r, ReceivingDto dto) {
        applyStatutoryFields(r, dto.getPlaceOfSupply(), dto.getSupplyType(),
                dto.getReverseCharge(), dto.getBillToAddress(), dto.getShipToAddress());
    }

    private static void applyStatutoryFields(Receiving r, String pos, String supplyType,
                                             Boolean reverseCharge, String billTo, String shipTo) {
        if (pos != null && !pos.isBlank()) {
            String[] parts = pos.split("-", 2);
            if (parts.length == 2 && parts[0].matches("\\d{2}")) {
                r.setPlaceOfSupplyStateCode(parts[0]);
                r.setPlaceOfSupplyState(parts[1].trim());
            } else {
                r.setPlaceOfSupplyState(pos);
            }
        }
        if (supplyType    != null) r.setSupplyType(supplyType);
        if (reverseCharge != null) r.setReverseCharge(reverseCharge);
        if (billTo        != null) r.setBillToPartySnapshot(billTo);
        if (shipTo        != null) r.setShipToPartySnapshot(shipTo);
    }
}