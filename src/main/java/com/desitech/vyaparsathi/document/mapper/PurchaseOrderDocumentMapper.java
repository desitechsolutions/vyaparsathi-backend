package com.desitech.vyaparsathi.document.mapper;

import com.desitech.vyaparsathi.common.enums.DocumentType;
import com.desitech.vyaparsathi.common.enums.SupplyType;
import com.desitech.vyaparsathi.common.util.AmountInWordsIndian;
import com.desitech.vyaparsathi.document.dto.DocumentAuditDto;
import com.desitech.vyaparsathi.document.dto.EnterpriseDocumentDto;
import com.desitech.vyaparsathi.document.dto.LineItemDto;
import com.desitech.vyaparsathi.document.dto.TotalsDto;
import com.desitech.vyaparsathi.document.service.DocumentIntegrityHashService;
import com.desitech.vyaparsathi.document.service.HsnSummaryComputer;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Maps {@link PurchaseOrder} → {@link EnterpriseDocumentDto}. */
@Component
public class PurchaseOrderDocumentMapper {

    private final PartyMapper partyMapper;
    private final HsnSummaryComputer hsnComputer;
    private final DocumentIntegrityHashService hashService;

    public PurchaseOrderDocumentMapper(PartyMapper partyMapper,
                                       HsnSummaryComputer hsnComputer,
                                       DocumentIntegrityHashService hashService) {
        this.partyMapper = partyMapper;
        this.hsnComputer = hsnComputer;
        this.hashService = hashService;
    }

    public EnterpriseDocumentDto map(PurchaseOrder po) {
        EnterpriseDocumentDto d = new EnterpriseDocumentDto();
        d.setDocumentType(DocumentType.PURCHASE_ORDER);
        d.setDocumentId(po.getId());
        d.setDocumentNumber(po.getPoNumber());
        d.setDocumentDate(po.getOrderDate() != null ? po.getOrderDate().toLocalDate() : null);
        d.setStatus(po.getStatus() != null ? po.getStatus().name() : null);
        if (po.getStatus() != null && po.getStatus().name().equals("DRAFT")) d.setWatermark("DRAFT");
        if (po.getStatus() != null && po.getStatus().name().contains("CANCEL")) d.setWatermark("CANCELLED");

        d.setIssuer(partyMapper.fromShop(po.getShop()));
        d.setCounterparty(partyMapper.fromSupplier(po.getSupplier()));

        // V99 statutory fields — feed the PDF renderer so the same
        // presentation the SaleDocumentMapper produces (PoS, RCM, supply
        // type, address snapshots) also lands on outward-facing POs.
        d.setPlaceOfSupplyState(po.getPlaceOfSupplyState());
        d.setPlaceOfSupplyStateCode(po.getPlaceOfSupplyStateCode());
        d.setSupplyType(SupplyType.fromString(po.getSupplyType()));
        d.setReverseCharge(Boolean.TRUE.equals(po.getReverseCharge()));
        d.setBillToAddress(po.getBillToPartySnapshot());
        d.setShipToAddress(po.getShipToPartySnapshot());

        if (po.getExpectedDeliveryDate() != null)
            d.setExpectedDeliveryDate(po.getExpectedDeliveryDate().toLocalDate());

        int lineNo = 1;
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discSum = BigDecimal.ZERO;
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal cgstSum = BigDecimal.ZERO;
        BigDecimal sgstSum = BigDecimal.ZERO;
        BigDecimal igstSum = BigDecimal.ZERO;
        for (PurchaseOrderItem it : po.getItems()) {
            LineItemDto ln = new LineItemDto();
            ln.setLineNo(lineNo++);
            ItemVariant iv = it.getItemVariant();
            ln.setDescription(iv != null && iv.getItem() != null ? iv.getItem().getName() : "Item");
            ln.setItemCode(iv != null ? iv.getSku() : null);
            ln.setHsnSac(it.getHsnCode() != null ? it.getHsnCode() : (iv != null ? iv.getHsn() : null));
            ln.setUom(iv != null ? iv.getUnit() : null);
            ln.setQuantity(BigDecimal.valueOf(it.getQuantity() != null ? it.getQuantity() : 0));
            ln.setUnitPrice(it.getUnitCost());
            ln.setDiscountAmount(it.getDiscount());
            ln.setTaxableValue(it.getTaxableValue());
            ln.setCgstAmount(it.getCgstAmt());
            ln.setSgstAmount(it.getSgstAmt());
            ln.setIgstAmount(it.getIgstAmt());
            if (it.getGstRate() != null) {
                ln.setTaxRatePct(BigDecimal.valueOf(it.getGstRate()));
                ln.setCgstRatePct(BigDecimal.valueOf(it.getGstRate() / 2.0));
                ln.setSgstRatePct(BigDecimal.valueOf(it.getGstRate() / 2.0));
                ln.setIgstRatePct(BigDecimal.valueOf(it.getGstRate()));
            }
            ln.setLineTotal(it.getLineTotal());
            d.getItems().add(ln);
            subtotal = subtotal.add(BigDecimal.valueOf(it.getQuantity() != null ? it.getQuantity() : 0)
                    .multiply(nz(it.getUnitCost())));
            discSum  = discSum.add(nz(it.getDiscount()));
            taxable  = taxable.add(nz(it.getTaxableValue()));
            cgstSum  = cgstSum.add(nz(it.getCgstAmt()));
            sgstSum  = sgstSum.add(nz(it.getSgstAmt()));
            igstSum  = igstSum.add(nz(it.getIgstAmt()));
        }

        TotalsDto t = new TotalsDto();
        t.setSubtotal(subtotal);
        t.setTotalDiscount(discSum);
        t.setTotalTaxable(taxable);
        t.setCgstAmount(cgstSum);
        t.setSgstAmount(sgstSum);
        t.setIgstAmount(igstSum);
        t.setGrandTotal(nz(po.getTotalAmount()));
        t.setAmountInWords(AmountInWordsIndian.toWords(nz(po.getTotalAmount())));
        d.setTotals(t);

        hsnComputer.populate(d);

        d.setNotes(po.getNotes());
        d.setBrandColorHex(po.getShop() != null ? po.getShop().getBrandColor() : null);

        DocumentAuditDto audit = new DocumentAuditDto();
        audit.setCreatedAt(po.getOrderDate());
        audit.setApprovedAt(po.getApprovedAt());
        audit.setDocumentHash(hashService.hash(d));
        d.setAudit(audit);
        return d;
    }

    private BigDecimal nz(BigDecimal b) { return b == null ? BigDecimal.ZERO : b; }
}
