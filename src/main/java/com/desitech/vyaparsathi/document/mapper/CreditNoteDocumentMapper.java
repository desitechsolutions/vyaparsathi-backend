package com.desitech.vyaparsathi.document.mapper;

import com.desitech.vyaparsathi.accounting.entity.CreditNote;
import com.desitech.vyaparsathi.accounting.entity.CreditNoteItem;
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

/** Maps {@link CreditNote} → {@link EnterpriseDocumentDto}. */
@Component
public class CreditNoteDocumentMapper {

    private final PartyMapper partyMapper;
    private final HsnSummaryComputer hsnComputer;
    private final DocumentIntegrityHashService hashService;

    public CreditNoteDocumentMapper(PartyMapper partyMapper,
                                    HsnSummaryComputer hsnComputer,
                                    DocumentIntegrityHashService hashService) {
        this.partyMapper = partyMapper;
        this.hsnComputer = hsnComputer;
        this.hashService = hashService;
    }

    public EnterpriseDocumentDto map(CreditNote cn) {
        EnterpriseDocumentDto d = new EnterpriseDocumentDto();
        d.setDocumentType(DocumentType.CREDIT_NOTE);
        d.setDocumentId(cn.getId());
        d.setDocumentNumber(cn.getCreditNoteNo());
        d.setDocumentDate(cn.getCreditNoteDate());
        d.setIssuer(partyMapper.fromShop(cn.getShop()));
        d.setCounterparty(partyMapper.fromCustomer(cn.getCustomer()));

        if (cn.getSale() != null) {
            d.getLinkedDocuments().add(new DocumentReferenceDto("TAX_INVOICE",
                    cn.getSale().getId(), cn.getSale().getInvoiceNo(),
                    cn.getSale().getDate() != null ? cn.getSale().getDate().toLocalDate() : null));
        }

        int lineNo = 1;
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CreditNoteItem it : cn.getItems()) {
            LineItemDto ln = new LineItemDto();
            ln.setLineNo(lineNo++);
            ln.setDescription(it.getItemName());
            ln.setHsnSac(it.getHsnSac());
            ln.setUom(it.getUnit());
            ln.setQuantity(it.getQty());
            ln.setUnitPrice(it.getUnitPrice());
            ln.setDiscountAmount(it.getDiscount());
            ln.setTaxableValue(it.getTaxableValue());
            ln.setCgstAmount(it.getCgstAmt());
            ln.setSgstAmount(it.getSgstAmt());
            ln.setIgstAmount(it.getIgstAmt());
            ln.setLineTotal(it.getTotalAmount());
            d.getItems().add(ln);
            subtotal = subtotal.add(nz(it.getQty()).multiply(nz(it.getUnitPrice())));
        }

        TotalsDto t = new TotalsDto();
        t.setSubtotal(subtotal);
        t.setTotalTaxable(nz(cn.getTaxableAmount()));
        t.setCgstAmount(nz(cn.getCgstAmount()));
        t.setSgstAmount(nz(cn.getSgstAmount()));
        t.setIgstAmount(nz(cn.getIgstAmount()));
        t.setGrandTotal(nz(cn.getTotalAmount()));
        t.setAmountInWords(AmountInWordsIndian.toWords(nz(cn.getTotalAmount())));
        d.setTotals(t);
        hsnComputer.populate(d);

        d.setNotes(cn.getReason());
        d.setBrandColorHex(cn.getShop() != null ? cn.getShop().getBrandColor() : null);

        DocumentAuditDto audit = new DocumentAuditDto();
        audit.setDocumentHash(hashService.hash(d));
        d.setAudit(audit);
        return d;
    }

    private BigDecimal nz(BigDecimal b) { return b == null ? BigDecimal.ZERO : b; }
}
