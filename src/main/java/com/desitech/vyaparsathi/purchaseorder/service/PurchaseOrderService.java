package com.desitech.vyaparsathi.purchaseorder.service;

import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.gst.service.GstJurisdictionService;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderDto;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderItemDto;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import com.desitech.vyaparsathi.purchaseorder.enums.EventType;
import com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus;
import com.desitech.vyaparsathi.purchaseorder.events.dto.PurchaseOrderEventDto;
import com.desitech.vyaparsathi.purchaseorder.mapper.PurchaseOrderMapper;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderItemRepository;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import com.desitech.vyaparsathi.supplier.repository.SupplierRepository;
import com.desitech.vyaparsathi.purchaseorder.events.PurchaseOrderProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    @Autowired
    private ShopRepository shopRepository;
    @Autowired
    private GstJurisdictionService gstJurisdictionService;
    @Autowired
    private PurchaseOrderNumberService purchaseOrderNumberService;

    /**
     * Create a new purchase order in DRAFT, persist it, and emit a Kafka event.
     * V83: recomputes line-level GST + header totals server-side from every
     * line's qty × unit_cost − discount, so the wire contract is
     * "declare intent, server does the math."
     */
    @Transactional
    public PurchaseOrderDto createPurchaseOrder(PurchaseOrderDto dto) {
        // Phase 2 (V84): if the caller doesn't supply a PO number, generate one
        // via the per-shop sequence. Blank strings are treated as "please
        // generate" — enterprise flows never require the shop to type an
        // identifier. Only reject as duplicate when the caller passed one
        // explicitly.
        String requestedPoNumber = dto.getPoNumber() != null ? dto.getPoNumber().trim() : "";
        if (!requestedPoNumber.isEmpty() && purchaseOrderRepository.existsByPoNumber(requestedPoNumber)) {
            throw new IllegalStateException("Purchase Order with number '" + requestedPoNumber + "' already exists.");
        }
        String poNumber = requestedPoNumber.isEmpty()
                ? purchaseOrderNumberService.nextPurchaseOrderNumber(
                        TenantUtils.getCurrentShopId(),
                        dto.getOrderDate() != null ? dto.getOrderDate().toLocalDate() : java.time.LocalDate.now())
                : requestedPoNumber;

        Supplier supplier = supplierRepository.findById(dto.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with ID: " + dto.getSupplierId()));

        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setPoNumber(poNumber);
        purchaseOrder.setSupplier(supplier);
        purchaseOrder.setOrderDate(dto.getOrderDate());
        purchaseOrder.setExpectedDeliveryDate(dto.getExpectedDeliveryDate());
        purchaseOrder.setStatus(PurchaseOrderStatus.DRAFT); // always DRAFT on create
        purchaseOrder.setNotes(dto.getNotes());
        purchaseOrder.setFreightCharges(nz(dto.getFreightCharges()));

        // Persist the header first so line items can carry the FK.
        PurchaseOrder savedPurchaseOrder = purchaseOrderRepository.save(purchaseOrder);

        List<PurchaseOrderItem> items = dto.getItems().stream().map(itemDto -> {
            ItemVariant itemVariant = itemVariantRepository.findById(itemDto.getItemVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item Variant not found with ID: " + itemDto.getItemVariantId()));
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(savedPurchaseOrder);
            populateLineFromDto(item, itemDto, itemVariant);
            return item;
        }).collect(Collectors.toList());

        // V83: compute per-line GST split (intra vs inter based on shop/supplier
        // state), then reconcile header totals.
        recomputeTotals(savedPurchaseOrder, items, supplier);

        purchaseOrderItemRepository.saveAll(items);
        savedPurchaseOrder.setItems(items);
        purchaseOrderRepository.save(savedPurchaseOrder);

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
        purchaseOrder.setFreightCharges(nz(dto.getFreightCharges()));
        // Do not allow changing status here, always keep as DRAFT until submit
        purchaseOrder.setStatus(PurchaseOrderStatus.DRAFT);

        purchaseOrderItemRepository.deleteAll(purchaseOrder.getItems());
        purchaseOrder.getItems().clear();

        List<PurchaseOrderItem> newItems = dto.getItems().stream().map(itemDto -> {
            ItemVariant itemVariant = itemVariantRepository.findById(itemDto.getItemVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item Variant not found with ID: " + itemDto.getItemVariantId()));
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(purchaseOrder);
            populateLineFromDto(item, itemDto, itemVariant);
            return item;
        }).collect(Collectors.toList());

        purchaseOrder.getItems().addAll(newItems);

        recomputeTotals(purchaseOrder, newItems, supplier);

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

    /**
     * Duplicate a PO into a fresh DRAFT. Copies supplier + line items (with
     * their V83 GST / discount / HSN fields) + notes + freight; resets status,
     * lifecycle stamps, received quantity, and generates a new PO number.
     * Order date becomes today; expected delivery is cleared so the shop
     * picks a new one on save. Lifecycle stamps + cancellation metadata never
     * carry over — a copy is a fresh document, not a resurrection.
     */
    @Transactional
    public PurchaseOrderDto duplicatePurchaseOrder(Long id) {
        PurchaseOrder src = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + id));

        String newPoNumber = purchaseOrderNumberService.nextPurchaseOrderNumber(
                TenantUtils.getCurrentShopId(), java.time.LocalDate.now());

        PurchaseOrder copy = new PurchaseOrder();
        copy.setPoNumber(newPoNumber);
        copy.setSupplier(src.getSupplier());
        copy.setOrderDate(LocalDateTime.now());
        copy.setExpectedDeliveryDate(null);
        copy.setStatus(PurchaseOrderStatus.DRAFT);
        copy.setNotes(src.getNotes());
        copy.setFreightCharges(nz(src.getFreightCharges()));

        PurchaseOrder saved = purchaseOrderRepository.save(copy);

        List<PurchaseOrderItem> items = (src.getItems() != null ? src.getItems() : java.util.Collections.<PurchaseOrderItem>emptyList())
                .stream().map(srcLine -> {
                    PurchaseOrderItem line = new PurchaseOrderItem();
                    line.setPurchaseOrder(saved);
                    line.setItemVariant(srcLine.getItemVariant());
                    line.setQuantity(srcLine.getQuantity());
                    line.setUnitCost(srcLine.getUnitCost());
                    // receivedQuantity always starts at zero on a fresh copy.
                    line.setDiscount(nz(srcLine.getDiscount()));
                    line.setDiscountPct(srcLine.getDiscountPct());
                    line.setGstRate(srcLine.getGstRate());
                    line.setHsnCode(srcLine.getHsnCode());
                    return line;
                }).collect(Collectors.toList());

        recomputeTotals(saved, items, saved.getSupplier());
        purchaseOrderItemRepository.saveAll(items);
        saved.setItems(items);
        purchaseOrderRepository.save(saved);

        purchaseOrderProducer.sendMessage(EventType.CREATED, PurchaseOrderEventDto.fromEntity(saved));
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

    // ─── V83 recompute helpers ────────────────────────────────────────

    /**
     * Copies the "declared" line inputs from the DTO onto the entity —
     * qty, unit cost, discount, HSN, gst rate. HSN and gst_rate fall back
     * to the parent variant when the caller doesn't supply them. Tax
     * amounts and taxable_value are NOT set here; those are computed by
     * {@link #recomputeTotals} after all lines are known so the same
     * jurisdiction lookup can be applied to every line uniformly.
     */
    private void populateLineFromDto(PurchaseOrderItem item, PurchaseOrderItemDto dto, ItemVariant variant) {
        item.setItemVariant(variant);
        item.setQuantity(dto.getQuantity());
        item.setUnitCost(dto.getUnitCost());
        item.setDiscount(nz(dto.getDiscount()));
        item.setDiscountPct(dto.getDiscountPct());
        // gst_rate + hsn fall back to the parent variant when the caller omits them.
        // A zero-rate line is legal (composition scheme etc.); the caller signals
        // that with an explicit 0 rather than by omission.
        item.setGstRate(dto.getGstRate() != null ? dto.getGstRate() : variant.getGstRate());
        String hsn = dto.getHsnCode() != null && !dto.getHsnCode().isBlank()
                ? dto.getHsnCode()
                : variant.getHsn();
        item.setHsnCode(hsn);
    }

    /**
     * V83: recompute every line's taxable_value / GST split / line_total, then
     * reconcile the header (subtotal / total_discount / total_tax / total_amount /
     * round_off) so both sides balance. Mirrors SaleService.
     *
     * <p>Jurisdiction: intra-state when the shop and supplier resolve to the
     * same 2-digit state code; otherwise inter-state (IGST). Missing codes
     * default to intra-state per {@link GstJurisdictionService#isIntraState}.
     *
     * <p>Rounding: line-level GST is HALF_UP to two decimals; the grand total
     * is HALF_UP to the whole rupee (BigDecimal scale 0), and the delta lands
     * in {@code roundOff} so the persisted numbers reconcile exactly.
     */
    private void recomputeTotals(PurchaseOrder po, List<PurchaseOrderItem> lines, Supplier supplier) {
        Long shopId = TenantUtils.getCurrentShopId();
        Shop shop = shopId != null ? shopRepository.findById(shopId).orElse(null) : null;
        String shopCode = gstJurisdictionService.resolveStateCode(shop).orElse(null);
        String supplierCode = gstJurisdictionService.resolveStateCode(supplier).orElse(null);
        boolean intraState = gstJurisdictionService.isIntraState(shopCode, supplierCode);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;

        for (PurchaseOrderItem line : lines) {
            BigDecimal qty = line.getQuantity() != null ? BigDecimal.valueOf(line.getQuantity()) : BigDecimal.ZERO;
            BigDecimal unitCost = nz(line.getUnitCost());
            BigDecimal discount = nz(line.getDiscount());
            BigDecimal taxable = qty.multiply(unitCost).subtract(discount).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            line.setTaxableValue(taxable);

            Integer gstRate = line.getGstRate();
            BigDecimal gstAmount = BigDecimal.ZERO;
            if (gstRate != null && gstRate > 0) {
                gstAmount = taxable.multiply(BigDecimal.valueOf(gstRate))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }

            if (gstAmount.signum() > 0) {
                if (intraState) {
                    BigDecimal half = gstAmount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                    line.setCgstAmt(half);
                    line.setSgstAmt(half);
                    line.setIgstAmt(BigDecimal.ZERO);
                } else {
                    line.setCgstAmt(BigDecimal.ZERO);
                    line.setSgstAmt(BigDecimal.ZERO);
                    line.setIgstAmt(gstAmount);
                }
            } else {
                line.setCgstAmt(BigDecimal.ZERO);
                line.setSgstAmt(BigDecimal.ZERO);
                line.setIgstAmt(BigDecimal.ZERO);
            }

            BigDecimal lineGst = line.getCgstAmt().add(line.getSgstAmt()).add(line.getIgstAmt());
            line.setLineTotal(taxable.add(lineGst));

            subtotal = subtotal.add(taxable);
            totalDiscount = totalDiscount.add(discount);
            totalTax = totalTax.add(lineGst);
        }

        BigDecimal freight = nz(po.getFreightCharges());
        BigDecimal grand = subtotal.add(totalTax).add(freight);
        BigDecimal rounded = grand.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundOff = rounded.subtract(grand).setScale(2, RoundingMode.HALF_UP);

        po.setSubtotal(subtotal);
        po.setTotalDiscount(totalDiscount);
        po.setTotalTax(totalTax);
        po.setRoundOff(roundOff);
        po.setTotalAmount(rounded);
    }

    /** null-safe BigDecimal: null → ZERO. */
    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}