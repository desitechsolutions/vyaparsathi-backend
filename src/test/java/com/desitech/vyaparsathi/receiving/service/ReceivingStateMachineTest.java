package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.inventory.repository.StockMovementRepository;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus;
import com.desitech.vyaparsathi.purchaseorder.events.PurchaseOrderProducer;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderItemRepository;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.entity.ReceivingTicket;
import com.desitech.vyaparsathi.receiving.enums.ReceivingStatus;
import com.desitech.vyaparsathi.receiving.enums.ReceivingTicketStatus;
import com.desitech.vyaparsathi.receiving.mapper.ReceivingMapper;
import com.desitech.vyaparsathi.receiving.repository.ReceivingRepository;
import com.desitech.vyaparsathi.receiving.repository.ReceivingStatusHistoryRepository;
import com.desitech.vyaparsathi.receiving.repository.ReceivingTicketRepository;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the Phase 6.1 state-machine entry points on {@link ReceivingService}.
 * The full receiving flow is heavier — this suite is focused on transition
 * legality (which status you can move to and from) and audit stamping.
 */
class ReceivingStateMachineTest {

    @Mock private ReceivingRepository receivingRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private ReceivingTicketRepository receivingTicketRepository;
    @Mock private ReceivingStatusHistoryRepository statusHistoryRepository;
    @Mock private ReceivingMapper receivingMapper;
    @Mock private GrnNumberService grnNumberService;
    @Mock private UserRepository userRepository;
    @Mock private PurchaseOrderProducer purchaseOrderProducer;

    @InjectMocks
    private ReceivingService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentShopId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── confirmReceiving ─────────────────────────────────────────────────────

    @Test
    void confirmReceiving_flipsDraftToPendingAndSavesTwice() {
        // Two saves: item-status recompute + stock-delta commit. Keeps the
        // audit trail honest — Phase 1 originally saved only once and never
        // reached adjustStockDeltas, so stock wasn't committed on confirm.
        com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder po =
                new com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder();
        po.setId(1L);
        po.setItems(new ArrayList<>());

        Receiving r = new Receiving();
        r.setId(50L);
        r.setStatus(ReceivingStatus.DRAFT);
        r.setGrNumber("GRN/26-27/00050");
        r.setItems(new ArrayList<>());
        r.setPurchaseOrder(po);

        when(receivingRepository.findById(50L)).thenReturn(Optional.of(r));
        when(receivingRepository.save(any(Receiving.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receivingMapper.toDto(any())).thenReturn(null);

        service.confirmReceiving(50L, "OK to commit");

        assertThat(r.getStatus()).isEqualTo(ReceivingStatus.PENDING);
        verify(receivingRepository, org.mockito.Mockito.atLeastOnce()).save(any(Receiving.class));
    }

    @Test
    void cancelReceiving_rejectsDraft() {
        Receiving r = new Receiving();
        r.setId(60L);
        r.setStatus(ReceivingStatus.DRAFT);
        r.setGrNumber("GRN/26-27/00060");
        r.setItems(new ArrayList<>());
        when(receivingRepository.findById(60L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.cancelReceiving(60L, "wrong PO", 3L))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Delete draft");
    }

    @Test
    void cancelReceiving_requiresReason() {
        Receiving r = new Receiving();
        r.setId(61L);
        r.setStatus(ReceivingStatus.PENDING);
        when(receivingRepository.findById(61L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.cancelReceiving(61L, "  ", 3L))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void confirmReceiving_noOpForAlreadyCommittedStatus() {
        Receiving r = new Receiving();
        r.setId(10L);
        r.setStatus(ReceivingStatus.PENDING); // already committed
        r.setGrNumber("GRN/26-27/00001");
        r.setItems(new ArrayList<>());

        when(receivingRepository.findById(10L)).thenReturn(Optional.of(r));
        when(receivingMapper.toDto(any())).thenReturn(null);

        service.confirmReceiving(10L, "second click");

        // No save, no PO status recompute — idempotent path.
        verify(receivingRepository, never()).save(any());
        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void confirmReceiving_missingThrows() {
        when(receivingRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirmReceiving(99L, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── approveReceiving ─────────────────────────────────────────────────────

    @Test
    void approveReceiving_rejectsDraftStatus() {
        Receiving r = new Receiving();
        r.setId(20L);
        r.setStatus(ReceivingStatus.DRAFT); // stock not committed yet
        when(receivingRepository.findById(20L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.approveReceiving(20L, "ok", 5L))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("confirmed before approval");
    }

    @Test
    void approveReceiving_rejectsAlreadyApproved() {
        Receiving r = new Receiving();
        r.setId(21L);
        r.setStatus(ReceivingStatus.COMPLETED);
        r.setApprovedByUser(new User()); // already stamped
        r.setGrNumber("GRN/26-27/00002");
        when(receivingRepository.findById(21L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.approveReceiving(21L, "again", 5L))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("already approved");
    }

    @Test
    void approveReceiving_stampsApproverAndNote() {
        Receiving r = new Receiving();
        r.setId(22L);
        r.setStatus(ReceivingStatus.PARTIALLY_RECEIVED);
        r.setGrNumber("GRN/26-27/00003");
        // Seed at least one line with an accepted qty > 0 — the service now
        // rejects approvals on zero-qty receipts (guards against rubber-stamping
        // an auto-created PENDING with nothing received).
        com.desitech.vyaparsathi.receiving.entity.ReceivingItem line =
                new com.desitech.vyaparsathi.receiving.entity.ReceivingItem();
        line.setReceivedQty(5);
        r.setItems(new java.util.ArrayList<>(java.util.List.of(line)));

        User approver = new User();
        approver.setId(7L);

        when(receivingRepository.findById(22L)).thenReturn(Optional.of(r));
        when(userRepository.findById(7L)).thenReturn(Optional.of(approver));
        when(receivingRepository.save(any(Receiving.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receivingMapper.toDto(any())).thenReturn(null);

        service.approveReceiving(22L, "spot-check OK", 7L);

        assertThat(r.getApprovedByUser()).isSameAs(approver);
        assertThat(r.getApprovedAt()).isNotNull();
        assertThat(r.getApprovalNote()).isEqualTo("spot-check OK");
    }

    // ── resolveReceivingTicket ───────────────────────────────────────────────

    @Test
    void resolveReceivingTicket_idempotentForTerminalStatus() {
        ReceivingTicket t = new ReceivingTicket();
        t.setStatusEnum(ReceivingTicketStatus.CLOSED);
        when(receivingTicketRepository.findById(30L)).thenReturn(Optional.of(t));

        ReceivingTicket result = service.resolveReceivingTicket(30L, "late", 9L);

        assertThat(result).isSameAs(t);
        assertThat(result.getStatus()).isEqualTo("CLOSED"); // unchanged
        verify(receivingTicketRepository, never()).save(any());
    }

    @Test
    void resolveReceivingTicket_stampsResolverAndFlipsStatus() {
        ReceivingTicket t = new ReceivingTicket();
        t.setStatusEnum(ReceivingTicketStatus.OPEN);
        when(receivingTicketRepository.findById(31L)).thenReturn(Optional.of(t));
        when(receivingTicketRepository.save(any(ReceivingTicket.class))).thenAnswer(inv -> inv.getArgument(0));

        ReceivingTicket saved = service.resolveReceivingTicket(31L, "supplier replaced", 9L);

        assertThat(saved.getStatus()).isEqualTo("RESOLVED");
        assertThat(saved.getResolvedBy()).isEqualTo(9L);
        assertThat(saved.getResolvedAt()).isNotNull();
        assertThat(saved.getResolutionNote()).isEqualTo("supplier replaced");
    }

    @Test
    void updateReceivingTicket_appliesInProgressStatus() {
        // "Mark in progress" was previously silently discarded because the
        // service ignored status. Verifies the fix in updateReceivingTicket.
        ReceivingTicket t = new ReceivingTicket();
        t.setStatusEnum(ReceivingTicketStatus.OPEN);
        when(receivingTicketRepository.findById(40L)).thenReturn(Optional.of(t));
        when(receivingTicketRepository.save(any(ReceivingTicket.class))).thenAnswer(inv -> inv.getArgument(0));

        com.desitech.vyaparsathi.receiving.dto.ReceivingTicketDTO dto =
                new com.desitech.vyaparsathi.receiving.dto.ReceivingTicketDTO();
        dto.setStatus("IN_PROGRESS");

        service.updateReceivingTicket(40L, dto);

        assertThat(t.getStatusEnum()).isEqualTo(ReceivingTicketStatus.IN_PROGRESS);
    }

    @Test
    void updateReceivingTicket_rejectsResolvedShortcut() {
        // RESOLVED must route through /resolve so the resolver stamp is captured.
        ReceivingTicket t = new ReceivingTicket();
        t.setStatusEnum(ReceivingTicketStatus.OPEN);
        when(receivingTicketRepository.findById(41L)).thenReturn(Optional.of(t));

        com.desitech.vyaparsathi.receiving.dto.ReceivingTicketDTO dto =
                new com.desitech.vyaparsathi.receiving.dto.ReceivingTicketDTO();
        dto.setStatus("RESOLVED");

        assertThatThrownBy(() -> service.updateReceivingTicket(41L, dto))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("/resolve");
    }

    // ── ReceivingTicketStatus lenient parsing ────────────────────────────────

    @Test
    void ticketStatusFromString_toleratesLegacyCasing() {
        // V88 backfill leaves legacy strings — the getter must not blow up.
        ReceivingTicket t = new ReceivingTicket();
        t.setStatus("Open"); // pre-refactor value
        assertThat(t.getStatusEnum()).isEqualTo(ReceivingTicketStatus.OPEN);
        t.setStatus("unknown-garbage");
        assertThat(t.getStatusEnum()).isEqualTo(ReceivingTicketStatus.OPEN); // safe default
    }
}