package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import com.desitech.vyaparsathi.receiving.dto.ThreeWayMatchDto;
import com.desitech.vyaparsathi.receiving.entity.ApInvoice;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.entity.ReceivingItem;
import com.desitech.vyaparsathi.receiving.repository.ApInvoiceRepository;
import com.desitech.vyaparsathi.receiving.repository.ReceivingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Computes the classic 3-way match: PO qty/price ↔ GRN accepted qty ↔ supplier
 * invoice qty/price. Emits per-line variance and a top-level status:
 * <ul>
 *   <li>MATCHED — everything within tolerance</li>
 *   <li>VARIANCE — at least one line outside tolerance</li>
 *   <li>NO_INVOICE — GRN has no matching AP invoice yet</li>
 * </ul>
 * Tolerance defaults to 2% cost variance; caller can pin a different tolerance
 * later via config. Read-only — the compute path never mutates state.
 */
@Service
public class ThreeWayMatchService {

    private static final BigDecimal COST_TOLERANCE_PCT = new BigDecimal("2.00");

    private final ReceivingRepository receivingRepository;
    private final ApInvoiceRepository apInvoiceRepository;

    public ThreeWayMatchService(ReceivingRepository receivingRepository,
                                ApInvoiceRepository apInvoiceRepository) {
        this.receivingRepository = receivingRepository;
        this.apInvoiceRepository = apInvoiceRepository;
    }

    @Transactional(readOnly = true)
    public ThreeWayMatchDto compute(Long receivingId) {
        Receiving r = receivingRepository.findById(receivingId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiving not found: " + receivingId));

        PurchaseOrder po = r.getPurchaseOrder();
        Optional<ApInvoice> maybeInvoice = apInvoiceRepository.findByReceivingId(receivingId);

        BigDecimal poTotal = BigDecimal.ZERO;
        BigDecimal grnTotal = BigDecimal.ZERO;
        BigDecimal invoiceTotal = maybeInvoice.map(ApInvoice::getTotalAmount).orElse(BigDecimal.ZERO);

        List<ThreeWayMatchDto.LineVariance> lineVariances = new ArrayList<>();
        boolean anyOutOfTolerance = false;

        if (r.getItems() != null) {
            for (ReceivingItem it : r.getItems()) {
                PurchaseOrderItem poItem = it.getPurchaseOrderItem();
                if (poItem == null) continue;

                int poQty = poItem.getQuantity() != null ? poItem.getQuantity() : 0;
                int acceptedQty = it.getAcceptedQty();
                BigDecimal poCost = Optional.ofNullable(poItem.getUnitCost()).orElse(BigDecimal.ZERO);
                BigDecimal grnCost = Optional.ofNullable(it.getUnitCost()).orElse(poCost);

                poTotal = poTotal.add(poCost.multiply(BigDecimal.valueOf(poQty)));
                grnTotal = grnTotal.add(grnCost.multiply(BigDecimal.valueOf(acceptedQty)));

                BigDecimal variancePct = BigDecimal.ZERO;
                if (poCost.signum() > 0) {
                    variancePct = grnCost.subtract(poCost)
                            .divide(poCost, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
                }
                boolean within = variancePct.abs().compareTo(COST_TOLERANCE_PCT) <= 0;
                if (!within) anyOutOfTolerance = true;

                ThreeWayMatchDto.LineVariance lv = new ThreeWayMatchDto.LineVariance();
                lv.purchaseOrderItemId = poItem.getId();
                lv.itemName = poItem.getItemVariant() != null && poItem.getItemVariant().getItem() != null
                        ? poItem.getItemVariant().getItem().getName()
                        : "Item";
                lv.poQty = poQty;
                lv.grnAcceptedQty = acceptedQty;
                lv.invoiceQty = null; // per-line invoice matching is future work; today we surface totals only
                lv.poUnitCost = poCost;
                lv.grnUnitCost = grnCost;
                lv.invoiceUnitCost = null;
                lv.costVariancePct = variancePct.setScale(2, RoundingMode.HALF_UP);
                lv.withinTolerance = within;
                lineVariances.add(lv);
            }
        }

        BigDecimal totalVariance = grnTotal.subtract(invoiceTotal).setScale(2, RoundingMode.HALF_UP);

        String status;
        if (maybeInvoice.isEmpty()) {
            status = "NO_INVOICE";
        } else if (anyOutOfTolerance || totalVariance.abs().compareTo(new BigDecimal("1")) > 0) {
            status = "VARIANCE";
        } else {
            status = "MATCHED";
        }

        ThreeWayMatchDto dto = new ThreeWayMatchDto();
        dto.setMatchStatus(status);
        dto.setPoTotal(poTotal.setScale(2, RoundingMode.HALF_UP));
        dto.setGrnTotal(grnTotal.setScale(2, RoundingMode.HALF_UP));
        dto.setInvoiceTotal(invoiceTotal.setScale(2, RoundingMode.HALF_UP));
        dto.setTotalVariance(totalVariance);
        dto.setLineVariances(lineVariances);
        return dto;
    }
}
