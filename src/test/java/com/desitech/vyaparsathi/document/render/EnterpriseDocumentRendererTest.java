package com.desitech.vyaparsathi.document.render;

import com.desitech.vyaparsathi.common.enums.DocumentType;
import com.desitech.vyaparsathi.common.enums.SupplyType;
import com.desitech.vyaparsathi.document.dto.EnterpriseDocumentDto;
import com.desitech.vyaparsathi.document.dto.LineItemDto;
import com.desitech.vyaparsathi.document.dto.PartyDto;
import com.desitech.vyaparsathi.document.dto.TotalsDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke coverage for the shared enterprise renderer — enough to prove
 * the mapper→DTO→renderer pipeline stays intact end-to-end. Full-fidelity
 * PDF rendering is verified visually; these tests are the safety net
 * against silent NPEs from mapping regressions.
 */
class EnterpriseDocumentRendererTest {

    private final EnterpriseDocumentRenderer renderer = new EnterpriseDocumentRenderer();

    private static PartyDto issuer() {
        PartyDto p = new PartyDto();
        p.setRole("ISSUER");
        p.setLegalName("Acme Traders Pvt Ltd");
        p.setTradeName("Acme");
        p.setGstin("10ABCDE1234F1Z5");
        p.setState("Bihar");
        p.setStateCode("10");
        p.setAddressLine1("12 Main St, Sitamarhi");
        p.setSignatoryName("R. Verma");
        p.setSignatoryDesignation("Proprietor");
        return p;
    }

    private static PartyDto customer() {
        PartyDto p = new PartyDto();
        p.setRole("CUSTOMER");
        p.setLegalName("Beta Retail LLP");
        p.setTradeName("Beta");
        p.setGstin("10FGHIJ5678K2Z9");
        p.setState("Bihar");
        p.setStateCode("10");
        p.setAddressLine1("Ward 4, Bettiah");
        return p;
    }

    private static EnterpriseDocumentDto baseDoc(DocumentType type) {
        EnterpriseDocumentDto d = new EnterpriseDocumentDto();
        d.setDocumentType(type);
        d.setDocumentId(1L);
        d.setDocumentNumber("INV/25-26/00001");
        d.setDocumentDate(LocalDate.of(2026, 8, 15));
        d.setIssuer(issuer());
        d.setCounterparty(customer());
        d.setSupplyType(SupplyType.INTRASTATE);
        d.setPlaceOfSupplyState("Bihar");
        d.setPlaceOfSupplyStateCode("10");

        LineItemDto line = new LineItemDto();
        line.setLineNo(1);
        line.setDescription("Widget");
        line.setHsnSac("7318");
        line.setUom("PIECE");
        line.setQuantity(new BigDecimal("3"));
        line.setUnitPrice(new BigDecimal("399.00"));
        line.setTaxableValue(new BigDecimal("1197.00"));
        line.setLineTotal(new BigDecimal("1197.00"));
        d.getItems().add(line);

        TotalsDto t = new TotalsDto();
        t.setSubtotal(new BigDecimal("1197.00"));
        t.setTotalTaxable(new BigDecimal("1197.00"));
        t.setGrandTotal(new BigDecimal("1197.00"));
        d.setTotals(t);
        return d;
    }

    @Test
    @DisplayName("Renders a tax invoice as a non-empty PDF starting with %PDF magic")
    void rendersTaxInvoice() {
        byte[] pdf = renderer.render(baseDoc(DocumentType.TAX_INVOICE));
        assertNotNull(pdf);
        assertTrue(pdf.length > 500, "PDF must have plausible size, got " + pdf.length + " bytes");
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
        assertEquals('D', (char) pdf[2]);
        assertEquals('F', (char) pdf[3]);
    }

    @Test
    @DisplayName("Renders a delivery challan even though supplyType is null")
    void rendersDeliveryChallanWithoutSupplyType() {
        EnterpriseDocumentDto d = baseDoc(DocumentType.DELIVERY_CHALLAN);
        d.setSupplyType(null);
        d.setTotals(null);  // challans have no financials — must still render
        byte[] pdf = renderer.render(d);
        assertNotNull(pdf);
        assertTrue(pdf.length > 500);
    }

    @Test
    @DisplayName("Bill of Supply doesn't require GST columns — no crash on zero-rated totals")
    void rendersBillOfSupply() {
        EnterpriseDocumentDto d = baseDoc(DocumentType.BILL_OF_SUPPLY);
        d.setSupplyType(SupplyType.COMPOSITION);
        byte[] pdf = renderer.render(d);
        assertNotNull(pdf);
        assertTrue(pdf.length > 500);
    }

    @Test
    @DisplayName("Renders a cancelled watermark PO without an issuer state")
    void rendersCancelledPo() {
        EnterpriseDocumentDto d = baseDoc(DocumentType.PURCHASE_ORDER);
        d.setWatermark("CANCELLED");
        d.getIssuer().setStateCode(null);
        d.getIssuer().setState(null);
        byte[] pdf = renderer.render(d);
        assertNotNull(pdf);
        assertTrue(pdf.length > 500);
    }

    @Test
    @DisplayName("Empty item list renders without crash — a draft with no lines")
    void rendersEmptyItems() {
        EnterpriseDocumentDto d = baseDoc(DocumentType.PROFORMA_INVOICE);
        d.getItems().clear();
        d.setWatermark("DRAFT");
        byte[] pdf = renderer.render(d);
        assertNotNull(pdf);
        assertTrue(pdf.length > 500);
    }
}
