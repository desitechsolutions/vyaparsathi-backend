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
    // Phase 5 additions — email dispatch + PDF renderer for /send.
    // @Lazy on both so a missing SMTP config only fails at send-time, not
    // at PurchaseOrderService construction (keeps tests using this bean
    // without needing a full email stack).
    @org.springframework.context.annotation.Lazy
    @Autowired(required = false)
    private com.desitech.vyaparsathi.notification.service.EmailService emailService;
    @org.springframework.context.annotation.Lazy
    @Autowired(required = false)
    private PurchaseOrderPdfService purchaseOrderPdfService;
    // Best-effort user resolver — lookup failures are non-fatal (name fields
    // stay null and the FE falls back to "user #ID").
    @Autowired(required = false)
    private com.desitech.vyaparsathi.auth.repository.UserRepository userRepository;

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
        // V92 landed cost — distribute freight into per-line unit cost when
        // the header flag is on. No-op when the flag is off or freight is 0.
        applyLandedCost(savedPurchaseOrder);

        purchaseOrderItemRepository.saveAll(items);
        savedPurchaseOrder.setItems(items);
        purchaseOrderRepository.save(savedPurchaseOrder);

        recordPoStatusChange(savedPurchaseOrder, null, savedPurchaseOrder.getStatus(),
                savedPurchaseOrder.getSubmittedBy(), "Created");

        // Emit Kafka event after save (eventType = "CREATED")
        PurchaseOrderEventDto eventDto = PurchaseOrderEventDto.fromEntity(savedPurchaseOrder);
        purchaseOrderProducer.sendMessage(EventType.CREATED, eventDto);

        return mapper.toDto(savedPurchaseOrder);
    }

    public List<PurchaseOrderDto> findAllPurchaseOrders() {
        return purchaseOrderRepository.findAll().stream()
                .map(mapper::toDto)
                .map(this::enrichUserNames)
                .toList();
    }

    public PurchaseOrderDto findPurchaseOrderById(Long id) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + id));
        return enrichUserNames(mapper.toDto(purchaseOrder));
    }

    /**
     * Best-effort user-name enrichment for the approval / rejection banners on
     * the FE. Never throws — a missing user, absent repository (test contexts),
     * or null id all leave the corresponding *ByName field null and the FE
     * falls back gracefully. Uses first-name + last-name when both present;
     * username as fallback so the banner never renders "user #17".
     */
    private PurchaseOrderDto enrichUserNames(PurchaseOrderDto dto) {
        if (dto == null || userRepository == null) return dto;
        if (dto.getApprovedBy() != null) dto.setApprovedByName(lookupUserName(dto.getApprovedBy()));
        if (dto.getRejectedBy() != null) dto.setRejectedByName(lookupUserName(dto.getRejectedBy()));
        if (dto.getSubmittedBy() != null) dto.setSubmittedByName(lookupUserName(dto.getSubmittedBy()));
        return dto;
    }

    private String lookupUserName(Long userId) {
        try {
            return userRepository.findById(userId).map(u -> {
                String full = (u.getFirstName() != null ? u.getFirstName() : "")
                        + (u.getLastName() != null ? " " + u.getLastName() : "");
                full = full.trim();
                if (!full.isEmpty()) return full;
                return u.getUsername() != null ? u.getUsername() : ("User #" + userId);
            }).orElse(null);
        } catch (Exception e) {
            logger.debug("User lookup failed for id {}: {}", userId, e.getMessage());
            return null;
        }
    }

    @Transactional
    public PurchaseOrderDto updatePurchaseOrder(Long id, PurchaseOrderDto dto) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + id));

        // Route through isEditable() rather than a direct DRAFT check so future
        // states that also permit in-place edits only need to flip that helper.
        // REJECTED specifically stays uneditable — the requester must click
        // /revise to transition into DRAFT, keeping the approver's comment in
        // sight during the transition.
        if (!purchaseOrder.getStatus().isEditable()) {
            throw new IllegalStateException(
                    "Only editable statuses can be updated. Current status: "
                            + purchaseOrder.getStatus() + ". For REJECTED, call /revise first.");
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
        applyLandedCost(purchaseOrder);

        PurchaseOrder saved = purchaseOrderRepository.save(purchaseOrder);

        // Emit Kafka event after update (eventType = "UPDATED")
        PurchaseOrderEventDto eventDto = PurchaseOrderEventDto.fromEntity(saved);
        purchaseOrderProducer.sendMessage(EventType.UPDATED, eventDto);

        return mapper.toDto(saved);
    }

    /**
     * @deprecated Prefer {@link #submitPurchaseOrder(Long, Long)}. Kept as a
     * null-user pass-through for legacy tests and clients that don't yet pass
     * the authenticated user id.
     */
    @Deprecated
    @Transactional
    public PurchaseOrderDto submitPurchaseOrder(Long id) {
        return submitPurchaseOrder(id, null);
    }

    /**
     * Submit a DRAFT. If the shop's approval policy is on and this PO's total
     * meets or exceeds the threshold, routes into PENDING_APPROVAL (V85);
     * otherwise transitions straight to SUBMITTED as before. Stamps
     * {@code submittedBy} in either case for the audit timeline.
     */
    @Transactional
    public PurchaseOrderDto submitPurchaseOrder(Long id, Long submittedByUserId) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + id));

        if (!PurchaseOrderStatus.DRAFT.equals(purchaseOrder.getStatus())) {
            throw new IllegalStateException("Only 'Draft' orders can be submitted.");
        }

        purchaseOrder.setSubmittedBy(submittedByUserId);

        // V85: shop policy check. Threshold-inclusive so a 0-threshold means
        // "every PO needs approval regardless of amount". Missing shop or
        // total (edge case, shouldn't happen post-recompute) falls through
        // to straight-submit.
        Shop shop = purchaseOrder.getShop();
        boolean requiresApproval = shop != null
                && Boolean.TRUE.equals(shop.getPoApprovalRequired())
                && purchaseOrder.getTotalAmount() != null
                && purchaseOrder.getTotalAmount().compareTo(shop.getPoApprovalThresholdAmount()) >= 0;

        // If this DRAFT is a revision of a previously-rejected PO, clear the
        // rejection metadata so the fresh approval cycle isn't cluttered by
        // the last iteration's banner. rejectionReason is intentionally
        // preserved *during* the DRAFT edit (surfaced as a reference banner);
        // it's cleared here at the moment of resubmission.
        if (purchaseOrder.getRejectionReason() != null || purchaseOrder.getRejectedAt() != null) {
            purchaseOrder.setRejectedBy(null);
            purchaseOrder.setRejectedAt(null);
            purchaseOrder.setRejectionReason(null);
        }

        PurchaseOrderStatus fromStatus = purchaseOrder.getStatus();
        if (requiresApproval) {
            purchaseOrder.setStatus(PurchaseOrderStatus.PENDING_APPROVAL);
            PurchaseOrder savedPO = purchaseOrderRepository.save(purchaseOrder);
            purchaseOrderProducer.sendMessage(EventType.APPROVAL_REQUESTED, PurchaseOrderEventDto.fromEntity(savedPO));
            recordPoStatusChange(savedPO, fromStatus, PurchaseOrderStatus.PENDING_APPROVAL, submittedByUserId, "Submitted for approval");
            logger.info("PO {} routed to PENDING_APPROVAL (total {} ≥ threshold {})",
                    savedPO.getPoNumber(), savedPO.getTotalAmount(), shop.getPoApprovalThresholdAmount());
            return mapper.toDto(savedPO);
        }

        purchaseOrder.setStatus(PurchaseOrderStatus.SUBMITTED);
        PurchaseOrder savedPO = purchaseOrderRepository.save(purchaseOrder);
        purchaseOrderProducer.sendMessage(EventType.SUBMITTED, PurchaseOrderEventDto.fromEntity(savedPO));
        recordPoStatusChange(savedPO, fromStatus, PurchaseOrderStatus.SUBMITTED, submittedByUserId, "Submitted");
        return mapper.toDto(savedPO);
    }

    /**
     * Approve a PENDING_APPROVAL PO. Transitions to SUBMITTED, stamps
     * {@code approvedBy}/{@code approvedAt}, and fires APPROVED then SUBMITTED
     * events so downstream receiving workflow triggers exactly as it would on
     * a direct submit (the receiving listener only reacts to SUBMITTED).
     */
    @Transactional
    public PurchaseOrderDto approvePurchaseOrder(Long id, Long approverUserId) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + id));
        if (po.getStatus() != PurchaseOrderStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only POs in PENDING_APPROVAL can be approved (current: " + po.getStatus() + ").");
        }
        PurchaseOrderStatus fromStatus = po.getStatus();
        po.setStatus(PurchaseOrderStatus.SUBMITTED);
        po.setApprovedBy(approverUserId);
        po.setApprovedAt(LocalDateTime.now());
        // Clear stale rejection metadata from any prior cycle — the PO has
        // been re-approved and the rejection banner should no longer render.
        po.setRejectedBy(null);
        po.setRejectedAt(null);
        po.setRejectionReason(null);
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        purchaseOrderProducer.sendMessage(EventType.APPROVED, PurchaseOrderEventDto.fromEntity(saved));
        recordPoStatusChange(saved, fromStatus, PurchaseOrderStatus.SUBMITTED, approverUserId, "Approved");
        // Fire SUBMITTED too — receiving listener + analytics both listen for
        // it, and approval is meant to be indistinguishable from a direct
        // submit downstream.
        purchaseOrderProducer.sendMessage(EventType.SUBMITTED, PurchaseOrderEventDto.fromEntity(saved));
        return enrichUserNames(mapper.toDto(saved));
    }

    /**
     * Reject a PENDING_APPROVAL PO. Transitions to REJECTED (not DRAFT) so the
     * approver's comment stays visible on the document as a banner until the
     * requester explicitly clicks Revise. Reason is @NotBlank at the controller.
     * Fires REJECTED for downstream audit listeners.
     *
     * <p>Refactored (2026-08-14) from the earlier "reject → DRAFT" flow — the
     * previous version threw away the read-only context an approver expects
     * for their rejection to stick.
     */
    @Transactional
    public PurchaseOrderDto rejectPurchaseOrder(Long id, String reason, Long rejecterUserId) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + id));
        if (po.getStatus() != PurchaseOrderStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only POs in PENDING_APPROVAL can be rejected (current: " + po.getStatus() + ").");
        }
        PurchaseOrderStatus fromStatus = po.getStatus();
        po.setStatus(PurchaseOrderStatus.REJECTED);
        po.setRejectedBy(rejecterUserId);
        po.setRejectedAt(LocalDateTime.now());
        po.setRejectionReason(reason);
        // Clear stale approval stamps so a re-approval later doesn't carry over.
        po.setApprovedBy(null);
        po.setApprovedAt(null);
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        purchaseOrderProducer.sendMessage(EventType.REJECTED, PurchaseOrderEventDto.fromEntity(saved));
        recordPoStatusChange(saved, fromStatus, PurchaseOrderStatus.REJECTED, rejecterUserId, reason);
        return enrichUserNames(mapper.toDto(saved));
    }

    /**
     * Move a REJECTED PO into DRAFT so the requester can edit + resubmit.
     * Preserves {@code rejectedBy}/{@code rejectedAt}/{@code rejectionReason}
     * on the row — the FE keeps them visible as a "banner" during revision so
     * the requester sees the approver's ask while they edit. When they submit
     * again, the normal threshold gate re-routes to PENDING_APPROVAL if the
     * revised amount still trips the shop's approval policy.
     */
    @Transactional
    public PurchaseOrderDto reviseRejectedPurchaseOrder(Long id, Long userId) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + id));
        if (po.getStatus() != PurchaseOrderStatus.REJECTED) {
            throw new IllegalStateException("Only REJECTED POs can be revised (current: " + po.getStatus() + ").");
        }
        PurchaseOrderStatus fromStatus = po.getStatus();
        po.setStatus(PurchaseOrderStatus.DRAFT);
        // Deliberately DO NOT clear rejectionReason — the FE surfaces it as
        // a reference banner during edit so the requester addresses the ask.
        // It's cleared on the next successful approval flow so a subsequent
        // resubmission history reads clean.
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        // Fire the dedicated REVISED event, not the generic UPDATED, so audit
        // and notification subscribers can tell a revision-after-rejection
        // apart from an ordinary edit.
        purchaseOrderProducer.sendMessage(EventType.REVISED, PurchaseOrderEventDto.fromEntity(saved));
        recordPoStatusChange(saved, fromStatus, PurchaseOrderStatus.DRAFT, userId, "Revised (was rejected)");
        logger.info("PO {} moved from REJECTED to DRAFT for revision by user {}", saved.getPoNumber(), userId);
        return enrichUserNames(mapper.toDto(saved));
    }

    /**
     * List POs awaiting approval — powers the /purchase-orders/approvals FE queue.
     */
    public List<PurchaseOrderDto> findPendingApprovalOrders() {
        return purchaseOrderRepository.findAllByStatusIn(
                        java.util.Collections.singletonList(PurchaseOrderStatus.PENDING_APPROVAL))
                .stream()
                .map(mapper::toDto)
                .map(this::enrichUserNames)
                .collect(Collectors.toList());
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
        PurchaseOrderStatus fromStatus = po.getStatus();
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        po.setCancelledAt(LocalDateTime.now());
        po.setCancelledBy(cancelledByUserId);
        po.setCancellationReason(reason);
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        purchaseOrderProducer.sendMessage(EventType.CANCELLED, PurchaseOrderEventDto.fromEntity(saved));
        recordPoStatusChange(saved, fromStatus, PurchaseOrderStatus.CANCELLED, cancelledByUserId, reason);
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
        PurchaseOrderStatus fromStatus = po.getStatus();
        po.setStatus(PurchaseOrderStatus.RECEIVED);
        po.setReceivedAt(LocalDateTime.now());
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        purchaseOrderProducer.sendMessage(EventType.RECEIVED, PurchaseOrderEventDto.fromEntity(saved));
        recordPoStatusChange(saved, fromStatus, PurchaseOrderStatus.RECEIVED, null, "Fully received");
        return mapper.toDto(saved);
    }

    /** @deprecated Prefer {@link #sendToSupplier(Long, String, String, String, boolean)}. */
    @Deprecated
    @Transactional
    public PurchaseOrderDto sendToSupplier(Long id) {
        return sendToSupplier(id, null, null, null, true);
    }

    /**
     * Send a PO to the supplier. Phase 5 wires real email dispatch: renders
     * the PDF via {@link PurchaseOrderPdfService}, ships an HTML body via
     * {@link com.desitech.vyaparsathi.notification.service.EmailService}, and
     * stamps {@code sentAt} on success. When SMTP or the recipient is missing
     * we still stamp {@code sentAt} (the shop may have sent the PO out-of-band
     * via WhatsApp / print) and log — the endpoint is idempotent-friendly so
     * re-sends work.
     *
     * <p>Params (all optional; server falls back sensibly):
     * <ul>
     *   <li>{@code toOverride} — recipient email; defaults to {@code supplier.email}</li>
     *   <li>{@code subjectOverride} — email subject; defaults to a generated one</li>
     *   <li>{@code bodyOverride} — HTML body; defaults to a generated one</li>
     *   <li>{@code attachPdf} — attach the rendered PO PDF</li>
     * </ul>
     */
    @Transactional
    public PurchaseOrderDto sendToSupplier(Long id, String toOverride, String subjectOverride,
                                            String bodyOverride, boolean attachPdf) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + id));
        if (po.getStatus() == PurchaseOrderStatus.DRAFT) {
            throw new IllegalStateException("Submit the PO before sending to a supplier.");
        }
        if (po.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot send a cancelled PO.");
        }

        String recipient = toOverride != null && !toOverride.isBlank()
                ? toOverride.trim()
                : (po.getSupplier() != null ? po.getSupplier().getEmail() : null);

        boolean dispatched = false;
        if (recipient != null && !recipient.isBlank() && emailService != null) {
            try {
                String subject = subjectOverride != null && !subjectOverride.isBlank()
                        ? subjectOverride
                        : ("Purchase Order " + po.getPoNumber());
                String body = bodyOverride != null && !bodyOverride.isBlank()
                        ? bodyOverride
                        : defaultSendBody(po);
                byte[] pdf = null;
                String attachmentName = null;
                if (attachPdf && purchaseOrderPdfService != null) {
                    try {
                        pdf = purchaseOrderPdfService.generatePdf(po.getId());
                        attachmentName = "purchase_order_" + (po.getPoNumber() != null
                                ? po.getPoNumber().replace('/', '_') : po.getId()) + ".pdf";
                    } catch (Exception e) {
                        // PDF render failure shouldn't block the notification —
                        // send the plain-text summary and let the shop resend
                        // with a different attachment path.
                        logger.warn("PO PDF render failed for id={}, sending email without attachment", po.getId(), e);
                    }
                }
                if (pdf != null && attachmentName != null) {
                    emailService.sendEmailWithAttachment(recipient, subject, body,
                            attachmentName, pdf, "application/pdf");
                } else {
                    emailService.sendEmail(recipient, subject, body);
                }
                dispatched = true;
            } catch (Exception e) {
                logger.warn("Email dispatch failed for PO {} to {} — stamping sentAt anyway (out-of-band send)",
                        po.getPoNumber(), recipient, e);
            }
        } else {
            logger.info("No recipient / email service available for PO {} — stamping sentAt without dispatch",
                    po.getPoNumber());
        }

        po.setSentAt(LocalDateTime.now());
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        purchaseOrderProducer.sendMessage(EventType.UPDATED, PurchaseOrderEventDto.fromEntity(saved));
        logger.info("PO {} marked as sent to supplier (dispatched={}, recipient={})",
                saved.getPoNumber(), dispatched, recipient);
        return mapper.toDto(saved);
    }

    /**
     * Simple HTML email body — kept in service so tests can assert against it
     * without spinning up a template engine. Callers can override via the
     * {@code bodyOverride} arg on {@link #sendToSupplier}.
     */
    private String defaultSendBody(PurchaseOrder po) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p>Dear ").append(po.getSupplier() != null && po.getSupplier().getName() != null
                ? po.getSupplier().getName() : "Supplier").append(",</p>");
        sb.append("<p>Please find attached our purchase order <strong>")
                .append(po.getPoNumber()).append("</strong>.</p>");
        sb.append("<p><strong>Order date:</strong> ")
                .append(po.getOrderDate() != null ? po.getOrderDate().toLocalDate() : "-").append("<br/>");
        if (po.getExpectedDeliveryDate() != null) {
            sb.append("<strong>Expected delivery:</strong> ")
                    .append(po.getExpectedDeliveryDate().toLocalDate()).append("<br/>");
        }
        sb.append("<strong>Total:</strong> ₹")
                .append(po.getTotalAmount() != null ? po.getTotalAmount().toPlainString() : "-")
                .append("</p>");
        sb.append("<p>Kindly confirm receipt and expected dispatch date. Reach out if you need any clarification on line items or delivery instructions.</p>");
        sb.append("<p>Regards,<br/>")
                .append(po.getShop() != null && po.getShop().getName() != null ? po.getShop().getName() : "Team")
                .append("</p>");
        return sb.toString();
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

    // Optional-injected so the existing unit-test suite (Mockito @InjectMocks
    // with the older constructor set) doesn't have to be updated in the same
    // pass. Nulls are treated as no-ops in every helper below.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderStatusHistoryRepository statusHistoryRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderApprovalRepository approvalRepository;

    /**
     * Persist a status transition to the audit log. Skips no-ops so the
     * timeline only reflects real state changes. Runs in the caller's tx
     * so a rollback voids the history entry too.
     */
    private void recordPoStatusChange(PurchaseOrder po,
                                      PurchaseOrderStatus fromStatus,
                                      PurchaseOrderStatus toStatus,
                                      Long userId, String note) {
        if (fromStatus == toStatus) return;
        if (statusHistoryRepository == null) return; // unit-test friendly
        com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderStatusHistory h =
                new com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderStatusHistory();
        h.setPurchaseOrderId(po.getId());
        h.setShop(po.getShop());
        h.setFromStatus(fromStatus != null ? fromStatus.name() : null);
        h.setToStatus(toStatus != null ? toStatus.name() : "-");
        h.setChangedBy(userId != null ? String.valueOf(userId) : "system");
        h.setChangedAt(java.time.LocalDateTime.now());
        h.setNote(note);
        statusHistoryRepository.save(h);
    }

    /**
     * Distribute the header freight amount pro-rata across line taxable values
     * into {@code landedUnitCost}. Only runs when {@code landedCostEnabled}
     * is on so historical POs stay untouched. Uses HALF_UP with 4-decimal
     * precision to keep valuation rounding predictable.
     */
    private void applyLandedCost(PurchaseOrder po) {
        if (!po.isLandedCostEnabled() || org.springframework.util.CollectionUtils.isEmpty(po.getItems())) {
            if (po.getItems() != null) po.getItems().forEach(i -> i.setLandedUnitCost(null));
            return;
        }
        BigDecimal freight = nz(po.getFreightCharges());
        BigDecimal totalTaxable = po.getItems().stream()
                .map(i -> nz(i.getTaxableValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalTaxable.signum() == 0 || freight.signum() == 0) {
            po.getItems().forEach(i -> i.setLandedUnitCost(i.getUnitCost()));
            return;
        }
        for (PurchaseOrderItem item : po.getItems()) {
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            if (qty == 0) { item.setLandedUnitCost(item.getUnitCost()); continue; }
            BigDecimal share = nz(item.getTaxableValue())
                    .divide(totalTaxable, 6, java.math.RoundingMode.HALF_UP)
                    .multiply(freight);
            BigDecimal perUnit = share.divide(BigDecimal.valueOf(qty), 4, java.math.RoundingMode.HALF_UP);
            item.setLandedUnitCost(nz(item.getUnitCost()).add(perUnit)
                    .setScale(4, java.math.RoundingMode.HALF_UP));
        }
    }

    /** Read-side helper for the FE timeline widget. */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.List<com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderStatusHistory> getStatusHistory(Long id) {
        return statusHistoryRepository.findByPurchaseOrderIdOrderByChangedAtAsc(id);
    }

    /** Multi-level approval steps for a PO. Seeds lazily on first fetch. */
    @org.springframework.transaction.annotation.Transactional
    public java.util.List<com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderApproval> getOrSeedApprovals(Long id) {
        java.util.List<com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderApproval> steps =
                approvalRepository.findByPurchaseOrderIdOrderByLevelAsc(id);
        if (!steps.isEmpty()) return steps;

        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found with ID: " + id));

        BigDecimal thresholdL1 = new BigDecimal("50000");
        if (po.getShop() != null && po.getShop().getPoApprovalThresholdAmount() != null) {
            thresholdL1 = po.getShop().getPoApprovalThresholdAmount();
        }

        com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderApproval l1 =
                new com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderApproval();
        l1.setPurchaseOrderId(po.getId());
        l1.setShop(po.getShop());
        l1.setLevel((short) 1);
        l1.setApproverRole("ADMIN");
        l1.setStatus("PENDING");
        l1.setThresholdMin(BigDecimal.ZERO);
        approvalRepository.save(l1);

        if (nz(po.getTotalAmount()).compareTo(thresholdL1) > 0) {
            com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderApproval l2 =
                    new com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderApproval();
            l2.setPurchaseOrderId(po.getId());
            l2.setShop(po.getShop());
            l2.setLevel((short) 2);
            l2.setApproverRole("OWNER");
            l2.setStatus("PENDING");
            l2.setThresholdMin(thresholdL1);
            approvalRepository.save(l2);
        }
        return approvalRepository.findByPurchaseOrderIdOrderByLevelAsc(id);
    }

    /** Approve one level. Sequential — L2 waits until L1 is done. */
    @org.springframework.transaction.annotation.Transactional
    public com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderApproval approveStep(Long approvalId, Long userId, String note) {
        com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderApproval a = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval step not found: " + approvalId));
        if (!"PENDING".equals(a.getStatus())) {
            throw new com.desitech.vyaparsathi.common.exception.BusinessValidationException("Approval step is already " + a.getStatus());
        }
        for (com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderApproval prior
                : approvalRepository.findByPurchaseOrderIdOrderByLevelAsc(a.getPurchaseOrderId())) {
            if (prior.getLevel() < a.getLevel() && !"APPROVED".equals(prior.getStatus())) {
                throw new com.desitech.vyaparsathi.common.exception.BusinessValidationException(
                        "Prior approval step L" + prior.getLevel() + " is not complete.");
            }
        }
        a.setStatus("APPROVED");
        a.setApproverId(userId);
        a.setApprovedAt(java.time.LocalDateTime.now());
        a.setNote(note);
        return approvalRepository.save(a);
    }

    /** null-safe BigDecimal: null → ZERO. */
    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * Force-close a partially-received PO with a written reason. Used when the
     * supplier cannot deliver the remainder and both sides agree to close.
     * Sets PO status to RECEIVED and stamps the short-close audit fields; any
     * later GRN attempt is blocked because we require SUBMITTED / PARTIALLY_RECEIVED.
     */
    @org.springframework.transaction.annotation.Transactional
    public com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderDto forceClose(Long id, String reason, Long userId) {
        if (reason == null || reason.isBlank()) {
            throw new com.desitech.vyaparsathi.common.exception.BusinessValidationException("Close reason is required.");
        }
        com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new com.desitech.vyaparsathi.common.exception.ResourceNotFoundException("PO not found: " + id));
        com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus s = po.getStatus();
        if (s == com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus.RECEIVED
                || s == com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus.CANCELLED) {
            throw new com.desitech.vyaparsathi.common.exception.BusinessValidationException(
                    "PO " + po.getPoNumber() + " is already " + s + " — nothing to force-close.");
        }
        com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus fromStatus = po.getStatus();
        po.setShortClosed(true);
        po.setCloseReason(reason);
        po.setClosedAt(java.time.LocalDateTime.now());
        po.setClosedBy(userId);
        po.setStatus(com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus.RECEIVED);
        com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder saved = purchaseOrderRepository.save(po);
        recordPoStatusChange(saved, fromStatus,
                com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus.RECEIVED,
                userId, "Short-closed: " + reason);
        return mapper.toDto(saved);
    }
}