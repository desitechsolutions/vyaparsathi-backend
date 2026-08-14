package com.desitech.vyaparsathi.purchaseorder.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

        ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("500");
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
                .hasMessageContaining("Draft");
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
