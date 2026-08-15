package com.desitech.vyaparsathi.document.mapper;

import com.desitech.vyaparsathi.common.enums.DocumentType;
import com.desitech.vyaparsathi.document.dto.DocumentAuditDto;
import com.desitech.vyaparsathi.document.dto.DocumentReferenceDto;
import com.desitech.vyaparsathi.document.dto.EnterpriseDocumentDto;
import com.desitech.vyaparsathi.document.dto.LineItemDto;
import com.desitech.vyaparsathi.document.dto.TotalsDto;
import com.desitech.vyaparsathi.document.service.DocumentIntegrityHashService;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.entity.ReceivingItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Maps {@link Receiving} → {@link EnterpriseDocumentDto}. */
@Component
public class ReceivingDocumentMapper {

    private final PartyMapper partyMapper;
    private final DocumentIntegrityHashService hashService;

    public ReceivingDocumentMapper(PartyMapper partyMapper, DocumentIntegrityHashService hashService) {
        this.partyMapper = partyMapper;
        this.hashService = hashService;
    }

    public EnterpriseDocumentDto map(Receiving r) {
        EnterpriseDocumentDto d = new EnterpriseDocumentDto();

        d.setDocumentType(DocumentType.GOODS_RECEIPT_NOTE);
        d.setDocumentId(r.getId());
        d.setDocumentNumber(r.getGrNumber() != null ? r.getGrNumber() : "GRN-" + r.getId());
        d.setDocumentDate(r.getCreatedAt() != null ? r.getCreatedAt().toLocalDate() : null);
        d.setStatus(r.getStatus() != null ? r.getStatus().name() : null);
        if (r.getStatus() != null && r.getStatus().name().equals("DRAFT")) d.setWatermark("DRAFT");
        if (r.getStatus() != null && r.getStatus().name().contains("CANCEL")) d.setWatermark("CANCELLED");

        // Parties: issuer = shop, counterparty = supplier from PO
        d.setIssuer(partyMapper.fromShop(r.getShop()));
        PurchaseOrder po = r.getPurchaseOrder();
        if (po != null && po.getSupplier() != null) {
            d.setCounterparty(partyMapper.fromSupplier(po.getSupplier()));
        }

        // Cross-references
        if (po != null) {
            String poNo = po.getPoNumber();
            d.getLinkedDocuments().add(new DocumentReferenceDto("PURCHASE_ORDER",
                    po.getId(), poNo != null ? poNo : ("PO-" + po.getId()),
                    po.getOrderDate() != null ? po.getOrderDate().toLocalDate() : null));
        }
        if (r.getSupplierInvoiceNo() != null && !r.getSupplierInvoiceNo().isBlank()) {
            d.getLinkedDocuments().add(new DocumentReferenceDto("SUPPLIER_INVOICE",
                    null, r.getSupplierInvoiceNo(), r.getSupplierInvoiceDate()));
        }

        // Extra header metadata specific to GRN
        if (r.getSupplierInvoiceNo() != null) d.getExtraMetadata().put("Supplier Inv", r.getSupplierInvoiceNo());
        if (r.getDeliveryChallanNo() != null) d.getExtraMetadata().put("Challan", r.getDeliveryChallanNo());
        if (r.getVehicleNo() != null) d.getExtraMetadata().put("Vehicle", r.getVehicleNo());
        if (r.getDockBay() != null) d.getExtraMetadata().put("Dock", r.getDockBay());

        // Items
        int lineNo = 1;
        BigDecimal cost = BigDecimal.ZERO;
        for (ReceivingItem it : r.getItems()) {
            LineItemDto ln = new LineItemDto();
            ln.setLineNo(lineNo++);
            PurchaseOrderItem poi = it.getPurchaseOrderItem();
            ItemVariant iv = poi != null ? poi.getItemVariant() : null;
            ln.setDescription(iv != null && iv.getItem() != null ? iv.getItem().getName() : "Item");
            ln.setItemCode(iv != null ? iv.getSku() : null);
            ln.setHsnSac(iv != null ? iv.getHsn() : null);
            ln.setUom(iv != null ? iv.getUnit() : null);
            ln.setOrderedQty(bd(it.getExpectedQty()));
            ln.setReceivedQty(bd(it.getReceivedQty()));
            ln.setDamagedQty(bd(it.getDamagedQty()));
            ln.setRejectedQty(bd(it.getRejectedQty()));
            ln.setAcceptedQty(bd(it.getAcceptedQty()));
            ln.setUnitPrice(it.getUnitCost());
            ln.setBatchNumber(it.getBatchNumber());
            BigDecimal lineCost = nz(it.getUnitCost()).multiply(bd(it.getAcceptedQty()));
            ln.setLineTotal(lineCost);
            cost = cost.add(lineCost);
            d.getItems().add(ln);
        }

        // Totals — GRN shows landed cost totals
        TotalsDto t = new TotalsDto();
        t.setSubtotal(cost);
        t.setGrandTotal(cost);
        if (r.getFreightActual() != null) t.setFreight(r.getFreightActual());
        d.setTotals(t);

        // Notes
        d.setNotes(r.getApprovalNote());

        // Brand + audit
        d.setBrandColorHex(r.getShop() != null ? r.getShop().getBrandColor() : null);
        DocumentAuditDto audit = new DocumentAuditDto();
        audit.setCreatedAt(r.getCreatedAt());
        audit.setApprovedAt(r.getApprovedAt());
        audit.setApprovedBy(r.getApprovedByUser() != null ? r.getApprovedByUser().getUsername() : null);
        audit.setDocumentHash(hashService.hash(d));
        d.setAudit(audit);
        return d;
    }

    private BigDecimal bd(Integer v) { return v == null ? BigDecimal.ZERO : BigDecimal.valueOf(v); }
    private BigDecimal nz(BigDecimal b) { return b == null ? BigDecimal.ZERO : b; }
}
