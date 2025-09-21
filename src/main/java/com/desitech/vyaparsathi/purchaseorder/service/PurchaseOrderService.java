package com.desitech.vyaparsathi.purchaseorder.service;

import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderDto;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import com.desitech.vyaparsathi.inventory.entity.Supplier;
import com.desitech.vyaparsathi.purchaseorder.enums.EventType;
import com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus;
import com.desitech.vyaparsathi.purchaseorder.events.dto.PurchaseOrderEventDto;
import com.desitech.vyaparsathi.purchaseorder.mapper.PurchaseOrderMapper;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderItemRepository;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import com.desitech.vyaparsathi.inventory.repository.SupplierRepository;
import com.desitech.vyaparsathi.purchaseorder.kafka.PurchaseOrderProducer;
import com.desitech.vyaparsathi.purchaseorder.events.PurchaseOrderEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PurchaseOrderService {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderService.class);

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired
    private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Autowired
    private SupplierRepository supplierRepository;
    @Autowired
    private ItemVariantRepository itemVariantRepository;
    @Autowired
    private PurchaseOrderMapper mapper;
    @Autowired
    private PurchaseOrderProducer purchaseOrderProducer;

    /**
     * Create a new purchase order in DRAFT, persist it, and emit a Kafka event.
     */
    @Transactional
    public PurchaseOrderDto createPurchaseOrder(PurchaseOrderDto dto) {
        if (purchaseOrderRepository.existsByPoNumber(dto.getPoNumber())) {
            throw new IllegalStateException("Purchase Order with number '" + dto.getPoNumber() + "' already exists.");
        }

        Supplier supplier = supplierRepository.findById(dto.getSupplierId())
                .orElseThrow(() -> new RuntimeException("Supplier not found with ID: " + dto.getSupplierId()));

        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setPoNumber(dto.getPoNumber());
        purchaseOrder.setSupplier(supplier);
        purchaseOrder.setOrderDate(dto.getOrderDate());
        purchaseOrder.setExpectedDeliveryDate(dto.getExpectedDeliveryDate());
        purchaseOrder.setStatus(PurchaseOrderStatus.DRAFT); // always DRAFT on create
        purchaseOrder.setNotes(dto.getNotes());

        BigDecimal totalAmount = dto.getItems().stream()
                .map(item -> item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        purchaseOrder.setTotalAmount(totalAmount);

        PurchaseOrder savedPurchaseOrder = purchaseOrderRepository.save(purchaseOrder);

        List<PurchaseOrderItem> items = dto.getItems().stream().map(itemDto -> {
            ItemVariant itemVariant = itemVariantRepository.findById(itemDto.getItemVariantId())
                    .orElseThrow(() -> new RuntimeException("Item Variant not found with ID: " + itemDto.getItemVariantId()));
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(savedPurchaseOrder);
            item.setItemVariant(itemVariant);
            item.setQuantity(itemDto.getQuantity());
            item.setUnitCost(itemDto.getUnitCost());
            return item;
        }).collect(Collectors.toList());

        purchaseOrderItemRepository.saveAll(items);
        savedPurchaseOrder.setItems(items);

        // Emit Kafka event after save (eventType = "CREATED")
        PurchaseOrderEventDto eventDto = PurchaseOrderEventDto.fromEntity(savedPurchaseOrder);
        purchaseOrderProducer.sendMessage(new PurchaseOrderEvent(EventType.CREATED, eventDto));

        return mapper.toDto(savedPurchaseOrder);
    }

    public List<PurchaseOrderDto> findAllPurchaseOrders() {
        List<PurchaseOrderDto> orderDtos = purchaseOrderRepository.findAll().stream()
                .map(mapper::toDto)
                .toList();
        System.out.println(orderDtos);
        return orderDtos;
    }

    public PurchaseOrderDto findPurchaseOrderById(Long id) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Purchase Order not found with ID: " + id));
        return mapper.toDto(purchaseOrder);
    }

    @Transactional
    public PurchaseOrderDto updatePurchaseOrder(Long id, PurchaseOrderDto dto) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Purchase Order not found with ID: " + id));

        if (!PurchaseOrderStatus.DRAFT.equals(purchaseOrder.getStatus())) {
            throw new IllegalStateException("Only 'Draft' orders can be updated.");
        }

        Supplier supplier = supplierRepository.findById(dto.getSupplierId())
                .orElseThrow(() -> new RuntimeException("Supplier not found with ID: " + dto.getSupplierId()));

        purchaseOrder.setSupplier(supplier);
        purchaseOrder.setOrderDate(dto.getOrderDate());
        purchaseOrder.setExpectedDeliveryDate(dto.getExpectedDeliveryDate());
        purchaseOrder.setNotes(dto.getNotes());
        // Do not allow changing status here, always keep as DRAFT until submit
        purchaseOrder.setStatus(PurchaseOrderStatus.DRAFT);

        purchaseOrderItemRepository.deleteAll(purchaseOrder.getItems());
        purchaseOrder.getItems().clear();

        List<PurchaseOrderItem> newItems = dto.getItems().stream().map(itemDto -> {
            ItemVariant itemVariant = itemVariantRepository.findById(itemDto.getItemVariantId())
                    .orElseThrow(() -> new RuntimeException("Item Variant not found with ID: " + itemDto.getItemVariantId()));
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(purchaseOrder);
            item.setItemVariant(itemVariant);
            item.setQuantity(itemDto.getQuantity());
            item.setUnitCost(itemDto.getUnitCost());
            return item;
        }).toList();

        purchaseOrder.getItems().addAll(newItems);

        BigDecimal totalAmount = newItems.stream()
                .map(item -> item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        purchaseOrder.setTotalAmount(totalAmount);

        PurchaseOrder saved = purchaseOrderRepository.save(purchaseOrder);

        // Emit Kafka event after update (eventType = "UPDATED")
        PurchaseOrderEventDto eventDto = PurchaseOrderEventDto.fromEntity(saved);
        purchaseOrderProducer.sendMessage(new PurchaseOrderEvent(EventType.UPDATED, eventDto));

        return mapper.toDto(saved);
    }

    @Transactional
    public PurchaseOrderDto submitPurchaseOrder(Long id) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Purchase Order not found with ID: " + id));

        if (!PurchaseOrderStatus.DRAFT.equals(purchaseOrder.getStatus())) {
            throw new IllegalStateException("Only 'Draft' orders can be submitted.");
        }
        purchaseOrder.setStatus(PurchaseOrderStatus.SUBMITTED);

        PurchaseOrder savedPO = purchaseOrderRepository.save(purchaseOrder);
        PurchaseOrderEventDto eventDto = PurchaseOrderEventDto.fromEntity(savedPO);
        // Emit Kafka event after submit (eventType = "SUBMITTED")
        purchaseOrderProducer.sendMessage(new PurchaseOrderEvent(EventType.SUBMITTED, eventDto));
        return mapper.toDto(savedPO);
    }

    @Transactional
    public void deletePurchaseOrder(Long id) {
        if (!purchaseOrderRepository.existsById(id)) {
            throw new RuntimeException("Purchase Order not found with ID: " + id);
        }
        PurchaseOrder po = purchaseOrderRepository.findById(id).orElseThrow();
        purchaseOrderRepository.deleteById(id);

        // Emit Kafka event after delete (eventType = "DELETED")
        PurchaseOrderEventDto eventDto = PurchaseOrderEventDto.fromEntity(po);
        purchaseOrderProducer.sendMessage(new PurchaseOrderEvent(EventType.DELETED, eventDto));
    }

    public List<PurchaseOrderDto> getPendingPurchaseOrders() {
        List<PurchaseOrderStatus> includedStatuses = Arrays.asList(
                PurchaseOrderStatus.SUBMITTED,
                PurchaseOrderStatus.PENDING
        );
        List<PurchaseOrder> pos = purchaseOrderRepository.findAllByStatusIn(includedStatuses);
        return pos.stream().map(mapper::toDto).collect(Collectors.toList());
    }
}