package com.desitech.vyaparsathi.document.mapper;

import com.desitech.vyaparsathi.common.enums.DocumentType;
import com.desitech.vyaparsathi.common.enums.SupplyType;
import com.desitech.vyaparsathi.delivery.entity.Delivery;
import com.desitech.vyaparsathi.delivery.entity.DeliveryItem;
import com.desitech.vyaparsathi.document.dto.DocumentAuditDto;
import com.desitech.vyaparsathi.document.dto.DocumentReferenceDto;
import com.desitech.vyaparsathi.document.dto.EnterpriseDocumentDto;
import com.desitech.vyaparsathi.document.dto.LineItemDto;
import com.desitech.vyaparsathi.document.service.DocumentIntegrityHashService;
import com.desitech.vyaparsathi.sales.entity.Sale;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Maps {@link Delivery} → {@link EnterpriseDocumentDto} so the shared
 * enterprise renderer can produce a Rule-55 compliant challan. Statutory
 * fields (PoS, supply type, reverse charge, address snapshots) flow through
 * from the parent Sale — a Delivery Challan is legally a downstream doc of
 * the invoice and reuses its jurisdiction context.
 */
@Component
public class DeliveryDocumentMapper {

    private final PartyMapper partyMapper;
    private final DocumentIntegrityHashService hashService;

    public DeliveryDocumentMapper(PartyMapper partyMapper, DocumentIntegrityHashService hashService) {
        this.partyMapper = partyMapper;
        this.hashService = hashService;
    }

    public EnterpriseDocumentDto map(Delivery d) {
        EnterpriseDocumentDto doc = new EnterpriseDocumentDto();
        doc.setDocumentType(DocumentType.DELIVERY_CHALLAN);
        doc.setDocumentId(d.getId());
        doc.setDocumentNumber(d.getChallanNo());
        doc.setDocumentDate(d.getCreatedAt() != null ? d.getCreatedAt().toLocalDate() : null);
        doc.setStatus(d.getDeliveryStatus() != null ? d.getDeliveryStatus().name() : null);
        if ("CANCELLED".equalsIgnoreCase(doc.getStatus())) doc.setWatermark("CANCELLED");

        Sale sale = d.getSale();
        doc.setIssuer(partyMapper.fromShop(d.getShop() != null ? d.getShop()
                : (sale != null ? sale.getShop() : null)));
        if (sale != null && sale.getCustomer() != null) {
            doc.setCounterparty(partyMapper.fromCustomer(sale.getCustomer()));
        }

        // Statutory context flows from the parent Sale — a challan is
        // downstream of the invoice and must show the same jurisdiction.
        if (sale != null) {
            doc.setPlaceOfSupplyState(sale.getPlaceOfSupply());
            doc.setReverseCharge(Boolean.TRUE.equals(sale.getReverseCharge()));
            // getSupplyType() now returns SupplyType enum directly
            if (sale.getSupplyType() != null) doc.setSupplyType(sale.getSupplyType());
            doc.setBillToAddress(sale.getBillToPartySnapshot());
            doc.setShipToAddress(sale.getShipToPartySnapshot());
            doc.setConsigneeAddress(sale.getConsigneePartySnapshot());

            // Cross-reference to the parent invoice.
            if (sale.getInvoiceNo() != null && !sale.getInvoiceNo().isBlank()) {
                doc.getLinkedDocuments().add(new DocumentReferenceDto(
                        "TAX_INVOICE",
                        sale.getId(),
                        sale.getInvoiceNo(),
                        sale.getDate() != null ? sale.getDate().toLocalDate() : null));
            }
        }

        // Challan-specific header meta
        if (d.getCourierPartner() != null) doc.getExtraMetadata().put("Courier", d.getCourierPartner());
        if (d.getEwayBillNo() != null) doc.getExtraMetadata().put("E-way Bill", d.getEwayBillNo());
        if (d.getTrackingNumber() != null) doc.getExtraMetadata().put("Tracking #", d.getTrackingNumber());

        // Line items — quantities only, no tax columns (Rule 55: challan is
        // a transport document, not a tax document).
        int lineNo = 1;
        for (DeliveryItem it : d.getItems()) {
            LineItemDto ln = new LineItemDto();
            ln.setLineNo(lineNo++);
            ln.setDescription(it.getItemName());
            ln.setHsnSac(it.getHsnSac());
            ln.setUom(it.getUnit());
            ln.setQuantity(it.getQty() != null ? it.getQty() : BigDecimal.ZERO);
            ln.setBatchNumber(it.getBatchNumber());
            ln.setExpiryDate(it.getExpiryDate());
            doc.getItems().add(ln);
        }

        doc.setNotes(d.getDeliveryNotes());
        doc.setBrandColorHex(d.getShop() != null ? d.getShop().getBrandColor() : null);

        DocumentAuditDto audit = new DocumentAuditDto();
        audit.setCreatedAt(d.getCreatedAt());
        audit.setDocumentHash(hashService.hash(doc));
        doc.setAudit(audit);
        return doc;
    }
}
