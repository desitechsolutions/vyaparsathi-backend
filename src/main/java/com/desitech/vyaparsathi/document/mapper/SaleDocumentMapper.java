package com.desitech.vyaparsathi.document.mapper;

import com.desitech.vyaparsathi.common.enums.DocumentType;
import com.desitech.vyaparsathi.common.enums.SupplyType;
import com.desitech.vyaparsathi.common.util.AmountInWordsIndian;
import com.desitech.vyaparsathi.document.dto.DocumentAuditDto;
import com.desitech.vyaparsathi.document.dto.EnterpriseDocumentDto;
import com.desitech.vyaparsathi.document.dto.LineItemDto;
import com.desitech.vyaparsathi.document.dto.TotalsDto;
import com.desitech.vyaparsathi.document.service.HsnSummaryComputer;
import com.desitech.vyaparsathi.document.service.DocumentIntegrityHashService;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * Maps {@link Sale} → {@link EnterpriseDocumentDto} so it can be rendered
 * by the shared {@code EnterpriseDocumentRenderer}. Proforma sales are
 * mapped as PROFORMA_INVOICE; regular sales as TAX_INVOICE (or BILL_OF_SUPPLY
 * when the shop is on composition scheme / GST not required).
 */
@Component
public class SaleDocumentMapper {

    private final PartyMapper partyMapper;
    private final HsnSummaryComputer hsnComputer;
    private final DocumentIntegrityHashService hashService;

    public SaleDocumentMapper(PartyMapper partyMapper,
                              HsnSummaryComputer hsnComputer,
                              DocumentIntegrityHashService hashService) {
        this.partyMapper = partyMapper;
        this.hsnComputer = hsnComputer;
        this.hashService = hashService;
    }

    public EnterpriseDocumentDto map(Sale sale, BigDecimal paid) {
        EnterpriseDocumentDto d = new EnterpriseDocumentDto();

        d.setDocumentType(resolveType(sale));
        d.setDocumentId(sale.getId());
        d.setDocumentNumber(sale.getInvoiceNo());
        d.setDocumentDate(sale.getDate() != null ? sale.getDate().toLocalDate() : null);
        d.setFiscalYear(fiscalYear(sale));
        d.setStatus(sale.getStatus() != null ? sale.getStatus().name() : null);
        d.setReverseCharge(sale.getReverseCharge());
        // Place of Supply resolution — CBIC rule 46(l) requires this on every
        // tax invoice. We fall back through three sources so PoS is never
        // blank on the printed doc:
        //   1. sale.placeOfSupply (explicit at invoice time)
        //   2. customer's state (recipient state → intra/inter-state derived)
        //   3. shop's own state (intrastate default when neither is set)
        String posState = sale.getPlaceOfSupply();
        String posCode  = null;
        if ((posState == null || posState.isBlank()) && sale.getCustomer() != null
                && sale.getCustomer().getState() != null && !sale.getCustomer().getState().isBlank()) {
            posState = sale.getCustomer().getState();
            posCode  = sale.getCustomer().getStateCode();
        } else if (posState != null && sale.getCustomer() != null
                && posState.equalsIgnoreCase(sale.getCustomer().getState())) {
            posCode = sale.getCustomer().getStateCode();
        }
        if ((posState == null || posState.isBlank()) && sale.getShop() != null) {
            posState = sale.getShop().getState();
            posCode  = sale.getShop().getStateCode();
        }
        d.setPlaceOfSupplyState(posState);
        d.setPlaceOfSupplyStateCode(posCode);
        // SupplyType — user-selected value on the sale wins; fall back to
        // derivation (intra vs inter) from PoS + shop state.
        SupplyType persisted = SupplyType.fromString(sale.getSupplyType());
        d.setSupplyType(persisted != null ? persisted : deriveSupplyType(sale));

        // Parties
        d.setIssuer(partyMapper.fromShop(sale.getShop()));
        d.setCounterparty(partyMapper.fromCustomer(sale.getCustomer()));
        // V99 address snapshots — override the counterparty's default address
        // for bill-to/ship-to/consignee blocks when present. Kept as raw
        // multi-line strings on the DTO for now; the renderer prints them
        // beneath the counterparty block on statutory PDFs.
        d.setBillToAddress(sale.getBillToPartySnapshot());
        d.setShipToAddress(sale.getShipToPartySnapshot());
        d.setConsigneeAddress(sale.getConsigneePartySnapshot());

        // Line items
        int lineNo = 1;
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discSum = BigDecimal.ZERO;
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal cgstSum = BigDecimal.ZERO;
        BigDecimal sgstSum = BigDecimal.ZERO;
        BigDecimal igstSum = BigDecimal.ZERO;

        for (SaleItem si : sale.getSaleItems()) {
            LineItemDto ln = new LineItemDto();
            ln.setLineNo(lineNo++);
            ItemVariant iv = si.getItemVariant();
            ln.setDescription(si.getCustomItemName() != null ? si.getCustomItemName()
                    : (iv != null && iv.getItem() != null ? iv.getItem().getName() : "Item"));
            ln.setItemCode(si.getCustomDescription() != null ? null : (iv != null ? iv.getSku() : null));
            ln.setHsnSac(si.getCustomHsnSac() != null ? si.getCustomHsnSac()
                    : (iv != null ? iv.getHsn() : null));
            ln.setUom(si.getCustomUnit() != null ? si.getCustomUnit()
                    : (iv != null ? iv.getUnit() : null));
            ln.setQuantity(si.getQty());
            ln.setUnitPrice(si.getUnitPrice());
            ln.setDiscountAmount(si.getDiscount());
            ln.setTaxableValue(si.getTaxableValue());
            ln.setCgstAmount(si.getCgstAmt());
            ln.setSgstAmount(si.getSgstAmt());
            ln.setIgstAmount(si.getIgstAmt());
            ln.setBatchNumber(si.getBatchNumber());
            ln.setExpiryDate(si.getExpiryDate());
            BigDecimal lineTotal = nz(si.getTaxableValue())
                    .add(nz(si.getCgstAmt())).add(nz(si.getSgstAmt())).add(nz(si.getIgstAmt()));
            ln.setLineTotal(lineTotal);
            d.getItems().add(ln);

            subtotal = subtotal.add(nz(si.getQty()).multiply(nz(si.getUnitPrice())));
            discSum  = discSum.add(nz(si.getDiscount()));
            taxable  = taxable.add(nz(si.getTaxableValue()));
            cgstSum  = cgstSum.add(nz(si.getCgstAmt()));
            sgstSum  = sgstSum.add(nz(si.getSgstAmt()));
            igstSum  = igstSum.add(nz(si.getIgstAmt()));
        }

        TotalsDto t = new TotalsDto();
        t.setSubtotal(subtotal);
        t.setTotalDiscount(discSum.add(nz(sale.getInvoiceDiscount())));
        t.setTotalTaxable(taxable);
        t.setCgstAmount(cgstSum);
        t.setSgstAmount(sgstSum);
        t.setIgstAmount(igstSum);
        t.setFreight(nz(sale.getShippingCharges()));
        t.setRoundOff(nz(sale.getRoundOff()));
        t.setGrandTotal(nz(sale.getTotalAmount()));
        t.setPaidAmount(nz(paid));
        BigDecimal outstanding = nz(sale.getTotalAmount()).subtract(nz(paid));
        t.setOutstandingAmount(outstanding.max(BigDecimal.ZERO));
        t.setAmountInWords(AmountInWordsIndian.toWords(nz(sale.getTotalAmount())));
        d.setTotals(t);

        // HSN summary
        hsnComputer.populate(d);

        // Notes + terms + due date
        d.setNotes(sale.getNotes());
        d.setDueDate(sale.getDueDate());

        // Watermark for proforma / cancelled
        if (sale.isProforma()) d.setWatermark("PROFORMA");
        if (sale.getStatus() != null && sale.getStatus().name().contains("CANCEL")) d.setWatermark("CANCELLED");

        // Terms & conditions from shop
        String terms = sale.getShop() != null ? sale.getShop().getTermsAndConditions() : null;
        if (terms != null && !terms.isBlank()) {
            for (String line : terms.split("\\r?\\n")) {
                String l = line.trim();
                if (!l.isBlank()) d.getTermsAndConditions().add(l);
            }
        }

        // Audit block
        DocumentAuditDto audit = new DocumentAuditDto();
        audit.setCreatedAt(sale.getDate());
        audit.setDocumentHash(hashService.hash(d));
        d.setAudit(audit);

        // Brand
        d.setBrandColorHex(sale.getShop() != null ? sale.getShop().getBrandColor() : null);
        return d;
    }

    private DocumentType resolveType(Sale sale) {
        if (sale.isProforma()) return DocumentType.PROFORMA_INVOICE;
        if (Boolean.FALSE.equals(sale.getIsGstRequired())) return DocumentType.BILL_OF_SUPPLY;
        return DocumentType.TAX_INVOICE;
    }

    private String fiscalYear(Sale sale) {
        if (sale.getDate() == null) return null;
        int y = sale.getDate().getYear();
        int m = sale.getDate().getMonthValue();
        int fyStart = m >= 4 ? y : y - 1;
        return fyStart + "-" + String.format("%02d", (fyStart + 1) % 100);
    }

    private SupplyType deriveSupplyType(Sale sale) {
        if (sale.getShop() == null || sale.getShop().getStateCode() == null) return null;
        if (sale.getCustomer() != null && sale.getCustomer().getStateCode() != null) {
            return sale.getShop().getStateCode().equalsIgnoreCase(sale.getCustomer().getStateCode())
                    ? SupplyType.INTRASTATE : SupplyType.INTERSTATE;
        }
        return SupplyType.INTRASTATE;
    }

    private BigDecimal nz(BigDecimal b) { return b == null ? BigDecimal.ZERO : b; }
}
