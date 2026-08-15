package com.desitech.vyaparsathi.document.mapper;

import com.desitech.vyaparsathi.common.enums.DocumentType;
import com.desitech.vyaparsathi.common.util.AmountInWordsIndian;
import com.desitech.vyaparsathi.document.dto.DocumentAuditDto;
import com.desitech.vyaparsathi.document.dto.DocumentReferenceDto;
import com.desitech.vyaparsathi.document.dto.EnterpriseDocumentDto;
import com.desitech.vyaparsathi.document.dto.LineItemDto;
import com.desitech.vyaparsathi.document.dto.TotalsDto;
import com.desitech.vyaparsathi.document.service.DocumentIntegrityHashService;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.purchasereturn.entity.PurchaseReturn;
import com.desitech.vyaparsathi.purchasereturn.entity.PurchaseReturnItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Maps {@link PurchaseReturn} → {@link EnterpriseDocumentDto}. */
@Component
public class PurchaseReturnDocumentMapper {

    private final PartyMapper partyMapper;
    private final DocumentIntegrityHashService hashService;

    public PurchaseReturnDocumentMapper(PartyMapper partyMapper, DocumentIntegrityHashService hashService) {
        this.partyMapper = partyMapper;
        this.hashService = hashService;
    }

    public EnterpriseDocumentDto map(PurchaseReturn pr) {
        EnterpriseDocumentDto d = new EnterpriseDocumentDto();
        d.setDocumentType(DocumentType.PURCHASE_RETURN);
        d.setDocumentId(pr.getId());
        d.setDocumentNumber(pr.getReturnNo());
        d.setDocumentDate(pr.getReturnDate() != null ? pr.getReturnDate().toLocalDate() : null);
        d.setStatus(pr.getStatus() != null ? pr.getStatus().name() : null);
        if (pr.getStatus() != null && pr.getStatus().name().equals("DRAFT")) d.setWatermark("DRAFT");
        if (pr.getStatus() != null && pr.getStatus().name().contains("CANCEL")) d.setWatermark("CANCELLED");

        d.setIssuer(partyMapper.fromShop(pr.getShop()));
        d.setCounterparty(partyMapper.fromSupplier(pr.getSupplier()));

        // Cross-references — PO and GRN behind this return
        if (pr.getPurchaseOrder() != null) {
            d.getLinkedDocuments().add(new DocumentReferenceDto("PURCHASE_ORDER",
                    pr.getPurchaseOrder().getId(),
                    pr.getPurchaseOrder().getPoNumber() != null
                            ? pr.getPurchaseOrder().getPoNumber() : ("PO-" + pr.getPurchaseOrder().getId()),
                    pr.getPurchaseOrder().getOrderDate() != null
                            ? pr.getPurchaseOrder().getOrderDate().toLocalDate() : null));
        }
        if (pr.getReceiving() != null) {
            d.getLinkedDocuments().add(new DocumentReferenceDto("GOODS_RECEIPT_NOTE",
                    pr.getReceiving().getId(),
                    pr.getReceiving().getGrNumber() != null
                            ? pr.getReceiving().getGrNumber() : ("GRN-" + pr.getReceiving().getId()),
                    pr.getReceiving().getCreatedAt() != null
                            ? pr.getReceiving().getCreatedAt().toLocalDate() : null));
        }

        // Items
        int lineNo = 1;
        BigDecimal subtotal = BigDecimal.ZERO;
        for (PurchaseReturnItem it : pr.getItems()) {
            LineItemDto ln = new LineItemDto();
            ln.setLineNo(lineNo++);
            ItemVariant iv = it.getItemVariant();
            ln.setDescription(iv != null && iv.getItem() != null ? iv.getItem().getName() : "Item");
            ln.setItemCode(iv != null ? iv.getSku() : null);
            ln.setHsnSac(iv != null ? iv.getHsn() : null);
            ln.setUom(iv != null ? iv.getUnit() : null);
            ln.setQuantity(BigDecimal.valueOf(it.getQuantity() != null ? it.getQuantity() : 0));
            ln.setUnitPrice(it.getUnitCost());
            ln.setTaxableValue(it.getTotalCost());
            ln.setLineTotal(it.getTotalCost());
            ln.setBatchNumber(it.getBatchNumber());
            ln.setReason(it.getReason());
            subtotal = subtotal.add(nz(it.getTotalCost()));
            d.getItems().add(ln);
        }

        TotalsDto t = new TotalsDto();
        t.setSubtotal(subtotal);
        t.setTotalTaxable(subtotal);
        t.setGrandTotal(nz(pr.getTotalAmount()));
        t.setAmountInWords(AmountInWordsIndian.toWords(nz(pr.getTotalAmount())));
        d.setTotals(t);

        d.setNotes(pr.getNotes());
        d.setBrandColorHex(pr.getShop() != null ? pr.getShop().getBrandColor() : null);

        DocumentAuditDto audit = new DocumentAuditDto();
        audit.setCreatedAt(pr.getReturnDate());
        audit.setApprovedAt(pr.getApprovedAt());
        audit.setDocumentHash(hashService.hash(d));
        d.setAudit(audit);
        return d;
    }

    private BigDecimal nz(BigDecimal b) { return b == null ? BigDecimal.ZERO : b; }
}
