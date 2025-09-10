package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.entity.StockMovement;
import com.desitech.vyaparsathi.inventory.repository.StockMovementRepository;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import com.desitech.vyaparsathi.purchaseorder.enums.EventType;
import com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus;
import com.desitech.vyaparsathi.purchaseorder.events.dto.PurchaseOrderEventDto;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderItemRepository;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import com.desitech.vyaparsathi.purchaseorder.events.PurchaseOrderEvent;
import com.desitech.vyaparsathi.receiving.dto.CreateReceivingDto;
import com.desitech.vyaparsathi.receiving.dto.ReceivingDto;
import com.desitech.vyaparsathi.receiving.dto.ReceivingTicketDTO;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.entity.ReceivingItem;
import com.desitech.vyaparsathi.receiving.entity.ReceivingTicket;
import com.desitech.vyaparsathi.receiving.enums.ReceivingStatus;
import com.desitech.vyaparsathi.receiving.enums.ReceivingItemStatus;
import com.desitech.vyaparsathi.receiving.mapper.ReceivingMapper;
import com.desitech.vyaparsathi.receiving.repository.ReceivingRepository;
import com.desitech.vyaparsathi.receiving.repository.ReceivingTicketRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReceivingService {
    private static final Logger logger = LoggerFactory.getLogger(ReceivingService.class);

    @Autowired private ReceivingRepository receivingRepository;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Autowired private ShopRepository shopRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private ReceivingTicketRepository receivingTicketRepository;

    @Autowired
    private ReceivingMapper receivingMapper;

    public List<ReceivingDto> getAllReceivings() {
        return receivingMapper.toDTOList(receivingRepository.findAll());
    }

    public Optional<Receiving> getReceivingById(Long id) {
        return receivingRepository.findById(id);
    }

    @Transactional
    public ReceivingDto createReceiving(ReceivingDto receivingDto) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(receivingDto.getPurchaseOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Purchase Order not found"));

        Shop shop = shopRepository.findById(receivingDto.getShopId())
                .orElseThrow(() -> new IllegalArgumentException("Shop not found"));

        Receiving receiving = new Receiving();
        receiving.setPurchaseOrder(purchaseOrder);
        receiving.setStatus(ReceivingStatus.PENDING); // Initial status
        receiving.setReceivedAt(LocalDateTime.now());
        receiving.setReceivedBy(receivingDto.getReceivedBy());
        receiving.setNotes(receivingDto.getNotes());
        receiving.setShop(shop);

        Receiving savedReceiving = receivingRepository.save(receiving);

        List<ReceivingItem> receivingItems = receivingDto.getReceivingItems().stream().map(itemDTO -> {
            PurchaseOrderItem poItem = purchaseOrderItemRepository.findById(itemDTO.getPurchaseOrderItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Purchase Order Item not found"));

            ReceivingItem receivingItem = new ReceivingItem();
            receivingItem.setReceiving(savedReceiving);
            receivingItem.setPurchaseOrderItem(poItem);
            receivingItem.setStatus(itemDTO.getStatus());
            receivingItem.setReceivedQty(itemDTO.getReceivedQty());
            receivingItem.setDamagedQty(itemDTO.getDamagedQty());
            receivingItem.setDamageReason(itemDTO.getDamageReason());
            receivingItem.setNotes(itemDTO.getNotes());
            receivingItem.setExpectedQty(itemDTO.getExpectedQty());

            return receivingItem;
        }).collect(Collectors.toList());

        savedReceiving.setItems(receivingItems);
        Receiving finalReceiving = receivingRepository.save(savedReceiving);

        // Only update stock and PO status if COMPLETED
        if (finalReceiving.getStatus() == ReceivingStatus.COMPLETED) {
            updateStockLevels(finalReceiving);
        }
        updatePOStatusBasedOnReceiving(finalReceiving);

        return receivingMapper.toDTO(receiving);
    }

    @Transactional
    public Optional<ReceivingDto> updateReceiving(Long id, ReceivingDto receivingDto) {
        return receivingRepository.findById(id)
                .map(existingReceiving -> {
                    PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(receivingDto.getPurchaseOrderId())
                            .orElseThrow(() -> new IllegalArgumentException("Purchase Order not found"));

                    Shop shop = shopRepository.findById(receivingDto.getShopId())
                            .orElseThrow(() -> new IllegalArgumentException("Shop not found"));

                    existingReceiving.setPurchaseOrder(purchaseOrder);
                    existingReceiving.setReceivedAt(LocalDateTime.now());
                    existingReceiving.setReceivedBy(receivingDto.getReceivedBy());
                    existingReceiving.setNotes(receivingDto.getNotes());
                    existingReceiving.setShop(shop);

                    boolean allReceived = receivingDto.getReceivingItems().stream()
                            .allMatch(item -> item.getStatus() == ReceivingItemStatus.RECEIVED);
                    existingReceiving.setStatus(allReceived ? ReceivingStatus.COMPLETED : ReceivingStatus.PARTIALLY_RECEIVED);

                    Map<Long, ReceivingItem> existingMap = existingReceiving.getItems().stream()
                            .filter(i -> i.getId() != null)
                            .collect(Collectors.toMap(ReceivingItem::getId, i -> i));

                    List<ReceivingItem> updatedItems = new java.util.ArrayList<>();
                    Set<Long> incomingIds = new java.util.HashSet<>();

                    for (var itemDTO : receivingDto.getReceivingItems()) {
                        ReceivingItem item;
                        if (itemDTO.getId() != null && existingMap.containsKey(itemDTO.getId())) {
                            item = existingMap.get(itemDTO.getId());
                            incomingIds.add(itemDTO.getId());
                        } else {
                            item = new ReceivingItem();
                            item.setReceiving(existingReceiving);
                        }
                        PurchaseOrderItem poItem = purchaseOrderItemRepository.findById(itemDTO.getPurchaseOrderItemId())
                                .orElseThrow(() -> new IllegalArgumentException("Purchase Order Item not found"));
                        item.setPurchaseOrderItem(poItem);
                        item.setStatus(itemDTO.getStatus());
                        item.setReceivedQty(itemDTO.getReceivedQty());
                        item.setDamagedQty(itemDTO.getDamagedQty());
                        item.setDamageReason(itemDTO.getDamageReason());
                        item.setNotes(itemDTO.getNotes());
                        item.setExpectedQty(itemDTO.getExpectedQty());
                        updatedItems.add(item);
                    }

                    List<ReceivingItem> toRemove = existingReceiving.getItems().stream()
                            .filter(i -> i.getId() != null && !incomingIds.contains(i.getId()))
                            .toList();
                    existingReceiving.getItems().removeAll(toRemove);

                    for (ReceivingItem item : updatedItems) {
                        if (!existingReceiving.getItems().contains(item)) {
                            existingReceiving.getItems().add(item);
                        }
                    }

                    Receiving updatedReceiving = receivingRepository.save(existingReceiving);

                    if (updatedReceiving.getStatus() == ReceivingStatus.COMPLETED) {
                        updateStockLevels(updatedReceiving);
                    }
                    updatePOStatusBasedOnReceiving(updatedReceiving);

                    return receivingMapper.toDTO(updatedReceiving);
                });
    }


    /**
     * CORRECTED: This method now only creates StockMovement records.
     * It no longer interacts with the obsolete StockEntry entity.
     */
    private void updateStockLevels(Receiving receiving) {
        for (ReceivingItem receivingItem : receiving.getItems()) {
            // Only update stock for items actually received
            if (receivingItem.getStatus() != ReceivingItemStatus.RECEIVED) continue;

            PurchaseOrderItem poItem = receivingItem.getPurchaseOrderItem();
            ItemVariant itemVariant = poItem.getItemVariant();
            BigDecimal receivedQty = BigDecimal.valueOf(Optional.ofNullable(receivingItem.getReceivedQty()).orElse(0));
            BigDecimal costPerUnit = poItem.getUnitCost(); // The cost for this specific batch

            if (receivedQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            // REMOVED: All logic related to finding, creating, or updating a StockEntry has been deleted.

            // Create a stock movement record for traceability. This is the single source of truth.
            StockMovement stockMovement = new StockMovement();
            stockMovement.setItemVariant(itemVariant);
            stockMovement.setMovementType("ADD");
            stockMovement.setQuantity(receivedQty);
            stockMovement.setCostPerUnit(costPerUnit); // Record the cost in the movement log
            stockMovement.setReason("Purchase Order Receiving");
            stockMovement.setReference(receiving.getId().toString());
            stockMovementRepository.save(stockMovement);
        }
    }

    // ... other methods (updatePOStatusBasedOnReceiving, deleteReceiving, etc.) are unchanged ...
    private void updatePOStatusBasedOnReceiving(Receiving receiving) {
        PurchaseOrder po = receiving.getPurchaseOrder();
        if (receiving.getItems() == null || receiving.getItems().isEmpty()) {
            logger.warn("updatePOStatusBasedOnReceiving called with no items for receiving ID: {}. No status change.", receiving.getId());
            return;
        }
        boolean allReceived = receiving.getItems().stream()
                .allMatch(item -> item.getStatus() == ReceivingItemStatus.RECEIVED);
        boolean anyReceived = receiving.getItems().stream()
                .anyMatch(item -> item.getStatus() == ReceivingItemStatus.RECEIVED);
        boolean anyStarted = receiving.getItems().stream()
                .anyMatch(item -> item.getStatus() != ReceivingItemStatus.PENDING);

        if (allReceived) {
            po.setStatus(PurchaseOrderStatus.RECEIVED);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        }
        purchaseOrderRepository.save(po);
        logger.info("Updated PO {} status to {}", po.getPoNumber(), po.getStatus());
    }

    public void deleteReceiving(Long id) {
        receivingRepository.deleteById(id);
    }

    @Transactional
    public ReceivingTicket createReceivingTicket(ReceivingTicketDTO receivingTicketDTO) {
        Receiving receiving = receivingRepository.findById(receivingTicketDTO.getReceivingId())
                .orElseThrow(() -> new IllegalArgumentException("Receiving not found"));

        ReceivingTicket receivingTicket = new ReceivingTicket();
        receivingTicket.setReceiving(receiving);
        receivingTicket.setReason(receivingTicketDTO.getReason());
        receivingTicket.setDescription(receivingTicketDTO.getDescription());
        receivingTicket.setStatus("Open"); // Initial status
        receivingTicket.setRaisedAt(LocalDateTime.now());
        receivingTicket.setRaisedBy(receivingTicketDTO.getRaisedBy());

        return receivingTicketRepository.save(receivingTicket);
    }

    public Optional<ReceivingTicket> getReceivingTicketById(Long id) {
        return receivingTicketRepository.findById(id);
    }

    @Transactional
    public ReceivingDto receiveGoods(CreateReceivingDto createReceivingDto) {
        logger.info("Attempting to create receiving record for PO ID: {}", createReceivingDto.getPurchaseOrderId());

        PurchaseOrder po = purchaseOrderRepository.findById(createReceivingDto.getPurchaseOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Purchase Order not found with ID: " + createReceivingDto.getPurchaseOrderId()));

        if (!PurchaseOrderStatus.SUBMITTED.equals(po.getStatus()) && !PurchaseOrderStatus.PARTIALLY_RECEIVED.equals(po.getStatus())) {
            throw new IllegalStateException("Cannot create receiving for a PO that is not in SUBMITTED or PARTIALLY_RECEIVED status.");
        }

        if (receivingRepository.existsByPurchaseOrder(po)) {
            throw new IllegalStateException("A receiving record already exists for this Purchase Order.");
        }

        List<Shop> shops = shopRepository.findAll();
        if (shops.isEmpty()) {
            throw new IllegalStateException("Cannot create receiving: No shops found in the system.");
        }
        Shop defaultShop = shops.get(0); // Or use a more sophisticated logic to find the right shop

        Receiving receiving = new Receiving();
        receiving.setPurchaseOrder(po);
        receiving.setStatus(ReceivingStatus.PENDING);
        receiving.setNotes("Manually created receiving record.");
        receiving.setShop(defaultShop);
        // receivedAt and receivedBy are left null until items are actually processed.

        List<ReceivingItem> receivingItems = po.getItems().stream().map(poItem -> {
            ReceivingItem item = new ReceivingItem();
            item.setReceiving(receiving);
            item.setPurchaseOrderItem(poItem);
            item.setStatus(ReceivingItemStatus.PENDING);
            item.setExpectedQty(poItem.getQuantity().intValue()); // Convert BigDecimal to int
            item.setReceivedQty(0);
            item.setDamagedQty(0);
            return item;
        }).collect(Collectors.toList());

        receiving.setItems(receivingItems);
        Receiving savedReceiving = receivingRepository.save(receiving);
        logger.info("Successfully created PENDING receiving record ID {} for PO ID {}", savedReceiving.getId(), po.getId());

        return receivingMapper.toDTO(savedReceiving);
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

        boolean exists = receivingRepository.existsByPurchaseOrder(po);
        if (exists){
            logger.info("Receiving record already exists for PO ID {}, skipping creation.", po.getId());
            return;
        }

        List<Shop> shops = shopRepository.findAll();
        if (shops.isEmpty()) {
            logger.error("Cannot create receiving for PO ID {}: No shops found in system.", po.getId());
            throw new IllegalStateException("No shops found in system.");
        }
        Shop shop = shops.get(0);

        Receiving receiving = new Receiving();
        receiving.setPurchaseOrder(po);
        receiving.setStatus(ReceivingStatus.PENDING);
        receiving.setReceivedAt(LocalDateTime.now());
        receiving.setReceivedBy("system-user");
        receiving.setNotes("Auto-created from PO event");
        receiving.setShop(shop);

        List<ReceivingItem> receivingItems = po.getItems().stream().map(poItem -> {
            ReceivingItem item = new ReceivingItem();
            item.setReceiving(receiving);
            item.setPurchaseOrderItem(poItem);
            item.setStatus(ReceivingItemStatus.PENDING);
            item.setExpectedQty(poItem.getQuantity());
            item.setReceivedQty(0);
            item.setDamagedQty(0);
            item.setNotes(null);
            return item;
        }).collect(Collectors.toList());

        receiving.setItems(receivingItems);
        receivingRepository.save(receiving);
    }

    public Optional<Receiving> getByPurchaseOrderId(Long poId) {
        return receivingRepository.findByPurchaseOrderId(poId);
    }
}