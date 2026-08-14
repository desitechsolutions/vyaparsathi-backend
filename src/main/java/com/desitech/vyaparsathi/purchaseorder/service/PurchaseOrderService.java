package com.desitech.vyaparsathi.purchaseorder.service;

import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderDto;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import com.desitech.vyaparsathi.purchaseorder.enums.EventType;
import com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus;
import com.desitech.vyaparsathi.purchaseorder.events.dto.PurchaseOrderEventDto;
import com.desitech.vyaparsathi.purchaseorder.mapper.PurchaseOrderMapper;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderItemRepository;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import com.desitech.vyaparsathi.supplier.repository.SupplierRepository;
import com.desitech.vyaparsathi.purchaseorder.events.PurchaseOrderProducer;
import com.desitech.vyaparsathi.purchaseorder.events.PurchaseOrderEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with ID: " + dto.getSupplierId()));

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
                    .orElseThrow(() -> new ResourceNotFoundException("Item Variant not found with ID: " + itemDto.getItemVariantId()));
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
        purchaseOrderProducer.sendMessage(EventType.CREATED, eventDto);

        return mapper.toDto(savedPurchaseOrder);
    }

    public List<PurchaseOrderDto> findAllPurchaseOrders() {
        return purchaseOrderRepository.findAll().stream()
                .map(mapper::toDto)
                .toList();
    }

    public PurchaseOrderDto findPurchaseOrderById(Long id) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + id));
        return mapper.toDto(purchaseOrder);
    }

    @Transactional
    public PurchaseOrderDto updatePurchaseOrder(Long id, PurchaseOrderDto dto) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + id));

        if (!PurchaseOrderStatus.DRAFT.equals(purchaseOrder.getStatus())) {
            throw new IllegalStateException("Only 'Draft' orders can be updated.");
        }

        Supplier supplier = supplierRepository.findById(dto.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with ID: " + dto.getSupplierId()));

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
                    .orElseThrow(() -> new ResourceNotFoundException("Item Variant not found with ID: " + itemDto.getItemVariantId()));
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
        purchaseOrderProducer.sendMessage(EventType.UPDATED, eventDto);

        return mapper.toDto(saved);
    }

    @Transactional
    public PurchaseOrderDto submitPurchaseOrder(Long id) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + id));

        if (!PurchaseOrderStatus.DRAFT.equals(purchaseOrder.getStatus())) {
            throw new IllegalStateException("Only 'Draft' orders can be submitted.");
        }
        purchaseOrder.setStatus(PurchaseOrderStatus.SUBMITTED);

        PurchaseOrder savedPO = purchaseOrderRepository.save(purchaseOrder);
        PurchaseOrderEventDto eventDto = PurchaseOrderEventDto.fromEntity(savedPO);
        // Emit Kafka event after submit (eventType = "SUBMITTED")
        purchaseOrderProducer.sendMessage(EventType.SUBMITTED, eventDto);
        return mapper.toDto(savedPO);
    }

    /**
     * Hard-delete a PO. Only permitted while still in DRAFT — anything past
     * DRAFT is a real commitment (Kafka events fired, receiving workflow
     * possibly begun, supplier possibly notified) and must be cancelled
     * via {@link #cancelPurchaseOrder(Long, String, Long)} instead.
     */
    @Transactional
    public void deletePurchaseOrder(Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + id));
        if (!po.getStatus().isEditable()) {
            throw new IllegalStateException(
                    "Only draft POs can be deleted. Cancel this PO instead (current status: " + po.getStatus() + ").");
        }
        purchaseOrderRepository.deleteById(id);
        purchaseOrderProducer.sendMessage(EventType.DELETED, PurchaseOrderEventDto.fromEntity(po));
    }

    /**
     * "Open" POs = still expect activity. Replaces the ambiguous
     * {@code getPendingPurchaseOrders()} which mixed SUBMITTED with the
     * now-deprecated PENDING status.
     */
    public List<PurchaseOrderDto> findOpenOrders() {
        List<PurchaseOrderStatus> includedStatuses = Arrays.asList(
                PurchaseOrderStatus.SUBMITTED,
                PurchaseOrderStatus.PARTIALLY_RECEIVED
        );
        return purchaseOrderRepository.findAllByStatusIn(includedStatuses).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * @deprecated V81 rename. Use {@link #findOpenOrders()}. Kept as a
     * pass-through so the existing {@code GET /api/purchase-orders/pending}
     * endpoint doesn't break any FE caller mid-migration.
     */
    @Deprecated
    public List<PurchaseOrderDto> getPendingPurchaseOrders() {
        return findOpenOrders();
    }

    // ─── V81 lifecycle actions ────────────────────────────────────────

    /**
     * Cancel a PO. Legal from every non-terminal status except DRAFT
     * (drafts get deleted instead). Records who cancelled + why for audit,
     * emits a CANCELLED Kafka event so downstream listeners (receiving,
     * analytics) can react.
     */
    @Transactional
    public PurchaseOrderDto cancelPurchaseOrder(Long id, String reason, Long cancelledByUserId) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + id));
        if (po.getStatus() == PurchaseOrderStatus.DRAFT) {
            throw new IllegalStateException("Delete draft POs instead of cancelling.");
        }
        if (po.getStatus().isTerminal()) {
            throw new IllegalStateException("PO is already " + po.getStatus() + " and cannot be cancelled.");
        }
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        po.setCancelledAt(LocalDateTime.now());
        po.setCancelledBy(cancelledByUserId);
        po.setCancellationReason(reason);
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        purchaseOrderProducer.sendMessage(EventType.CANCELLED, PurchaseOrderEventDto.fromEntity(saved));
        return mapper.toDto(saved);
    }

    /**
     * Records that the PO has been fully received. Called by the receiving
     * flow once cumulative received qty ≥ ordered qty on every line. Safe
     * to call from any non-terminal status; already-RECEIVED calls are
     * treated as idempotent no-ops.
     */
    @Transactional
    public PurchaseOrderDto markAsReceived(Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + id));
        if (po.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled POs cannot be marked received.");
        }
        if (po.getStatus() == PurchaseOrderStatus.RECEIVED) {
            return mapper.toDto(po); // idempotent
        }
        po.setStatus(PurchaseOrderStatus.RECEIVED);
        po.setReceivedAt(LocalDateTime.now());
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        purchaseOrderProducer.sendMessage(EventType.RECEIVED, PurchaseOrderEventDto.fromEntity(saved));
        return mapper.toDto(saved);
    }

    /**
     * Records that the PO has been sent to the supplier. Phase 1 stamps
     * {@code sent_at} only; Phase 5 wires the actual email dispatch via
     * the existing {@code EmailService}. Safe to call from SUBMITTED or
     * later so a shop can re-send if the supplier lost the previous copy.
     */
    @Transactional
    public PurchaseOrderDto sendToSupplier(Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + id));
        if (po.getStatus() == PurchaseOrderStatus.DRAFT) {
            throw new IllegalStateException("Submit the PO before sending to a supplier.");
        }
        if (po.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot send a cancelled PO.");
        }
        po.setSentAt(LocalDateTime.now());
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        // No new EventType.SENT yet — additive change deferred until the
        // receiving listener signals it wants to observe sends. Reusing
        // UPDATED so downstream doesn't see an unknown type.
        purchaseOrderProducer.sendMessage(EventType.UPDATED, PurchaseOrderEventDto.fromEntity(saved));
        return mapper.toDto(saved);
    }

    // ─── Receiving ────────────────────────────────────────────────────────────

    /**
     * Transitions a PO from SUBMITTED to PARTIALLY_RECEIVED (or keeps current status).
     * Called by {@code POST /api/purchase-orders/{id}/receive} so the frontend can signal
     * that the receiving workflow has begun without creating a full Receiving record.
     */
    @Transactional
    public PurchaseOrderDto markAsReceiving(Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + id));

        if (PurchaseOrderStatus.SUBMITTED.equals(po.getStatus())) {
            po.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
            purchaseOrderRepository.save(po);
        }
        return mapper.toDto(po);
    }
}