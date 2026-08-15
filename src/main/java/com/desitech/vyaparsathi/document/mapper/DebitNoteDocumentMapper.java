package com.desitech.vyaparsathi.document.mapper;

import com.desitech.vyaparsathi.accounting.entity.DebitNote;
import com.desitech.vyaparsathi.accounting.entity.DebitNoteItem;
import com.desitech.vyaparsathi.common.enums.DocumentType;
import com.desitech.vyaparsathi.common.util.AmountInWordsIndian;
import com.desitech.vyaparsathi.document.dto.DocumentAuditDto;
import com.desitech.vyaparsathi.document.dto.DocumentReferenceDto;
import com.desitech.vyaparsathi.document.dto.EnterpriseDocumentDto;
import com.desitech.vyaparsathi.document.dto.LineItemDto;
import com.desitech.vyaparsathi.document.dto.TotalsDto;
import com.desitech.vyaparsathi.document.service.DocumentIntegrityHashService;
import com.desitech.vyaparsathi.document.service.HsnSummaryComputer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Maps {@link DebitNote} → {@link EnterpriseDocumentDto}. */
@Component
public class DebitNoteDocumentMapper {

    private final PartyMapper partyMapper;
    private final HsnSummaryComputer hsnComputer;
    private final DocumentIntegrityHashService hashService;

    public DebitNoteDocumentMapper(PartyMapper partyMapper,
                                   HsnSummaryComputer hsnComputer,
                                   DocumentIntegrityHashService hashService) {
        this.partyMapper = partyMapper;
        this.hsnComputer = hsnComputer;
        this.hashService = hashService;
    }

    public EnterpriseDocumentDto map(DebitNote dn) {
        EnterpriseDocumentDto d = new EnterpriseDocumentDto();
        d.setDocumentType(DocumentType.DEBIT_NOTE);
        d.setDocumentId(dn.getId());
        d.setDocumentNumber(dn.getDebitNoteNo());
        d.setDocumentDate(dn.getDebitNoteDate());
        d.setStatus(dn.getStatus() != null ? dn.getStatus().name() : null);
        if (dn.getStatus() != null && dn.getStatus().name().contains("CANCEL")) d.setWatermark("CANCELLED");

        d.setIssuer(partyMapper.fromShop(dn.getShop()));
        d.setCounterparty(partyMapper.fromSupplier(dn.getSupplier()));

        // Reference to originating invoice / PR
        if (dn.getPurchaseInvoice() != null) {
            d.getLinkedDocuments().add(new DocumentReferenceDto("PURCHASE_INVOICE",
                    dn.getPurchaseInvoice().getId(),
                    "PI-" + dn.getPurchaseInvoice().getId(), null));
        }
        if (dn.getPurchaseReturn() != null) {
            d.getLinkedDocuments().add(new DocumentReferenceDto("PURCHASE_RETURN",
                    dn.getPurchaseReturn().getId(),
                    dn.getPurchaseReturn().getReturnNo(),
                    dn.getPurchaseReturn().getReturnDate() != null
                            ? dn.getPurchaseReturn().getReturnDate().toLocalDate() : null));
        }

        int lineNo = 1;
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxable = BigDecimal.ZERO;
        for (DebitNoteItem it : dn.getItems()) {
            LineItemDto ln = new LineItemDto();
            ln.setLineNo(lineNo++);
            ln.setDescription(it.getItemName());
            ln.setHsnSac(it.getHsnSac());
            ln.setBatchNumber(it.getBatchNumber());
            ln.setQuantity(it.getQty());
            ln.setUnitPrice(it.getUnitCost());
            ln.setTaxableValue(it.getTaxableValue());
            ln.setCgstAmount(it.getCgstAmt());
            ln.setSgstAmount(it.getSgstAmt());
            ln.setIgstAmount(it.getIgstAmt());
            ln.setLineTotal(it.getTotalAmount());
            d.getItems().add(ln);
            subtotal = subtotal.add(nz(it.getQty()).multiply(nz(it.getUnitCost())));
            taxable  = taxable.add(nz(it.getTaxableValue()));
        }

        TotalsDto t = new TotalsDto();
        t.setSubtotal(subtotal);
        t.setTotalTaxable(nz(dn.getTaxableAmount()));
        t.setCgstAmount(nz(dn.getCgstAmount()));
        t.setSgstAmount(nz(dn.getSgstAmount()));
        t.setIgstAmount(nz(dn.getIgstAmount()));
        t.setGrandTotal(nz(dn.getTotalAmount()));
        t.setPaidAmount(nz(dn.getAppliedAmount()));
        t.setOutstandingAmount(nz(dn.getTotalAmount()).subtract(nz(dn.getAppliedAmount())));
        t.setAmountInWords(AmountInWordsIndian.toWords(nz(dn.getTotalAmount())));
        d.setTotals(t);

        hsnComputer.populate(d);

        d.setNotes(dn.getReason() != null ? "Reason: " + dn.getReason()
                + (dn.getNotes() != null ? "\n\n" + dn.getNotes() : "") : dn.getNotes());
        d.setBrandColorHex(dn.getShop() != null ? dn.getShop().getBrandColor() : null);

        DocumentAuditDto audit = new DocumentAuditDto();
        audit.setDocumentHash(hashService.hash(d));
        d.setAudit(audit);
        return d;
    }

    private BigDecimal nz(BigDecimal b) { return b == null ? BigDecimal.ZERO : b; }
}
