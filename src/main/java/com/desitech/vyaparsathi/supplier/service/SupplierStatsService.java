package com.desitech.vyaparsathi.supplier.service;

import com.desitech.vyaparsathi.accounting.entity.DebitNote;
import com.desitech.vyaparsathi.accounting.repository.DebitNoteRepository;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import com.desitech.vyaparsathi.purchasereturn.entity.PurchaseReturn;
import com.desitech.vyaparsathi.purchasereturn.repository.PurchaseReturnRepository;
import com.desitech.vyaparsathi.receiving.repository.ReceivingRepository;
import com.desitech.vyaparsathi.supplier.dto.SupplierStatsDto;
import com.desitech.vyaparsathi.supplier.service.SupplierLedgerService;
import com.desitech.vyaparsathi.supplier.dto.SupplierLedgerDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Aggregate stats for the enterprise Supplier detail page. Reads from
 * existing repositories rather than pre-materialising — one supplier's
 * numbers are small enough that on-demand computation is fine.
 */
@Service
public class SupplierStatsService {

    private static final java.util.Set<PurchaseOrderStatus> OPEN_PO_STATUSES = java.util.EnumSet.of(
            PurchaseOrderStatus.DRAFT,
            PurchaseOrderStatus.SUBMITTED,
            PurchaseOrderStatus.PARTIALLY_RECEIVED);

    private final PurchaseOrderRepository poRepo;
    private final PurchaseReturnRepository prRepo;
    private final DebitNoteRepository dnRepo;
    private final ReceivingRepository receivingRepo;
    private final SupplierLedgerService ledgerService;

    public SupplierStatsService(PurchaseOrderRepository poRepo,
                                PurchaseReturnRepository prRepo,
                                DebitNoteRepository dnRepo,
                                ReceivingRepository receivingRepo,
                                SupplierLedgerService ledgerService) {
        this.poRepo = poRepo;
        this.prRepo = prRepo;
        this.dnRepo = dnRepo;
        this.receivingRepo = receivingRepo;
        this.ledgerService = ledgerService;
    }

    @Transactional(readOnly = true)
    public SupplierStatsDto compute(Long supplierId) {
        SupplierStatsDto stats = new SupplierStatsDto();
        stats.setSupplierId(supplierId);
        Long shopId = TenantUtils.getCurrentShopId();
        if (shopId == null) return stats;

        // Purchase Orders
        List<PurchaseOrder> pos = poRepo.findBySupplierIdAndShopId(supplierId, shopId);
        stats.setTotalPurchaseOrders(pos.size());
        long openCount = 0;
        BigDecimal totalPoValue = BigDecimal.ZERO;
        LocalDate lastDate = null;
        for (PurchaseOrder po : pos) {
            if (po.getStatus() != null && OPEN_PO_STATUSES.contains(po.getStatus())) {
                openCount++;
            }
            if (po.getTotalAmount() != null) totalPoValue = totalPoValue.add(po.getTotalAmount());
            if (po.getOrderDate() != null) {
                LocalDate d = po.getOrderDate().toLocalDate();
                if (lastDate == null || d.isAfter(lastDate)) lastDate = d;
            }
        }
        stats.setOpenPurchaseOrders(openCount);
        stats.setTotalPoValue(totalPoValue);

        // GRNs — counted directly via receivingRepo for the supplier and shop
        long grnCount = receivingRepo.countBySupplierIdAndShopId(supplierId, shopId);
        stats.setTotalGrns(grnCount);

        // Purchase Returns
        Page<PurchaseReturn> prsPage = prRepo.findBySupplierIdAndShopId(
                supplierId, shopId, PageRequest.of(0, 1000));
        List<PurchaseReturn> prs = prsPage.getContent();
        stats.setTotalPurchaseReturns(prs.size());
        BigDecimal returnValue = BigDecimal.ZERO;
        for (PurchaseReturn pr : prs) {
            if (pr.getTotalAmount() != null) returnValue = returnValue.add(pr.getTotalAmount());
            if (pr.getReturnDate() != null) {
                LocalDate d = pr.getReturnDate().toLocalDate();
                if (lastDate == null || d.isAfter(lastDate)) lastDate = d;
            }
        }
        stats.setTotalReturnValue(returnValue);

        // Debit Notes — via all-DNs-for-supplier walk (small N in practice).
        // If the module grows large, add a targeted supplierId aggregation query.
        List<DebitNote> allDns = dnRepo.findAll().stream()
                .filter(dn -> dn.getSupplier() != null
                        && supplierId.equals(dn.getSupplier().getId()))
                .toList();
        stats.setTotalDebitNotes(allDns.size());
        BigDecimal dnTotal = BigDecimal.ZERO;
        BigDecimal dnApplied = BigDecimal.ZERO;
        for (DebitNote dn : allDns) {
            if (dn.getTotalAmount() != null) dnTotal = dnTotal.add(dn.getTotalAmount());
            if (dn.getAppliedAmount() != null) dnApplied = dnApplied.add(dn.getAppliedAmount());
            if (dn.getDebitNoteDate() != null) {
                LocalDate d = dn.getDebitNoteDate();
                if (lastDate == null || d.isAfter(lastDate)) lastDate = d;
            }
        }
        stats.setTotalDebitNoteValue(dnTotal);
        stats.setAppliedDebitNoteValue(dnApplied);

        // Outstanding payable — latest ledger row's running_balance is the
        // authoritative supplier-side balance (payable when positive).
        try {
            Page<SupplierLedgerDto> latest = ledgerService.getSupplierLedger(
                    supplierId, PageRequest.of(0, 1));
            if (!latest.isEmpty()) {
                BigDecimal running = latest.getContent().get(0).getRunningBalance();
                stats.setOutstandingPayable(running == null ? BigDecimal.ZERO : running);
            }
        } catch (RuntimeException ignore) {
            // Ledger absent / not populated — leave zero.
        }

        stats.setLastTransactionDate(lastDate);
        return stats;
    }
}
