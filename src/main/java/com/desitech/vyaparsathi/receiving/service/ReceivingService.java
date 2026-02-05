package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.inventory.StockMovementType;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.entity.StockMovement;
import com.desitech.vyaparsathi.inventory.repository.StockMovementRepository;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import com.desitech.vyaparsathi.purchaseorder.enums.EventType;
import com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus;
import com.desitech.vyaparsathi.purchaseorder.events.PurchaseOrderEvent;
import com.desitech.vyaparsathi.purchaseorder.events.dto.PurchaseOrderEventDto;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderItemRepository;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import com.desitech.vyaparsathi.receiving.dto.*;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.entity.ReceivingItem;
import com.desitech.vyaparsathi.receiving.entity.ReceivingTicket;
import com.desitech.vyaparsathi.receiving.entity.ReceivingTicketAttachment;
import com.desitech.vyaparsathi.receiving.enums.ReceivingItemStatus;
import com.desitech.vyaparsathi.receiving.enums.ReceivingStatus;
import com.desitech.vyaparsathi.receiving.mapper.ReceivingMapper;
import com.desitech.vyaparsathi.receiving.repository.ReceivingRepository;
import com.desitech.vyaparsathi.receiving.repository.ReceivingTicketRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
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
    private final ReceivingMapper receivingMapper;

    @Value("${app.default.shop.id:1}")  // Configurable default shop ID
    private Long defaultShopId;

    // Constructor injection for better testability
    public ReceivingService(ReceivingRepository receivingRepository,
                            PurchaseOrderRepository purchaseOrderRepository,
                            PurchaseOrderItemRepository purchaseOrderItemRepository,
                            ShopRepository shopRepository,
                            StockMovementRepository stockMovementRepository,
                            ReceivingTicketRepository receivingTicketRepository,
                            ReceivingMapper receivingMapper) {
        this.receivingRepository = receivingRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.shopRepository = shopRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.receivingTicketRepository = receivingTicketRepository;
        this.receivingMapper = receivingMapper;
    }

    /**
     * Retrieves all receivings with pagination support.
     */
    public Page<ReceivingDto> getAllReceivings(Pageable pageable) {
        return receivingRepository.findAll(pageable)
                .map(receivingMapper::toDto);
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

                    // Update allowed fields only (no PO, no shop, no receivedAt)
                    existingReceiving.setNotes(receivingDto.getNotes());

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

                    Receiving savedReceiving = receivingRepository.save(existingReceiving);

                    adjustStockDeltas(savedReceiving, oldReceivedQtys, oldVariants, oldCosts);
                    updateReceivingStatus(savedReceiving);
                    updatePOStatus(savedReceiving.getPurchaseOrder());

                    logger.info("Updated receiving ID {} for PO ID {}", id, savedReceiving.getPurchaseOrder().getId());
                    return receivingMapper.toDto(savedReceiving);
                });
    }

    /**
     * Validates ReceivingDto for create/update, including non-negative quantities.
     */
    private void validateDtoForCreateOrUpdate(ReceivingDto dto, boolean isUpdate) {
        if (CollectionUtils.isEmpty(dto.getReceivingItems())) {
            throw new BusinessValidationException("Receiving items cannot be empty");
        }
        for (ReceivingItemDto itemDto : dto.getReceivingItems()) {
            if (itemDto.getReceivedQty() < 0 || itemDto.getDamagedQty() < 0 || itemDto.getRejectedQty() < 0 || itemDto.getPutawayQty() < 0) {
                throw new BusinessValidationException("Quantities cannot be negative");
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
            item.setReceivedQty(itemDto.getReceivedQty());
            item.setDamagedQty(itemDto.getDamagedQty());
            item.setDamageReason(itemDto.getDamageReason());
            item.setRejectedQty(itemDto.getRejectedQty());
            item.setRejectReason(itemDto.getRejectReason());
            item.setPutawayQty(itemDto.getPutawayQty());
            item.setPutAwayStatus(itemDto.getPutAwayStatus());
            item.setNotes(itemDto.getNotes());
            item.setStatus(determineReceivingItemStatus(item));

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
        boolean allReceived = receiving.getItems().stream()
                .allMatch(item -> item.getStatus() == ReceivingItemStatus.RECEIVED);
        boolean anyReceived = receiving.getItems().stream()
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
     * Gets default shop, configurable.
     */
    private Shop getDefaultShop() {
        if (defaultShopId != null) {
            return shopRepository.findById(defaultShopId)
                    .orElseThrow(() -> new ResourceNotFoundException("Default shop not found with ID: " + defaultShopId));
        }
        List<Shop> shops = shopRepository.findAll();
        if (shops.isEmpty()) {
            throw new IllegalStateException("No shops found in the system.");
        }
        return shops.get(0);
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
            int delta = item.getReceivedQty() - oldQty;
            if (delta != 0) {
                ItemVariant variant = item.getPurchaseOrderItem().getItemVariant();
                BigDecimal cost = item.getPurchaseOrderItem().getUnitCost();
                StockMovementType type = delta > 0 ? StockMovementType.ADD : StockMovementType.DEDUCT;
                createStockMovement(variant, cost, type, Math.abs(delta), newReceiving.getId().toString());
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
                    createStockMovement(variant, cost, StockMovementType.DEDUCT, oldQty, newReceiving.getId().toString());
                }
            }
        }
    }

    /**
     * Creates a stock movement.
     */
    private void createStockMovement(ItemVariant variant, BigDecimal cost, StockMovementType type, int quantity, String reference) {
        StockMovement stockMovement = new StockMovement();
        stockMovement.setItemVariant(variant);
        stockMovement.setMovementType(type);
        stockMovement.setQuantity(BigDecimal.valueOf(quantity));
        stockMovement.setCostPerUnit(cost);
        stockMovement.setReason("Purchase Order Receiving Adjustment");
        stockMovement.setReference(reference);
        stockMovementRepository.save(stockMovement);
    }

    /**
     * Updates PO status based on all receivings.
     */
    private void updatePOStatus(PurchaseOrder po) {
        if (CollectionUtils.isEmpty(po.getItems())) {
            logger.warn("PO {} has no items. Cannot update status.", po.getId());
            return;
        }

        // Optimized: Could batch sums if needed, but for now, per-item
        boolean allPoItemsReceived = po.getItems().stream()
                .allMatch(poItem -> {
                    ReceivingQtySummary qtySummary = receivingRepository.getQtySummaryForPOItem(poItem.getId(), TenantUtils.getCurrentShopId());
                    return poItem.getQuantity().intValue() <= (qtySummary.received() + qtySummary.damaged() + qtySummary.rejected());
                });

        if (allPoItemsReceived) {
            po.setStatus(PurchaseOrderStatus.RECEIVED);
        } else {
            po.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        }
        purchaseOrderRepository.save(po);
        logger.info("Updated PO {} status to {}", po.getPoNumber(), po.getStatus());
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
        receivingTicket.setStatus("Open"); // Use enum
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
                    ticket.setReason(dto.getReason());
                    ticket.setDescription(dto.getDescription());
                    ticket.setRaisedBy(dto.getRaisedBy());
                    // Status update logic if needed
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

    @Transactional
    public ReceivingDto createInitialReceivingRecord(CreateReceivingDto createReceivingDto) {
        logger.info("Attempting to create receiving record for PO ID: {}", createReceivingDto.getPurchaseOrderId());

        PurchaseOrder po = getPurchaseOrder(createReceivingDto.getPurchaseOrderId());

        if (!PurchaseOrderStatus.SUBMITTED.equals(po.getStatus())) {
            throw new BusinessValidationException("Cannot create a receiving record for a PO that is not in SUBMITTED");
        }

        if (receivingRepository.existsByPurchaseOrder(po)) {
            throw new BusinessValidationException("A receiving record already exists for this Purchase Order.");
        }

        Receiving receiving = createPendingReceiving(po, "Manually created receiving record.", null);
        if (createReceivingDto.getReceivedDate() != null) {
            receiving.setReceivedAt(createReceivingDto.getReceivedDate());
        }
        Receiving savedReceiving = receivingRepository.save(receiving);
        logger.info("Successfully created PENDING receiving record ID {} for PO ID {}", savedReceiving.getId(), po.getId());

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
        return receivingTicketRepository.findAll();
    }
}