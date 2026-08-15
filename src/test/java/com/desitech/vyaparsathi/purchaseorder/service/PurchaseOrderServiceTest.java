package com.desitech.vyaparsathi.purchaseorder.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.gst.service.GstJurisdictionService;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderDto;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderItemDto;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import com.desitech.vyaparsathi.purchaseorder.enums.EventType;
import com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus;
import com.desitech.vyaparsathi.purchaseorder.events.PurchaseOrderProducer;
import com.desitech.vyaparsathi.purchaseorder.events.dto.PurchaseOrderEventDto;
import com.desitech.vyaparsathi.purchaseorder.mapper.PurchaseOrderMapper;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderItemRepository;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import com.desitech.vyaparsathi.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the V81 state-machine additions on {@link PurchaseOrderService}.
 * Covers cancel / send / mark-received / delete guards, findOpenOrders filter
 * behaviour, and DRAFT-only enforcement on update.
 */
class PurchaseOrderServiceTest {

    @InjectMocks
    private PurchaseOrderService purchaseOrderService;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private ItemVariantRepository itemVariantRepository;
    @Mock
    private PurchaseOrderMapper mapper;
    @Mock
    private PurchaseOrderProducer purchaseOrderProducer;
    @Mock
    private ShopRepository shopRepository;
    @Mock
    private GstJurisdictionService gstJurisdictionService;
    @Mock
    private PurchaseOrderNumberService purchaseOrderNumberService;

    private Supplier supplier;
    private ItemVariant variant;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentShopId(1L);

        supplier = new Supplier();
        supplier.setId(1L);
        supplier.setName("Acme Supplies");

        variant = new ItemVariant();
        variant.setId(10L);
        variant.setSku("SKU-100");

        when(mapper.toDto(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder po = invocation.getArgument(0);
            PurchaseOrderDto dto = new PurchaseOrderDto();
            dto.setId(po.getId());
            dto.setStatus(po.getStatus());
            dto.setCancelledAt(po.getCancelledAt());
            dto.setCancelledBy(po.getCancelledBy());
            dto.setCancellationReason(po.getCancellationReason());
            dto.setSentAt(po.getSentAt());
            dto.setReceivedAt(po.getReceivedAt());
            return dto;
        });

        // V83 defaults: no shop resolved, no supplier code resolved → intra-state.
        // Individual tests override for inter-state (IGST) scenarios.
        when(shopRepository.findById(1L)).thenReturn(Optional.empty());
        when(gstJurisdictionService.resolveStateCode(any(Shop.class))).thenReturn(Optional.empty());
        when(gstJurisdictionService.resolveStateCode((Shop) null)).thenReturn(Optional.empty());
        when(gstJurisdictionService.resolveStateCode(any(Supplier.class))).thenReturn(Optional.empty());
        when(gstJurisdictionService.resolveStateCode((Supplier) null)).thenReturn(Optional.empty());
        when(gstJurisdictionService.isIntraState(any(), any())).thenReturn(true);

        // Default: return a synthetic number for auto-gen paths. Individual
        // tests can override or send an explicit poNumber to skip generation.
        when(purchaseOrderNumberService.nextPurchaseOrderNumber(any(), any()))
                .thenReturn("PO/AUTO/00001");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── createPurchaseOrder ──────────────────────────────────────────

    @Test
    @DisplayName("createPurchaseOrder forces DRAFT status and emits CREATED event")
    void createPurchaseOrder_alwaysDraft() {
        PurchaseOrderDto dto = new PurchaseOrderDto();
        dto.setPoNumber("PO-1");
        dto.setSupplierId(1L);
        dto.setOrderDate(LocalDateTime.now());
        dto.setStatus(PurchaseOrderStatus.SUBMITTED); // caller lies — server must ignore
        PurchaseOrderItemDto itemDto = new PurchaseOrderItemDto();
        itemDto.setItemVariantId(10L);
        itemDto.setQuantity(5);
        itemDto.setUnitCost(new BigDecimal("100"));
        dto.setItems(List.of(itemDto));

        when(purchaseOrderRepository.existsByPoNumber("PO-1")).thenReturn(false);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(itemVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder po = invocation.getArgument(0);
            po.setId(100L);
            return po;
        });

        purchaseOrderService.createPurchaseOrder(dto);

        // V83: create saves twice — first to get the header ID for line FKs,
        // then again after recomputeTotals reconciles subtotal/tax/rounding.
        ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        PurchaseOrder finalPo = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(finalPo.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        // No GST on the variant → subtotal = 5 × 100 = 500, tax = 0, rounded = 500.
        assertThat(finalPo.getTotalAmount()).isEqualByComparingTo("500");
        assertThat(finalPo.getSubtotal()).isEqualByComparingTo("500");
        assertThat(finalPo.getTotalTax()).isEqualByComparingTo("0");
        verify(purchaseOrderProducer).sendMessage(eq(EventType.CREATED), any(PurchaseOrderEventDto.class));
    }

    @Test
    @DisplayName("createPurchaseOrder rejects duplicate PO numbers")
    void createPurchaseOrder_duplicatePoNumber() {
        PurchaseOrderDto dto = new PurchaseOrderDto();
        dto.setPoNumber("PO-DUP");
        dto.setItems(List.of(new PurchaseOrderItemDto()));
        when(purchaseOrderRepository.existsByPoNumber("PO-DUP")).thenReturn(true);

        assertThatThrownBy(() -> purchaseOrderService.createPurchaseOrder(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("createPurchaseOrder auto-generates PO number when caller omits it (V84)")
    void createPurchaseOrder_autoGeneratesPoNumber() {
        PurchaseOrderDto dto = new PurchaseOrderDto();
        // Explicitly no poNumber — mimics the FE editor flow after V84.
        dto.setPoNumber(null);
        dto.setSupplierId(1L);
        dto.setOrderDate(LocalDateTime.now());
        PurchaseOrderItemDto line = new PurchaseOrderItemDto();
        line.setItemVariantId(10L);
        line.setQuantity(1);
        line.setUnitCost(new BigDecimal("50"));
        dto.setItems(List.of(line));

        when(purchaseOrderRepository.existsByPoNumber(any())).thenReturn(false);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(itemVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> {
            PurchaseOrder p = i.getArgument(0);
            if (p.getId() == null) p.setId(500L);
            return p;
        });
        when(purchaseOrderNumberService.nextPurchaseOrderNumber(any(), any()))
                .thenReturn("PO/26-27/00042");

        purchaseOrderService.createPurchaseOrder(dto);

        PurchaseOrder saved = captureFinalSave();
        assertThat(saved.getPoNumber()).isEqualTo("PO/26-27/00042");
    }

    @Test
    @DisplayName("createPurchaseOrder treats blank PO number as auto-generate request")
    void createPurchaseOrder_blankPoNumberAutoGenerates() {
        PurchaseOrderDto dto = new PurchaseOrderDto();
        dto.setPoNumber("   ");  // Blank / whitespace only
        dto.setSupplierId(1L);
        dto.setOrderDate(LocalDateTime.now());
        PurchaseOrderItemDto line = new PurchaseOrderItemDto();
        line.setItemVariantId(10L);
        line.setQuantity(1);
        line.setUnitCost(new BigDecimal("50"));
        dto.setItems(List.of(line));

        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(itemVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> {
            PurchaseOrder p = i.getArgument(0);
            if (p.getId() == null) p.setId(501L);
            return p;
        });
        when(purchaseOrderNumberService.nextPurchaseOrderNumber(any(), any()))
                .thenReturn("PO/26-27/00043");

        purchaseOrderService.createPurchaseOrder(dto);

        PurchaseOrder saved = captureFinalSave();
        assertThat(saved.getPoNumber()).isEqualTo("PO/26-27/00043");
        // existsByPoNumber must NOT be consulted for auto-gen (sequence guarantees uniqueness).
        verify(purchaseOrderRepository, org.mockito.Mockito.never()).existsByPoNumber(any());
    }

    // ─── updatePurchaseOrder ──────────────────────────────────────────

    @Test
    @DisplayName("updatePurchaseOrder rejects any non-DRAFT status")
    void updatePurchaseOrder_nonDraftRejected() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.SUBMITTED);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        PurchaseOrderDto dto = new PurchaseOrderDto();
        dto.setSupplierId(1L);
        dto.setItems(List.of());

        assertThatThrownBy(() -> purchaseOrderService.updatePurchaseOrder(1L, dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("editable");
    }

    @Test
    @DisplayName("updatePurchaseOrder rejects REJECTED status — must go through /revise first")
    void updatePurchaseOrder_rejectedRequiresRevise() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.REJECTED);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        PurchaseOrderDto dto = new PurchaseOrderDto();
        dto.setSupplierId(1L);
        dto.setItems(List.of());

        assertThatThrownBy(() -> purchaseOrderService.updatePurchaseOrder(1L, dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/revise");
    }

    // ─── submitPurchaseOrder ──────────────────────────────────────────

    @Test
    @DisplayName("submitPurchaseOrder transitions DRAFT → SUBMITTED and emits SUBMITTED event")
    void submitPurchaseOrder_success() {
        PurchaseOrder po = draftPo(1L);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        PurchaseOrderDto result = purchaseOrderService.submitPurchaseOrder(1L);

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.SUBMITTED);
        assertThat(result.getStatus()).isEqualTo(PurchaseOrderStatus.SUBMITTED);
        verify(purchaseOrderProducer).sendMessage(eq(EventType.SUBMITTED), any(PurchaseOrderEventDto.class));
    }

    @Test
    @DisplayName("submitPurchaseOrder rejects non-DRAFT status")
    void submitPurchaseOrder_nonDraftRejected() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.SUBMITTED);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> purchaseOrderService.submitPurchaseOrder(1L))
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── deletePurchaseOrder ──────────────────────────────────────────

    @Test
    @DisplayName("deletePurchaseOrder allows DRAFT only")
    void deletePurchaseOrder_draftOnly() {
        PurchaseOrder po = draftPo(1L);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        purchaseOrderService.deletePurchaseOrder(1L);

        verify(purchaseOrderRepository).deleteById(1L);
        verify(purchaseOrderProducer).sendMessage(eq(EventType.DELETED), any(PurchaseOrderEventDto.class));
    }

    @Test
    @DisplayName("deletePurchaseOrder rejects SUBMITTED and asks caller to cancel")
    void deletePurchaseOrder_submittedRejected() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.SUBMITTED);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> purchaseOrderService.deletePurchaseOrder(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cancel this PO instead");
        verify(purchaseOrderRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("deletePurchaseOrder rejects RECEIVED (terminal, non-editable)")
    void deletePurchaseOrder_receivedRejected() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.RECEIVED);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> purchaseOrderService.deletePurchaseOrder(1L))
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── cancelPurchaseOrder (V81) ────────────────────────────────────

    @Test
    @DisplayName("cancelPurchaseOrder stamps who/when/why and emits CANCELLED event")
    void cancelPurchaseOrder_success() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.SUBMITTED);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        purchaseOrderService.cancelPurchaseOrder(1L, "Supplier bankrupt", 42L);
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);
        assertThat(po.getCancellationReason()).isEqualTo("Supplier bankrupt");
        assertThat(po.getCancelledBy()).isEqualTo(42L);
        assertThat(po.getCancelledAt()).isBetween(before, after);
        verify(purchaseOrderProducer).sendMessage(eq(EventType.CANCELLED), any(PurchaseOrderEventDto.class));
    }

    @Test
    @DisplayName("cancelPurchaseOrder rejects DRAFT (should be deleted instead)")
    void cancelPurchaseOrder_draftRejected() {
        PurchaseOrder po = draftPo(1L); // DRAFT
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> purchaseOrderService.cancelPurchaseOrder(1L, "typo", 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Delete draft POs");
    }

    @Test
    @DisplayName("cancelPurchaseOrder rejects already-terminal RECEIVED")
    void cancelPurchaseOrder_terminalRejected() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.RECEIVED);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> purchaseOrderService.cancelPurchaseOrder(1L, "changed mind", 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already");
    }

    @Test
    @DisplayName("cancelPurchaseOrder rejects already-CANCELLED (terminal)")
    void cancelPurchaseOrder_alreadyCancelledRejected() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> purchaseOrderService.cancelPurchaseOrder(1L, "again", 42L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("cancelPurchaseOrder throws NotFound for unknown id")
    void cancelPurchaseOrder_notFound() {
        when(purchaseOrderRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> purchaseOrderService.cancelPurchaseOrder(999L, "x", 42L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── markAsReceived (V81) ─────────────────────────────────────────

    @Test
    @DisplayName("markAsReceived transitions to RECEIVED and stamps receivedAt")
    void markAsReceived_success() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        purchaseOrderService.markAsReceived(1L);

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        assertThat(po.getReceivedAt()).isNotNull();
        verify(purchaseOrderProducer).sendMessage(eq(EventType.RECEIVED), any(PurchaseOrderEventDto.class));
    }

    @Test
    @DisplayName("markAsReceived is idempotent — calling on RECEIVED returns without re-saving or re-emitting")
    void markAsReceived_idempotent() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.RECEIVED);
        LocalDateTime originalReceivedAt = LocalDateTime.now().minusHours(2);
        po.setReceivedAt(originalReceivedAt);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        purchaseOrderService.markAsReceived(1L);

        assertThat(po.getReceivedAt()).isEqualTo(originalReceivedAt);
        verify(purchaseOrderRepository, never()).save(any());
        verify(purchaseOrderProducer, never()).sendMessage(eq(EventType.RECEIVED), any());
    }

    @Test
    @DisplayName("markAsReceived rejects CANCELLED")
    void markAsReceived_cancelledRejected() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> purchaseOrderService.markAsReceived(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cancelled");
    }

    // ─── sendToSupplier (V81) ─────────────────────────────────────────

    @Test
    @DisplayName("sendToSupplier stamps sentAt from SUBMITTED and emits UPDATED (temporary bus type)")
    void sendToSupplier_success() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.SUBMITTED);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        purchaseOrderService.sendToSupplier(1L);

        assertThat(po.getSentAt()).isNotNull();
        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.SUBMITTED); // status unchanged
        verify(purchaseOrderProducer).sendMessage(eq(EventType.UPDATED), any(PurchaseOrderEventDto.class));
    }

    @Test
    @DisplayName("sendToSupplier can re-send from PARTIALLY_RECEIVED (supplier lost the copy)")
    void sendToSupplier_partiallyReceivedAllowed() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        purchaseOrderService.sendToSupplier(1L);
        assertThat(po.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("sendToSupplier rejects DRAFT")
    void sendToSupplier_draftRejected() {
        PurchaseOrder po = draftPo(1L); // DRAFT
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> purchaseOrderService.sendToSupplier(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Submit");
    }

    @Test
    @DisplayName("sendToSupplier rejects CANCELLED")
    void sendToSupplier_cancelledRejected() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> purchaseOrderService.sendToSupplier(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cancelled");
    }

    // ─── findOpenOrders (V81 rename) ──────────────────────────────────

    @Test
    @DisplayName("findOpenOrders queries only SUBMITTED and PARTIALLY_RECEIVED")
    void findOpenOrders_correctFilter() {
        when(purchaseOrderRepository.findAllByStatusIn(any())).thenReturn(new ArrayList<>());

        purchaseOrderService.findOpenOrders();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PurchaseOrderStatus>> captor = ArgumentCaptor.forClass(List.class);
        verify(purchaseOrderRepository).findAllByStatusIn(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(
                PurchaseOrderStatus.SUBMITTED,
                PurchaseOrderStatus.PARTIALLY_RECEIVED);
    }

    @Test
    @DisplayName("Deprecated getPendingPurchaseOrders delegates to findOpenOrders")
    @SuppressWarnings("deprecation")
    void deprecatedAlias_delegates() {
        when(purchaseOrderRepository.findAllByStatusIn(any())).thenReturn(new ArrayList<>());

        purchaseOrderService.getPendingPurchaseOrders();

        verify(purchaseOrderRepository).findAllByStatusIn(any());
    }

    // ─── markAsReceiving (existing behaviour) ─────────────────────────

    @Test
    @DisplayName("markAsReceiving transitions SUBMITTED → PARTIALLY_RECEIVED")
    void markAsReceiving_success() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.SUBMITTED);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        purchaseOrderService.markAsReceiving(1L);

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        verify(purchaseOrderRepository).save(po);
    }

    @Test
    @DisplayName("markAsReceiving is a no-op when already PARTIALLY_RECEIVED")
    void markAsReceiving_noopOnPartial() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        purchaseOrderService.markAsReceiving(1L);

        verify(purchaseOrderRepository, never()).save(any());
    }

    // ─── V85 approval workflow ────────────────────────────────────────

    @Test
    @DisplayName("submitPurchaseOrder → SUBMITTED when shop has no approval policy")
    void submitPurchaseOrder_noPolicy_directSubmit() {
        PurchaseOrder po = draftPo(1L);
        // Shop has policy disabled — usual path.
        com.desitech.vyaparsathi.shop.entity.Shop shop = new com.desitech.vyaparsathi.shop.entity.Shop();
        shop.setId(1L);
        shop.setPoApprovalRequired(false);
        po.setShop(shop);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        purchaseOrderService.submitPurchaseOrder(1L, 42L);

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.SUBMITTED);
        assertThat(po.getSubmittedBy()).isEqualTo(42L);
        verify(purchaseOrderProducer).sendMessage(eq(EventType.SUBMITTED), any(PurchaseOrderEventDto.class));
    }

    @Test
    @DisplayName("submitPurchaseOrder → PENDING_APPROVAL when shop policy + threshold trip")
    void submitPurchaseOrder_thresholdTrip_pendingApproval() {
        PurchaseOrder po = draftPo(1L);
        po.setTotalAmount(new BigDecimal("1000"));
        com.desitech.vyaparsathi.shop.entity.Shop shop = new com.desitech.vyaparsathi.shop.entity.Shop();
        shop.setId(1L);
        shop.setPoApprovalRequired(true);
        shop.setPoApprovalThresholdAmount(new BigDecimal("500"));
        po.setShop(shop);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        purchaseOrderService.submitPurchaseOrder(1L, 42L);

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.PENDING_APPROVAL);
        assertThat(po.getSubmittedBy()).isEqualTo(42L);
        verify(purchaseOrderProducer).sendMessage(eq(EventType.APPROVAL_REQUESTED), any(PurchaseOrderEventDto.class));
        verify(purchaseOrderProducer, org.mockito.Mockito.never()).sendMessage(eq(EventType.SUBMITTED), any());
    }

    @Test
    @DisplayName("submitPurchaseOrder threshold check is inclusive of the exact amount")
    void submitPurchaseOrder_exactThreshold_pendingApproval() {
        PurchaseOrder po = draftPo(1L);
        po.setTotalAmount(new BigDecimal("500"));
        com.desitech.vyaparsathi.shop.entity.Shop shop = new com.desitech.vyaparsathi.shop.entity.Shop();
        shop.setId(1L);
        shop.setPoApprovalRequired(true);
        shop.setPoApprovalThresholdAmount(new BigDecimal("500"));
        po.setShop(shop);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        purchaseOrderService.submitPurchaseOrder(1L, 42L);

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("submitPurchaseOrder → SUBMITTED when total is below threshold")
    void submitPurchaseOrder_belowThreshold_directSubmit() {
        PurchaseOrder po = draftPo(1L);
        po.setTotalAmount(new BigDecimal("100"));
        com.desitech.vyaparsathi.shop.entity.Shop shop = new com.desitech.vyaparsathi.shop.entity.Shop();
        shop.setId(1L);
        shop.setPoApprovalRequired(true);
        shop.setPoApprovalThresholdAmount(new BigDecimal("500"));
        po.setShop(shop);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        purchaseOrderService.submitPurchaseOrder(1L, 42L);

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.SUBMITTED);
        verify(purchaseOrderProducer).sendMessage(eq(EventType.SUBMITTED), any(PurchaseOrderEventDto.class));
    }

    @Test
    @DisplayName("approvePurchaseOrder → SUBMITTED, stamps approver, fires APPROVED + SUBMITTED")
    void approvePurchaseOrder_success() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.PENDING_APPROVAL);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        purchaseOrderService.approvePurchaseOrder(1L, 99L);

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.SUBMITTED);
        assertThat(po.getApprovedBy()).isEqualTo(99L);
        assertThat(po.getApprovedAt()).isNotNull();
        verify(purchaseOrderProducer).sendMessage(eq(EventType.APPROVED), any(PurchaseOrderEventDto.class));
        verify(purchaseOrderProducer).sendMessage(eq(EventType.SUBMITTED), any(PurchaseOrderEventDto.class));
    }

    @Test
    @DisplayName("approvePurchaseOrder rejects non-PENDING_APPROVAL status")
    void approvePurchaseOrder_wrongStatus() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.DRAFT);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> purchaseOrderService.approvePurchaseOrder(1L, 99L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING_APPROVAL");
    }

    @Test
    @DisplayName("rejectPurchaseOrder → REJECTED (not DRAFT), stamps rejecter + reason, fires REJECTED")
    void rejectPurchaseOrder_success() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.PENDING_APPROVAL);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        purchaseOrderService.rejectPurchaseOrder(1L, "Amount too high", 99L);

        // Refactor: REJECTED (not DRAFT) so the approver's comment stays as a
        // banner until the requester explicitly clicks Revise.
        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.REJECTED);
        assertThat(po.getRejectionReason()).isEqualTo("Amount too high");
        assertThat(po.getRejectedBy()).isEqualTo(99L);
        assertThat(po.getRejectedAt()).isNotNull();
        verify(purchaseOrderProducer).sendMessage(eq(EventType.REJECTED), any(PurchaseOrderEventDto.class));
    }

    @Test
    @DisplayName("reviseRejectedPurchaseOrder → DRAFT, keeps rejection reason as reference")
    void reviseRejectedPurchaseOrder_success() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.REJECTED);
        po.setRejectionReason("Amount too high");
        po.setRejectedBy(99L);
        po.setRejectedAt(LocalDateTime.now().minusHours(2));
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        purchaseOrderService.reviseRejectedPurchaseOrder(1L, 42L);

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        // Rejection metadata is deliberately preserved — the FE renders it
        // as a reference banner during revision.
        assertThat(po.getRejectionReason()).isEqualTo("Amount too high");
        assertThat(po.getRejectedBy()).isEqualTo(99L);
        assertThat(po.getRejectedAt()).isNotNull();
        // Fires the dedicated REVISED event so audit / notification subscribers
        // can distinguish "requester revised after rejection" from a plain edit.
        verify(purchaseOrderProducer).sendMessage(eq(EventType.REVISED), any(PurchaseOrderEventDto.class));
        verify(purchaseOrderProducer, org.mockito.Mockito.never()).sendMessage(eq(EventType.UPDATED), any());
    }

    @Test
    @DisplayName("reviseRejectedPurchaseOrder rejects non-REJECTED status")
    void reviseRejectedPurchaseOrder_wrongStatus() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.DRAFT);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> purchaseOrderService.reviseRejectedPurchaseOrder(1L, 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REJECTED");
    }

    @Test
    @DisplayName("submit clears stale rejection metadata when resubmitting a revised PO")
    void submitPurchaseOrder_resubmitClearsRejectionMetadata() {
        PurchaseOrder po = draftPo(1L);
        po.setRejectionReason("Prior reason");
        po.setRejectedBy(99L);
        po.setRejectedAt(LocalDateTime.now().minusHours(2));
        com.desitech.vyaparsathi.shop.entity.Shop shop = new com.desitech.vyaparsathi.shop.entity.Shop();
        shop.setId(1L);
        shop.setPoApprovalRequired(false);
        po.setShop(shop);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        purchaseOrderService.submitPurchaseOrder(1L, 42L);

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.SUBMITTED);
        assertThat(po.getRejectionReason()).isNull();
        assertThat(po.getRejectedBy()).isNull();
        assertThat(po.getRejectedAt()).isNull();
    }

    @Test
    @DisplayName("rejectPurchaseOrder rejects non-PENDING_APPROVAL status")
    void rejectPurchaseOrder_wrongStatus() {
        PurchaseOrder po = draftPo(1L);
        po.setStatus(PurchaseOrderStatus.SUBMITTED);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> purchaseOrderService.rejectPurchaseOrder(1L, "nope", 99L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING_APPROVAL");
    }

    // ─── V84 duplicatePurchaseOrder ───────────────────────────────────

    @Test
    @DisplayName("duplicatePurchaseOrder clones supplier + line items into a fresh DRAFT")
    void duplicatePurchaseOrder_success() {
        // Existing PO in a terminal state — a common trigger for duplicating.
        PurchaseOrder src = draftPo(50L);
        src.setStatus(PurchaseOrderStatus.RECEIVED);
        src.setPoNumber("PO/26-27/00007");
        src.setNotes("Original notes");
        src.setFreightCharges(new BigDecimal("75"));
        src.setReceivedAt(LocalDateTime.now().minusDays(3));
        src.setSentAt(LocalDateTime.now().minusDays(10));
        // Give the source line a full V83 shape so we can verify carry-over.
        PurchaseOrderItem srcLine = src.getItems().get(0);
        srcLine.setDiscount(new BigDecimal("20"));
        srcLine.setGstRate(18);
        srcLine.setHsnCode("6103");
        srcLine.setReceivedQuantity(new BigDecimal("5"));

        when(purchaseOrderRepository.findById(50L)).thenReturn(Optional.of(src));
        when(purchaseOrderNumberService.nextPurchaseOrderNumber(any(), any()))
                .thenReturn("PO/26-27/00099");
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> {
            PurchaseOrder p = i.getArgument(0);
            if (p.getId() == null) p.setId(999L);
            return p;
        });

        purchaseOrderService.duplicatePurchaseOrder(50L);

        PurchaseOrder copy = captureFinalSave();
        assertThat(copy.getId()).isNotEqualTo(src.getId());
        assertThat(copy.getPoNumber()).isEqualTo("PO/26-27/00099");
        assertThat(copy.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        assertThat(copy.getSupplier()).isEqualTo(src.getSupplier());
        assertThat(copy.getNotes()).isEqualTo("Original notes");
        assertThat(copy.getFreightCharges()).isEqualByComparingTo("75");
        // Lifecycle stamps must NOT carry over.
        assertThat(copy.getSentAt()).isNull();
        assertThat(copy.getReceivedAt()).isNull();
        assertThat(copy.getCancelledAt()).isNull();
        assertThat(copy.getExpectedDeliveryDate()).isNull();

        PurchaseOrderItem copyLine = copy.getItems().get(0);
        assertThat(copyLine.getItemVariant()).isEqualTo(srcLine.getItemVariant());
        assertThat(copyLine.getQuantity()).isEqualTo(srcLine.getQuantity());
        assertThat(copyLine.getUnitCost()).isEqualByComparingTo(srcLine.getUnitCost());
        assertThat(copyLine.getDiscount()).isEqualByComparingTo("20");
        assertThat(copyLine.getGstRate()).isEqualTo(18);
        assertThat(copyLine.getHsnCode()).isEqualTo("6103");
        // receivedQuantity starts fresh on a copy.
        assertThat(copyLine.getReceivedQuantity()).isEqualByComparingTo("0");
        verify(purchaseOrderProducer).sendMessage(eq(EventType.CREATED), any(PurchaseOrderEventDto.class));
    }

    @Test
    @DisplayName("duplicatePurchaseOrder rejects unknown id")
    void duplicatePurchaseOrder_notFound() {
        when(purchaseOrderRepository.findById(9999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> purchaseOrderService.duplicatePurchaseOrder(9999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── V83 recompute (line-level GST + header totals) ───────────────

    @Test
    @DisplayName("recompute — intra-state supplier splits GST 50/50 into CGST + SGST")
    void recompute_intraState_cgstSgstSplit() {
        variant.setGstRate(18); // 18% → 9% CGST + 9% SGST

        PurchaseOrderDto dto = new PurchaseOrderDto();
        dto.setPoNumber("PO-INTRA-1");
        dto.setSupplierId(1L);
        dto.setOrderDate(LocalDateTime.now());
        PurchaseOrderItemDto line = new PurchaseOrderItemDto();
        line.setItemVariantId(10L);
        line.setQuantity(2);
        line.setUnitCost(new BigDecimal("100"));
        dto.setItems(List.of(line));

        when(purchaseOrderRepository.existsByPoNumber(anyString())).thenReturn(false);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(itemVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> {
            PurchaseOrder p = i.getArgument(0);
            p.setId(200L);
            return p;
        });

        purchaseOrderService.createPurchaseOrder(dto);

        ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        PurchaseOrder saved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(saved.getSubtotal()).isEqualByComparingTo("200");   // 2 × 100
        assertThat(saved.getTotalTax()).isEqualByComparingTo("36");    // 200 × 18%
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("236"); // 200 + 36

        PurchaseOrderItem savedLine = saved.getItems().get(0);
        assertThat(savedLine.getTaxableValue()).isEqualByComparingTo("200");
        assertThat(savedLine.getCgstAmt()).isEqualByComparingTo("18");
        assertThat(savedLine.getSgstAmt()).isEqualByComparingTo("18");
        assertThat(savedLine.getIgstAmt()).isEqualByComparingTo("0");
        assertThat(savedLine.getLineTotal()).isEqualByComparingTo("236");
    }

    @Test
    @DisplayName("recompute — inter-state supplier routes 100% of GST into IGST")
    void recompute_interState_igst() {
        variant.setGstRate(18);
        // Different state codes → inter-state
        when(gstJurisdictionService.isIntraState(any(), any())).thenReturn(false);
        when(gstJurisdictionService.resolveStateCode(any(Shop.class))).thenReturn(Optional.of("29"));
        when(gstJurisdictionService.resolveStateCode(any(Supplier.class))).thenReturn(Optional.of("07"));

        PurchaseOrderDto dto = poDtoWithSingleLine("PO-INTER-1", 2, "100", null);
        stubRepositoriesForCreate();

        purchaseOrderService.createPurchaseOrder(dto);

        PurchaseOrder saved = captureFinalSave();
        assertThat(saved.getTotalTax()).isEqualByComparingTo("36");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("236");

        PurchaseOrderItem line = saved.getItems().get(0);
        assertThat(line.getCgstAmt()).isEqualByComparingTo("0");
        assertThat(line.getSgstAmt()).isEqualByComparingTo("0");
        assertThat(line.getIgstAmt()).isEqualByComparingTo("36");
    }

    @Test
    @DisplayName("recompute — line discount reduces taxable_value before GST is applied")
    void recompute_lineDiscount() {
        variant.setGstRate(18);
        PurchaseOrderDto dto = poDtoWithSingleLine("PO-DISC-1", 2, "100", new BigDecimal("50"));
        stubRepositoriesForCreate();

        purchaseOrderService.createPurchaseOrder(dto);

        PurchaseOrder saved = captureFinalSave();
        PurchaseOrderItem line = saved.getItems().get(0);
        // 2×100 = 200 − 50 discount = 150 taxable. GST 18% = 27.
        assertThat(line.getTaxableValue()).isEqualByComparingTo("150");
        assertThat(saved.getTotalDiscount()).isEqualByComparingTo("50");
        assertThat(line.getCgstAmt()).isEqualByComparingTo("13.5"); // 27/2
        assertThat(line.getSgstAmt()).isEqualByComparingTo("13.5");
        assertThat(saved.getTotalTax()).isEqualByComparingTo("27");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("177"); // 150 + 27
    }

    @Test
    @DisplayName("recompute — GST rate + HSN fall back to parent variant when line omits them")
    void recompute_variantFallback() {
        variant.setGstRate(12);
        variant.setHsn("6103");
        PurchaseOrderDto dto = poDtoWithSingleLine("PO-FB-1", 1, "1000", null);
        // Line has neither gstRate nor hsnCode — service must pull from variant.
        stubRepositoriesForCreate();

        purchaseOrderService.createPurchaseOrder(dto);

        PurchaseOrder saved = captureFinalSave();
        PurchaseOrderItem line = saved.getItems().get(0);
        assertThat(line.getGstRate()).isEqualTo(12);
        assertThat(line.getHsnCode()).isEqualTo("6103");
        assertThat(saved.getTotalTax()).isEqualByComparingTo("120"); // 1000 × 12%
    }

    @Test
    @DisplayName("recompute — explicit line-level HSN overrides variant HSN")
    void recompute_explicitHsnWins() {
        variant.setGstRate(5);
        variant.setHsn("VARIANT-HSN");
        PurchaseOrderDto dto = new PurchaseOrderDto();
        dto.setPoNumber("PO-HSN-1");
        dto.setSupplierId(1L);
        dto.setOrderDate(LocalDateTime.now());
        PurchaseOrderItemDto lineDto = new PurchaseOrderItemDto();
        lineDto.setItemVariantId(10L);
        lineDto.setQuantity(1);
        lineDto.setUnitCost(new BigDecimal("500"));
        lineDto.setHsnCode("OVERRIDDEN-HSN");
        lineDto.setGstRate(18);
        dto.setItems(List.of(lineDto));
        stubRepositoriesForCreate();

        purchaseOrderService.createPurchaseOrder(dto);

        PurchaseOrderItem line = captureFinalSave().getItems().get(0);
        assertThat(line.getHsnCode()).isEqualTo("OVERRIDDEN-HSN");
        assertThat(line.getGstRate()).isEqualTo(18);
    }

    @Test
    @DisplayName("recompute — zero-rate line records no tax and full amount as line_total")
    void recompute_zeroRate() {
        variant.setGstRate(0);
        PurchaseOrderDto dto = poDtoWithSingleLine("PO-ZR-1", 3, "50", null);
        stubRepositoriesForCreate();

        purchaseOrderService.createPurchaseOrder(dto);

        PurchaseOrder saved = captureFinalSave();
        PurchaseOrderItem line = saved.getItems().get(0);
        assertThat(line.getCgstAmt()).isEqualByComparingTo("0");
        assertThat(line.getSgstAmt()).isEqualByComparingTo("0");
        assertThat(line.getIgstAmt()).isEqualByComparingTo("0");
        assertThat(line.getLineTotal()).isEqualByComparingTo("150"); // 3 × 50
        assertThat(saved.getTotalTax()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("recompute — freight is added on top of subtotal + tax before rounding")
    void recompute_freight() {
        variant.setGstRate(18);
        PurchaseOrderDto dto = poDtoWithSingleLine("PO-FRT-1", 1, "100", null);
        dto.setFreightCharges(new BigDecimal("50"));
        stubRepositoriesForCreate();

        purchaseOrderService.createPurchaseOrder(dto);

        PurchaseOrder saved = captureFinalSave();
        assertThat(saved.getSubtotal()).isEqualByComparingTo("100");
        assertThat(saved.getTotalTax()).isEqualByComparingTo("18");
        assertThat(saved.getFreightCharges()).isEqualByComparingTo("50");
        // 100 + 18 + 50 = 168 (already whole, so round_off = 0)
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("168");
        assertThat(saved.getRoundOff()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("recompute — non-integer grand total is rounded HALF_UP and delta lands in round_off")
    void recompute_roundOff() {
        variant.setGstRate(18);
        // 1 × 99.5 = 99.5 taxable. Intra-state GST 18% = 17.91 → half = 8.96 (HALF_UP
        // of 8.955), so CGST + SGST = 17.92 (0.01 drift is intentional and mirrors
        // SaleService's split behaviour). Grand = 99.5 + 17.92 = 117.42 → 117 rounded.
        // round_off = 117 − 117.42 = −0.42.
        PurchaseOrderDto dto = poDtoWithSingleLine("PO-RO-1", 1, "99.5", null);
        stubRepositoriesForCreate();

        purchaseOrderService.createPurchaseOrder(dto);

        PurchaseOrder saved = captureFinalSave();
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("117");
        assertThat(saved.getRoundOff()).isEqualByComparingTo("-0.42");
    }

    @Test
    @DisplayName("recompute — header totals reconcile Σlines within rounding tolerance")
    void recompute_multilineReconciliation() {
        variant.setGstRate(18);
        // Second variant with GST 5 for tax rate mix.
        ItemVariant variant2 = new ItemVariant();
        variant2.setId(11L);
        variant2.setSku("SKU-101");
        variant2.setGstRate(5);

        PurchaseOrderDto dto = new PurchaseOrderDto();
        dto.setPoNumber("PO-MULTI-1");
        dto.setSupplierId(1L);
        dto.setOrderDate(LocalDateTime.now());
        PurchaseOrderItemDto a = new PurchaseOrderItemDto();
        a.setItemVariantId(10L); a.setQuantity(2); a.setUnitCost(new BigDecimal("100"));
        PurchaseOrderItemDto b = new PurchaseOrderItemDto();
        b.setItemVariantId(11L); b.setQuantity(4); b.setUnitCost(new BigDecimal("50"));
        dto.setItems(List.of(a, b));

        when(purchaseOrderRepository.existsByPoNumber(anyString())).thenReturn(false);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(itemVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(itemVariantRepository.findById(11L)).thenReturn(Optional.of(variant2));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> {
            PurchaseOrder p = i.getArgument(0);
            p.setId(300L);
            return p;
        });

        purchaseOrderService.createPurchaseOrder(dto);

        PurchaseOrder saved = captureFinalSave();
        // Line A: 2×100 = 200 taxable, GST 18% = 36
        // Line B: 4×50 = 200 taxable, GST 5% = 10
        // subtotal = 400, tax = 46, grand = 446
        assertThat(saved.getSubtotal()).isEqualByComparingTo("400");
        assertThat(saved.getTotalTax()).isEqualByComparingTo("46");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("446");
    }

    // ─── Test-only helpers ────────────────────────────────────────────

    private PurchaseOrderDto poDtoWithSingleLine(String poNumber, int qty, String unitCost, BigDecimal discount) {
        PurchaseOrderDto dto = new PurchaseOrderDto();
        dto.setPoNumber(poNumber);
        dto.setSupplierId(1L);
        dto.setOrderDate(LocalDateTime.now());
        PurchaseOrderItemDto line = new PurchaseOrderItemDto();
        line.setItemVariantId(10L);
        line.setQuantity(qty);
        line.setUnitCost(new BigDecimal(unitCost));
        line.setDiscount(discount);
        dto.setItems(List.of(line));
        return dto;
    }

    private void stubRepositoriesForCreate() {
        when(purchaseOrderRepository.existsByPoNumber(anyString())).thenReturn(false);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(itemVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer((Answer<PurchaseOrder>) i -> {
            PurchaseOrder p = i.getArgument(0);
            if (p.getId() == null) p.setId(999L);
            return p;
        });
    }

    private PurchaseOrder captureFinalSave() {
        ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues().get(captor.getAllValues().size() - 1);
    }

    // ─── helpers ──────────────────────────────────────────────────────

    private PurchaseOrder draftPo(Long id) {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(id);
        po.setPoNumber("PO-" + id);
        po.setSupplier(supplier);
        po.setOrderDate(LocalDateTime.now());
        po.setStatus(PurchaseOrderStatus.DRAFT);
        po.setTotalAmount(new BigDecimal("500"));
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setPurchaseOrder(po);
        item.setItemVariant(variant);
        item.setQuantity(5);
        item.setUnitCost(new BigDecimal("100"));
        po.setItems(new ArrayList<>(List.of(item)));
        return po;
    }
}
