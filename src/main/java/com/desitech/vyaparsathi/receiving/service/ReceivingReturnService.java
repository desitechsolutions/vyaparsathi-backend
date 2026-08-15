package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.purchasereturn.dto.CreatePurchaseReturnDto;
import com.desitech.vyaparsathi.purchasereturn.dto.CreatePurchaseReturnItemDto;
import com.desitech.vyaparsathi.purchasereturn.dto.PurchaseReturnDto;
import com.desitech.vyaparsathi.purchasereturn.service.PurchaseReturnService;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.entity.ReceivingItem;
import com.desitech.vyaparsathi.receiving.repository.ReceivingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Seeds a Return-to-Vendor (RTV) draft from a GRN. Pulls every damaged +
 * rejected quantity across the GRN's lines into the return, so the operator
 * only reviews reasons and clicks submit. Delegates the actual persistence to
 * the existing {@link PurchaseReturnService} — the return then flows through
 * its normal DRAFT → APPROVED → SHIPPED lifecycle.
 */
@Service
public class ReceivingReturnService {

    private static final Logger log = LoggerFactory.getLogger(ReceivingReturnService.class);

    private final ReceivingRepository receivingRepository;
    private final PurchaseReturnService purchaseReturnService;

    public ReceivingReturnService(ReceivingRepository receivingRepository,
                                  PurchaseReturnService purchaseReturnService) {
        this.receivingRepository = receivingRepository;
        this.purchaseReturnService = purchaseReturnService;
    }

    @Transactional
    public PurchaseReturnDto createReturnFromReceiving(Long receivingId, String notesOverride) {
        Receiving r = receivingRepository.findById(receivingId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiving not found: " + receivingId));
        if (r.getPurchaseOrder() == null || r.getPurchaseOrder().getSupplier() == null) {
            throw new BusinessValidationException("Receiving is not linked to a supplier — cannot create RTV.");
        }

        List<CreatePurchaseReturnItemDto> lines = new ArrayList<>();
        if (r.getItems() != null) {
            for (ReceivingItem it : r.getItems()) {
                int damaged = Optional.ofNullable(it.getDamagedQty()).orElse(0);
                int rejected = Optional.ofNullable(it.getRejectedQty()).orElse(0);
                int qty = damaged + rejected;
                if (qty == 0) continue;
                if (it.getPurchaseOrderItem() == null || it.getPurchaseOrderItem().getItemVariant() == null) continue;
                CreatePurchaseReturnItemDto line = new CreatePurchaseReturnItemDto();
                line.setItemVariantId(it.getPurchaseOrderItem().getItemVariant().getId());
                line.setBatchNumber(it.getBatchNumber());
                line.setQuantity(qty);
                BigDecimal cost = it.getUnitCost() != null
                        ? it.getUnitCost()
                        : Optional.ofNullable(it.getPurchaseOrderItem().getUnitCost()).orElse(BigDecimal.ZERO);
                line.setUnitCost(cost);
                StringBuilder reason = new StringBuilder();
                if (damaged > 0) reason.append("Damaged: ").append(Optional.ofNullable(it.getDamageReason()).orElse(damaged + " units"));
                if (rejected > 0) {
                    if (reason.length() > 0) reason.append(" | ");
                    reason.append("Rejected: ").append(Optional.ofNullable(it.getRejectReason()).orElse(rejected + " units"));
                }
                line.setReason(reason.toString());
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            throw new BusinessValidationException(
                    "GRN has no damaged or rejected units — nothing to return.");
        }

        CreatePurchaseReturnDto dto = new CreatePurchaseReturnDto();
        dto.setSupplierId(r.getPurchaseOrder().getSupplier().getId());
        dto.setPurchaseOrderId(r.getPurchaseOrder().getId());
        dto.setReceivingId(r.getId());
        dto.setReturnDate(LocalDateTime.now());
        dto.setNotes(notesOverride != null && !notesOverride.isBlank()
                ? notesOverride
                : "Return-to-vendor from GRN " + r.getGrNumber());
        dto.setItems(lines);
        PurchaseReturnDto created = purchaseReturnService.createPurchaseReturn(dto);
        log.info("Created RTV {} from GRN {} — {} line(s)", created.getReturnNo(), r.getGrNumber(), lines.size());
        return created;
    }
}
