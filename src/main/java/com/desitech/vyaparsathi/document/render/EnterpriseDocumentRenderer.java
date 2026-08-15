package com.desitech.vyaparsathi.document.render;

import com.desitech.vyaparsathi.common.enums.DocumentType;
import com.desitech.vyaparsathi.common.util.AmountInWordsIndian;
import com.desitech.vyaparsathi.common.util.IndianCurrencyFormat;
import com.desitech.vyaparsathi.document.dto.DocumentReferenceDto;
import com.desitech.vyaparsathi.document.dto.EnterpriseDocumentDto;
import com.desitech.vyaparsathi.document.dto.HsnSummaryRowDto;
import com.desitech.vyaparsathi.document.dto.LineItemDto;
import com.desitech.vyaparsathi.document.dto.PartyDto;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The single shared PDF renderer for every enterprise document type.
 * Every category-specific PDF service maps its entity → {@link
 * EnterpriseDocumentDto} and hands it here — the renderer stays dumb
 * so every printed document looks identical.
 */
@Service
public class EnterpriseDocumentRenderer {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseDocumentRenderer.class);

    private static final Color BRAND_DEFAULT = new Color(41, 128, 185);
    private static final Color SECTION_LABEL_BG = new Color(243, 244, 246);
    private static final Color ROW_ALT_BG = new Color(249, 250, 251);
    private static final Color BORDER_LIGHT = new Color(229, 231, 235);
    private static final Color TEXT_MUTED = new Color(107, 114, 128);
    private static final Color TEXT_STRONG = new Color(17, 24, 39);
    private static final Color TOTAL_ROW_BG = new Color(250, 245, 235);
    private static final Color WATERMARK_COLOR = new Color(220, 38, 38, 40);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private static final Font TITLE_FONT       = new Font(Font.HELVETICA, 20, Font.BOLD, TEXT_STRONG);
    private static final Font SUBTITLE_FONT    = new Font(Font.HELVETICA, 10, Font.NORMAL, TEXT_MUTED);
    private static final Font SECTION_LABEL    = new Font(Font.HELVETICA, 7, Font.BOLD, TEXT_MUTED);
    private static final Font META_LABEL       = new Font(Font.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);
    private static final Font META_VALUE       = new Font(Font.HELVETICA, 9, Font.BOLD, TEXT_STRONG);
    private static final Font BODY             = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXT_STRONG);
    private static final Font BODY_MUTED       = new Font(Font.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);
    private static final Font BODY_STRONG      = new Font(Font.HELVETICA, 9, Font.BOLD, TEXT_STRONG);
    private static final Font TABLE_HEADER     = new Font(Font.HELVETICA, 8, Font.BOLD, TEXT_STRONG);
    private static final Font TABLE_CELL       = new Font(Font.HELVETICA, 8, Font.NORMAL, TEXT_STRONG);
    private static final Font TABLE_CELL_MUTED = new Font(Font.HELVETICA, 7, Font.NORMAL, TEXT_MUTED);
    private static final Font TOTAL_BOLD       = new Font(Font.HELVETICA, 10, Font.BOLD, TEXT_STRONG);
    private static final Font FOOTER_FONT      = new Font(Font.HELVETICA, 7, Font.NORMAL, TEXT_MUTED);

    public byte[] render(EnterpriseDocumentDto doc) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document pdf = new Document(PageSize.A4, 36, 36, 40, 44);
            PdfWriter writer = PdfWriter.getInstance(pdf, baos);

            Color brand = resolveBrand(doc);
            doc.setBrandColor(brand);
            writer.setPageEvent(new PageEvent(brand, doc));
            setPdfMetadata(pdf, doc);

            pdf.open();

            addHeader(pdf, doc, brand);
            addBrandRule(pdf, brand);
            addPartyBlocks(pdf, doc);
            addReferenceDocuments(pdf, doc);
            addItemsTable(pdf, doc);
            addHsnAndTotals(pdf, doc, brand);
            addPaymentAndTerms(pdf, doc, brand);
            addNotesAndSignatory(pdf, doc);
            addEInvoiceBlock(pdf, doc);
            pdf.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("EnterpriseDocumentRenderer failed for doc {}/{}",
                    doc.getDocumentType(), doc.getDocumentNumber(), e);
            throw new RuntimeException("PDF render failed: " + e.getMessage(), e);
        }
    }

    private void setPdfMetadata(Document pdf, EnterpriseDocumentDto doc) {
        pdf.addTitle((doc.getDocumentType() != null ? doc.getDocumentType().legalTitle() : "Document")
                + " " + safe(doc.getDocumentNumber()));
        pdf.addSubject(doc.getDocumentType() != null ? doc.getDocumentType().legalTitle() : "");
        pdf.addAuthor(doc.getIssuer() != null ? doc.getIssuer().displayLegal() : "Vyaparsathi");
        pdf.addCreator("Vyaparsathi Enterprise Document Renderer");
        String gstin = doc.getIssuer() != null ? safe(doc.getIssuer().getGstin()) : "";
        pdf.addKeywords(String.format("%s|%s|%s",
                doc.getDocumentType() != null ? doc.getDocumentType().name() : "",
                gstin,
                safe(doc.getFiscalYear())));
    }

    private Color resolveBrand(EnterpriseDocumentDto doc) {
        String hex = doc.getBrandColorHex();
        if (hex == null || hex.isBlank()) return BRAND_DEFAULT;
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            return new Color(
                    Integer.parseInt(h.substring(0, 2), 16),
                    Integer.parseInt(h.substring(2, 4), 16),
                    Integer.parseInt(h.substring(4, 6), 16));
        } catch (Exception e) { return BRAND_DEFAULT; }
    }

    // ── Header ────────────────────────────────────────────────────────────

    private void addHeader(Document pdf, EnterpriseDocumentDto doc, Color brand) throws DocumentException {
        PdfPTable header = new PdfPTable(new float[]{58, 42});
        header.setWidthPercentage(100);
        header.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        // ── Left cell: issuer identity ─────────────────────────────────
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPaddingRight(20);

        PartyDto issuer = doc.getIssuer();
        if (issuer != null) {
            if (doc.getLogoBytes() != null && doc.getLogoBytes().length > 0) {
                try {
                    Image logo = Image.getInstance(doc.getLogoBytes());
                    logo.scaleToFit(120, 60);
                    left.addElement(logo);
                } catch (Exception ignore) {}
            }
            left.addElement(paragraph(issuer.displayName(),
                    new Font(Font.HELVETICA, 15, Font.BOLD, TEXT_STRONG)));
            if (issuer.getLegalName() != null && !issuer.getLegalName().equalsIgnoreCase(issuer.getTradeName())) {
                left.addElement(paragraph(issuer.getLegalName(), BODY_MUTED));
            }
            if (issuer.getAddressLine1() != null) left.addElement(paragraph(issuer.getAddressLine1(), BODY_MUTED));
            if (issuer.getAddressLine2() != null) left.addElement(paragraph(issuer.getAddressLine2(), BODY_MUTED));
            String cityLine = joinComma(issuer.getCity(), issuer.getState(),
                    issuer.getPincode() != null ? "— " + issuer.getPincode() : null);
            if (!cityLine.isBlank()) left.addElement(paragraph(cityLine, BODY_MUTED));
            if (issuer.getPhone() != null) left.addElement(paragraph("Phone: " + issuer.getPhone(), BODY_MUTED));
            if (issuer.getEmail() != null) left.addElement(paragraph("Email: " + issuer.getEmail(), BODY_MUTED));
            if (issuer.getGstin() != null) left.addElement(paragraph("GSTIN: " + issuer.getGstin(), BODY_STRONG));
            if (issuer.getPan() != null) left.addElement(paragraph("PAN: " + issuer.getPan(), BODY_MUTED));
            if (issuer.getCin() != null) left.addElement(paragraph("CIN: " + issuer.getCin(), BODY_MUTED));
        }
        header.addCell(left);

        // ── Right cell: title + meta ───────────────────────────────────
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        DocumentType type = doc.getDocumentType();
        Paragraph title = new Paragraph(type != null ? type.legalTitle() : "DOCUMENT",
                new Font(Font.HELVETICA, 20, Font.BOLD, brand));
        title.setAlignment(Element.ALIGN_RIGHT);
        title.setSpacingAfter(6f);
        right.addElement(title);

        // Meta rows on the right
        Map<String, String> rows = new java.util.LinkedHashMap<>();
        if (doc.getDocumentNumber() != null) rows.put("Doc No", doc.getDocumentNumber());
        if (doc.getDocumentDate() != null) rows.put("Doc Date", DATE_FMT.format(doc.getDocumentDate()));
        if (doc.getFiscalYear() != null) rows.put("FY", doc.getFiscalYear());
        if (doc.getPlaceOfSupplyState() != null) rows.put("PoS",
                doc.getPlaceOfSupplyState() +
                (doc.getPlaceOfSupplyStateCode() != null ? " (" + doc.getPlaceOfSupplyStateCode() + ")" : ""));
        if (doc.getSupplyType() != null) rows.put("Supply", humaniseSupply(doc.getSupplyType().name()));
        if (doc.getReverseCharge() != null) rows.put("RCM", doc.getReverseCharge() ? "Yes" : "No");
        if (doc.getCurrencyCode() != null && !"INR".equalsIgnoreCase(doc.getCurrencyCode()))
            rows.put("Currency", doc.getCurrencyCode() + " @ " + (doc.getExchangeRate() != null ? doc.getExchangeRate().toPlainString() : "1"));
        if (doc.getStatus() != null) rows.put("Status", doc.getStatus());
        if (doc.getEInvoice() != null && doc.getEInvoice().getIrn() != null)
            rows.put("IRN", ellipsis(doc.getEInvoice().getIrn(), 20));
        if (doc.getEWayBill() != null && doc.getEWayBill().getNumber() != null)
            rows.put("EWB", doc.getEWayBill().getNumber());
        if (doc.getExtraMetadata() != null) rows.putAll(doc.getExtraMetadata());

        PdfPTable meta = new PdfPTable(new float[]{40, 60});
        meta.setWidthPercentage(100);
        for (Map.Entry<String, String> e : rows.entrySet()) {
            meta.addCell(metaCell(e.getKey(), META_LABEL, Element.ALIGN_LEFT));
            meta.addCell(metaCell(e.getValue(), META_VALUE, Element.ALIGN_RIGHT));
        }
        right.addElement(meta);
        header.addCell(right);

        pdf.add(header);
    }

    private void addBrandRule(Document pdf, Color brand) throws DocumentException {
        LineSeparator rule = new LineSeparator(1.5f, 100, brand, Element.ALIGN_CENTER, -2);
        Paragraph p = new Paragraph();
        p.setSpacingBefore(6f);
        p.setSpacingAfter(10f);
        p.add(new Chunk(rule));
        pdf.add(p);
    }

    // ── Party blocks (bill-to / ship-to / consignee) ──────────────────────

    private void addPartyBlocks(Document pdf, EnterpriseDocumentDto doc) throws DocumentException {
        // Always render counterparty. Then bill/ship/consignee when they differ.
        java.util.List<java.util.Map.Entry<String, PartyDto>> blocks = new java.util.ArrayList<>();
        PartyDto cp = doc.getCounterparty();

        String cpLabel = counterpartyLabel(doc);
        if (cp != null && !cp.isEmpty()) blocks.add(entry(cpLabel, cp));
        if (doc.getBillTo() != null && !doc.getBillTo().isEmpty() && cp != null && !partyEquivalent(cp, doc.getBillTo()))
            blocks.add(entry("BILL TO", doc.getBillTo()));
        if (doc.getShipTo() != null && !doc.getShipTo().isEmpty())
            blocks.add(entry("SHIP TO", doc.getShipTo()));
        if (doc.getConsignee() != null && !doc.getConsignee().isEmpty() && !partyEquivalent(doc.getShipTo(), doc.getConsignee()))
            blocks.add(entry("CONSIGNEE", doc.getConsignee()));

        if (blocks.isEmpty()) return;

        int cols = blocks.size();
        float[] widths = new float[cols];
        for (int i = 0; i < cols; i++) widths[i] = 1;
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);
        for (java.util.Map.Entry<String, PartyDto> e : blocks) {
            table.addCell(partyCell(e.getKey(), e.getValue()));
        }
        pdf.add(table);
    }

    private String counterpartyLabel(EnterpriseDocumentDto doc) {
        DocumentType t = doc.getDocumentType();
        if (t == null) return "PARTY";
        switch (t) {
            case TAX_INVOICE, BILL_OF_SUPPLY, PROFORMA_INVOICE, QUOTATION,
                 CREDIT_NOTE, DELIVERY_CHALLAN, SALES_ORDER, RECEIPT_VOUCHER:
                return "BILLED TO";
            case PURCHASE_ORDER, GOODS_RECEIPT_NOTE, PURCHASE_RETURN,
                 DEBIT_NOTE, PAYMENT_VOUCHER:
                return "SUPPLIER";
            default: return "COUNTERPARTY";
        }
    }

    private PdfPCell partyCell(String label, PartyDto p) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER_LIGHT);
        cell.setPadding(8f);

        Paragraph lbl = new Paragraph(label, SECTION_LABEL);
        lbl.setSpacingAfter(3f);
        cell.addElement(lbl);

        cell.addElement(paragraph(p.displayName(), BODY_STRONG));
        if (p.getLegalName() != null && !p.getLegalName().equalsIgnoreCase(p.getTradeName()))
            cell.addElement(paragraph(p.getLegalName(), BODY_MUTED));
        if (p.getAddressLine1() != null) cell.addElement(paragraph(p.getAddressLine1(), BODY_MUTED));
        if (p.getAddressLine2() != null) cell.addElement(paragraph(p.getAddressLine2(), BODY_MUTED));
        String cityLine = joinComma(p.getCity(), p.getState(),
                p.getPincode() != null ? "— " + p.getPincode() : null);
        if (!cityLine.isBlank()) cell.addElement(paragraph(cityLine, BODY_MUTED));
        if (p.getGstin() != null) cell.addElement(paragraph("GSTIN: " + p.getGstin(), BODY_STRONG));
        else if (p.getStateCode() != null) cell.addElement(paragraph("State code: " + p.getStateCode(), BODY_MUTED));
        if (p.getPan() != null) cell.addElement(paragraph("PAN: " + p.getPan(), BODY_MUTED));
        if (p.getPhone() != null) cell.addElement(paragraph("Phone: " + p.getPhone(), BODY_MUTED));
        if (p.getEmail() != null) cell.addElement(paragraph("Email: " + p.getEmail(), BODY_MUTED));
        return cell;
    }

    // ── Reference documents block ─────────────────────────────────────────

    private void addReferenceDocuments(Document pdf, EnterpriseDocumentDto doc) throws DocumentException {
        List<DocumentReferenceDto> refs = doc.getLinkedDocuments();
        if (refs == null || refs.isEmpty()) return;

        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingAfter(10f);

        PdfPCell label = new PdfPCell(new Paragraph("REFERENCE DOCUMENTS", SECTION_LABEL));
        label.setBackgroundColor(SECTION_LABEL_BG);
        label.setBorder(Rectangle.NO_BORDER);
        label.setPadding(5f);
        t.addCell(label);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < refs.size(); i++) {
            DocumentReferenceDto r = refs.get(i);
            if (i > 0) sb.append("   ·   ");
            sb.append(humaniseType(r.getType())).append(" ").append(safe(r.getNumber()));
            if (r.getDate() != null) sb.append(" (").append(DATE_FMT.format(r.getDate())).append(")");
        }
        PdfPCell val = new PdfPCell(new Paragraph(sb.toString(), BODY));
        val.setBorder(Rectangle.NO_BORDER);
        val.setPadding(6f);
        t.addCell(val);
        pdf.add(t);
    }

    // ── Items table ──────────────────────────────────────────────────────

    private void addItemsTable(Document pdf, EnterpriseDocumentDto doc) throws DocumentException {
        if (doc.getItems() == null || doc.getItems().isEmpty()) return;

        boolean showGst = shouldShowGst(doc);
        boolean isGrn   = doc.getDocumentType() == DocumentType.GOODS_RECEIPT_NOTE;

        // Layout choice: GRN gets Ordered/Received/Damaged/Rejected/Accepted;
        // everything else gets Qty/Rate/Discount/Taxable/GST-split/Total.
        float[] widths;
        String[] headers;
        if (isGrn) {
            widths  = new float[]{3, 24, 8, 6, 8, 8, 8, 8, 8, 9};
            headers = new String[]{"#", "Item", "HSN", "UOM", "Ordered", "Received", "Damaged", "Rejected", "Accepted", "Unit Cost"};
        } else if (showGst) {
            widths  = new float[]{3, 24, 8, 5, 6, 7, 7, 8, 8, 8, 8, 8};
            headers = new String[]{"#", "Item", "HSN", "UOM", "Qty", "Rate", "Disc", "Taxable", "CGST", "SGST", "IGST", "Total"};
        } else {
            widths  = new float[]{3, 40, 8, 5, 8, 10, 10, 16};
            headers = new String[]{"#", "Item", "HSN", "UOM", "Qty", "Rate", "Disc", "Total"};
        }

        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setSpacingAfter(6f);
        t.setHeaderRows(1);

        for (String h : headers) t.addCell(headerCell(h));

        int i = 0;
        for (LineItemDto ln : doc.getItems()) {
            Color bg = (i++ % 2 == 0) ? Color.WHITE : ROW_ALT_BG;
            t.addCell(bodyCell(String.valueOf(ln.getLineNo() != null ? ln.getLineNo() : i), bg, Element.ALIGN_CENTER));
            t.addCell(itemCell(ln, bg));
            t.addCell(bodyCell(safe(ln.getHsnSac()), bg, Element.ALIGN_CENTER));
            t.addCell(bodyCell(safe(ln.getUom()), bg, Element.ALIGN_CENTER));

            if (isGrn) {
                t.addCell(bodyCell(qty(ln.getOrderedQty()), bg, Element.ALIGN_RIGHT));
                t.addCell(bodyCell(qty(ln.getReceivedQty()), bg, Element.ALIGN_RIGHT));
                t.addCell(bodyCell(qty(ln.getDamagedQty()), bg, Element.ALIGN_RIGHT));
                t.addCell(bodyCell(qty(ln.getRejectedQty()), bg, Element.ALIGN_RIGHT));
                t.addCell(bodyCell(qty(ln.getAcceptedQty()), bg, Element.ALIGN_RIGHT));
                t.addCell(bodyCell(money(ln.getUnitPrice()), bg, Element.ALIGN_RIGHT));
            } else if (showGst) {
                t.addCell(bodyCell(qty(ln.getQuantity()), bg, Element.ALIGN_RIGHT));
                t.addCell(bodyCell(money(ln.getUnitPrice()), bg, Element.ALIGN_RIGHT));
                t.addCell(bodyCell(money(ln.getDiscountAmount()), bg, Element.ALIGN_RIGHT));
                t.addCell(bodyCell(money(ln.getTaxableValue()), bg, Element.ALIGN_RIGHT));
                t.addCell(gstCell(ln.getCgstAmount(), ln.getCgstRatePct(), bg));
                t.addCell(gstCell(ln.getSgstAmount(), ln.getSgstRatePct(), bg));
                t.addCell(gstCell(ln.getIgstAmount(), ln.getIgstRatePct(), bg));
                t.addCell(bodyCell(money(ln.getLineTotal()), bg, Element.ALIGN_RIGHT));
            } else {
                t.addCell(bodyCell(qty(ln.getQuantity()), bg, Element.ALIGN_RIGHT));
                t.addCell(bodyCell(money(ln.getUnitPrice()), bg, Element.ALIGN_RIGHT));
                t.addCell(bodyCell(money(ln.getDiscountAmount()), bg, Element.ALIGN_RIGHT));
                t.addCell(bodyCell(money(ln.getLineTotal()), bg, Element.ALIGN_RIGHT));
            }
        }

        pdf.add(t);
    }

    private boolean shouldShowGst(EnterpriseDocumentDto doc) {
        DocumentType t = doc.getDocumentType();
        return t == DocumentType.TAX_INVOICE
                || t == DocumentType.PROFORMA_INVOICE
                || t == DocumentType.QUOTATION
                || t == DocumentType.SALES_ORDER
                || t == DocumentType.PURCHASE_ORDER
                || t == DocumentType.PURCHASE_RETURN
                || t == DocumentType.DEBIT_NOTE
                || t == DocumentType.CREDIT_NOTE;
    }

    private PdfPCell itemCell(LineItemDto ln, Color bg) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(bg);
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(5f);
        c.addElement(paragraph(safe(ln.getDescription()), BODY));
        if (ln.getItemCode() != null) c.addElement(paragraph("SKU: " + ln.getItemCode(), TABLE_CELL_MUTED));
        String batchLine = joinComma(
                ln.getBatchNumber() != null ? "Batch " + ln.getBatchNumber() : null,
                ln.getExpiryDate() != null ? "Exp " + ln.getExpiryDate() : null);
        if (!batchLine.isBlank()) c.addElement(paragraph(batchLine, TABLE_CELL_MUTED));
        if (ln.getReason() != null) c.addElement(paragraph("Reason: " + ln.getReason(), TABLE_CELL_MUTED));
        return c;
    }

    private PdfPCell gstCell(BigDecimal amount, BigDecimal ratePct, Color bg) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(bg);
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(5f);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.addElement(rightPara(money(amount), TABLE_CELL));
        if (ratePct != null) c.addElement(rightPara(IndianCurrencyFormat.formatPercent(ratePct), TABLE_CELL_MUTED));
        return c;
    }

    // ── HSN summary + Totals card ─────────────────────────────────────────

    private void addHsnAndTotals(Document pdf, EnterpriseDocumentDto doc, Color brand) throws DocumentException {
        PdfPTable frame = new PdfPTable(new float[]{55, 45});
        frame.setWidthPercentage(100);
        frame.setSpacingAfter(8f);

        // Left: HSN summary
        PdfPCell hsnCell = new PdfPCell();
        hsnCell.setBorder(Rectangle.NO_BORDER);
        hsnCell.setPaddingRight(10f);

        if (doc.getHsnSummary() != null && !doc.getHsnSummary().isEmpty()) {
            Paragraph lbl = new Paragraph("HSN SUMMARY", SECTION_LABEL);
            lbl.setSpacingAfter(4f);
            hsnCell.addElement(lbl);

            PdfPTable hsn = new PdfPTable(new float[]{18, 20, 12, 12, 12, 12, 14});
            hsn.setWidthPercentage(100);
            hsn.addCell(headerCell("HSN/SAC"));
            hsn.addCell(headerCell("Taxable"));
            hsn.addCell(headerCell("CGST"));
            hsn.addCell(headerCell("SGST"));
            hsn.addCell(headerCell("IGST"));
            hsn.addCell(headerCell("Cess"));
            hsn.addCell(headerCell("Total"));
            for (HsnSummaryRowDto r : doc.getHsnSummary()) {
                hsn.addCell(bodyCell(safe(r.getHsnSac()), Color.WHITE, Element.ALIGN_LEFT));
                hsn.addCell(bodyCell(money(r.getTaxableValue()), Color.WHITE, Element.ALIGN_RIGHT));
                hsn.addCell(bodyCell(money(r.getCgstAmount()), Color.WHITE, Element.ALIGN_RIGHT));
                hsn.addCell(bodyCell(money(r.getSgstAmount()), Color.WHITE, Element.ALIGN_RIGHT));
                hsn.addCell(bodyCell(money(r.getIgstAmount()), Color.WHITE, Element.ALIGN_RIGHT));
                hsn.addCell(bodyCell(money(r.getCessAmount()), Color.WHITE, Element.ALIGN_RIGHT));
                hsn.addCell(bodyCell(money(r.getGrandTotal()), Color.WHITE, Element.ALIGN_RIGHT));
            }
            hsnCell.addElement(hsn);
        }
        frame.addCell(hsnCell);

        // Right: Totals
        PdfPCell totalsCell = new PdfPCell();
        totalsCell.setBorder(Rectangle.NO_BORDER);
        PdfPTable totals = new PdfPTable(new float[]{55, 45});
        totals.setWidthPercentage(100);

        var tt = doc.getTotals();
        if (tt == null) return;

        addTotalsRow(totals, "Subtotal",             money(tt.getSubtotal()),        false);
        if (nonZero(tt.getTotalDiscount()))
            addTotalsRow(totals, "Discount",         "(" + money(tt.getTotalDiscount()) + ")", false);
        if (nonZero(tt.getTotalTaxable()) && !equals(tt.getTotalTaxable(), tt.getSubtotal()))
            addTotalsRow(totals, "Taxable",          money(tt.getTotalTaxable()),    false);
        if (nonZero(tt.getCgstAmount())) addTotalsRow(totals, "CGST", money(tt.getCgstAmount()), false);
        if (nonZero(tt.getSgstAmount())) addTotalsRow(totals, "SGST", money(tt.getSgstAmount()), false);
        if (nonZero(tt.getIgstAmount())) addTotalsRow(totals, "IGST", money(tt.getIgstAmount()), false);
        if (nonZero(tt.getCessAmount())) addTotalsRow(totals, "Cess", money(tt.getCessAmount()), false);
        if (nonZero(tt.getFreight()))        addTotalsRow(totals, "Freight",        money(tt.getFreight()),        false);
        if (nonZero(tt.getInsurance()))      addTotalsRow(totals, "Insurance",      money(tt.getInsurance()),      false);
        if (nonZero(tt.getLandingCharges())) addTotalsRow(totals, "Landing charges", money(tt.getLandingCharges()), false);
        if (nonZero(tt.getRoundOff()))       addTotalsRow(totals, "Round off",      money(tt.getRoundOff()),       false);

        // Grand total row — brand-coloured band
        PdfPCell gLbl = new PdfPCell(new Paragraph("GRAND TOTAL", TOTAL_BOLD));
        gLbl.setBackgroundColor(TOTAL_ROW_BG);
        gLbl.setBorder(Rectangle.NO_BORDER);
        gLbl.setPadding(6f);
        totals.addCell(gLbl);

        PdfPCell gVal = new PdfPCell(rightPara(IndianCurrencyFormat.formatCurrency(tt.getGrandTotal()), TOTAL_BOLD));
        gVal.setBackgroundColor(TOTAL_ROW_BG);
        gVal.setBorder(Rectangle.NO_BORDER);
        gVal.setPadding(6f);
        totals.addCell(gVal);

        if (nonZero(tt.getPaidAmount())) addTotalsRow(totals, "Paid", money(tt.getPaidAmount()), false);
        if (nonZero(tt.getOutstandingAmount()))
            addTotalsRow(totals, "Outstanding", money(tt.getOutstandingAmount()), true);

        totalsCell.addElement(totals);

        // Amount in words
        String words = tt.getAmountInWords();
        if ((words == null || words.isBlank()) && tt.getGrandTotal() != null) {
            words = AmountInWordsIndian.toWords(tt.getGrandTotal());
        }
        if (words != null && !words.isBlank()) {
            Paragraph aw = new Paragraph();
            aw.add(new Chunk("Amount in words: ", BODY_MUTED));
            aw.add(new Chunk(words, BODY_STRONG));
            aw.setSpacingBefore(6f);
            totalsCell.addElement(aw);
        }
        frame.addCell(totalsCell);
        pdf.add(frame);
    }

    private void addTotalsRow(PdfPTable t, String label, String value, boolean emphasise) {
        Font lFont = emphasise ? BODY_STRONG : BODY_MUTED;
        Font vFont = emphasise ? TOTAL_BOLD : BODY;
        PdfPCell l = new PdfPCell(new Paragraph(label, lFont));
        l.setBorder(Rectangle.NO_BORDER);
        l.setPadding(3f);
        t.addCell(l);
        PdfPCell v = new PdfPCell(rightPara(value, vFont));
        v.setBorder(Rectangle.NO_BORDER);
        v.setPadding(3f);
        t.addCell(v);
    }

    // ── Payment / terms block ────────────────────────────────────────────

    private void addPaymentAndTerms(Document pdf, EnterpriseDocumentDto doc, Color brand) throws DocumentException {
        PartyDto iss = doc.getIssuer();
        boolean hasBank = iss != null &&
                (iss.getBankAccountNumber() != null || iss.getUpiId() != null);
        boolean hasTerms = doc.getTermsAndConditions() != null && !doc.getTermsAndConditions().isEmpty();
        boolean hasPay   = doc.getPaymentTerms() != null || doc.getDueDate() != null;

        if (!hasBank && !hasTerms && !hasPay) return;

        PdfPTable t = new PdfPTable(new float[]{50, 50});
        t.setWidthPercentage(100);
        t.setSpacingAfter(8f);

        // Left: Payment & bank
        PdfPCell payCell = new PdfPCell();
        payCell.setBorder(Rectangle.BOX);
        payCell.setBorderColor(BORDER_LIGHT);
        payCell.setPadding(8f);
        Paragraph lb = new Paragraph("PAYMENT & BANK", SECTION_LABEL);
        lb.setSpacingAfter(4f);
        payCell.addElement(lb);
        if (hasPay) {
            if (doc.getPaymentTerms() != null) payCell.addElement(paragraph("Terms: " + doc.getPaymentTerms(), BODY));
            if (doc.getDueDate() != null) payCell.addElement(paragraph("Due date: " + DATE_FMT.format(doc.getDueDate()), BODY));
        }
        if (hasBank) {
            if (iss.getBankName() != null) payCell.addElement(paragraph("Bank: " + iss.getBankName(), BODY));
            if (iss.getBankAccountNumber() != null) payCell.addElement(paragraph("A/c: " + iss.getBankAccountNumber(), BODY));
            if (iss.getBankIfsc() != null) payCell.addElement(paragraph("IFSC: " + iss.getBankIfsc(), BODY));
            if (iss.getBankBranch() != null) payCell.addElement(paragraph("Branch: " + iss.getBankBranch(), BODY_MUTED));
            if (iss.getUpiId() != null) payCell.addElement(paragraph("UPI: " + iss.getUpiId(), BODY_STRONG));
        }
        t.addCell(payCell);

        // Right: Terms & conditions
        PdfPCell termsCell = new PdfPCell();
        termsCell.setBorder(Rectangle.BOX);
        termsCell.setBorderColor(BORDER_LIGHT);
        termsCell.setPadding(8f);
        Paragraph tl = new Paragraph("TERMS & CONDITIONS", SECTION_LABEL);
        tl.setSpacingAfter(4f);
        termsCell.addElement(tl);
        if (hasTerms) {
            int i = 1;
            for (String term : doc.getTermsAndConditions()) {
                termsCell.addElement(paragraph(i++ + ". " + term, BODY_MUTED));
            }
        } else {
            termsCell.addElement(paragraph("—", BODY_MUTED));
        }
        t.addCell(termsCell);
        pdf.add(t);
    }

    // ── Notes + signatory ────────────────────────────────────────────────

    private void addNotesAndSignatory(Document pdf, EnterpriseDocumentDto doc) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[]{60, 40});
        t.setWidthPercentage(100);
        t.setSpacingAfter(10f);

        // Notes
        PdfPCell notes = new PdfPCell();
        notes.setBorder(Rectangle.NO_BORDER);
        notes.setPaddingRight(12f);
        Paragraph nl = new Paragraph("NOTES", SECTION_LABEL);
        nl.setSpacingAfter(3f);
        notes.addElement(nl);
        notes.addElement(paragraph(doc.getNotes() != null ? doc.getNotes() : "—", BODY_MUTED));
        t.addCell(notes);

        // Signatory
        PdfPCell sig = new PdfPCell();
        sig.setBorder(Rectangle.NO_BORDER);
        sig.setPaddingLeft(12f);
        sig.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PartyDto iss = doc.getIssuer();
        sig.addElement(rightPara("For " + (iss != null ? iss.displayLegal() : "—"), BODY_STRONG));
        if (doc.getSignatureBytes() != null && doc.getSignatureBytes().length > 0) {
            try {
                Image simg = Image.getInstance(doc.getSignatureBytes());
                simg.scaleToFit(120, 40);
                simg.setAlignment(Image.ALIGN_RIGHT);
                sig.addElement(simg);
            } catch (Exception ignore) {}
        } else {
            sig.addElement(rightPara(" ", BODY));
            sig.addElement(rightPara(" ", BODY));
        }
        String signatoryName = iss != null && iss.getSignatoryName() != null
                ? iss.getSignatoryName() : "Authorised Signatory";
        String signatoryDesig = iss != null && iss.getSignatoryDesignation() != null
                ? iss.getSignatoryDesignation() : "";
        sig.addElement(rightPara(signatoryName, BODY_STRONG));
        if (!signatoryDesig.isBlank()) sig.addElement(rightPara(signatoryDesig, BODY_MUTED));
        t.addCell(sig);
        pdf.add(t);
    }

    // ── e-Invoice IRN + QR ───────────────────────────────────────────────

    private void addEInvoiceBlock(Document pdf, EnterpriseDocumentDto doc) throws DocumentException {
        if (doc.getEInvoice() == null || doc.getEInvoice().getIrn() == null) return;

        PdfPTable t = new PdfPTable(new float[]{100});
        t.setWidthPercentage(100);
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.BOX);
        c.setBorderColor(BORDER_LIGHT);
        c.setBackgroundColor(SECTION_LABEL_BG);
        c.setPadding(8f);

        Paragraph lbl = new Paragraph("E-INVOICE", SECTION_LABEL);
        lbl.setSpacingAfter(3f);
        c.addElement(lbl);
        c.addElement(paragraph("IRN: " + doc.getEInvoice().getIrn(), BODY_STRONG));
        if (doc.getEInvoice().getAckNumber() != null)
            c.addElement(paragraph("Ack #: " + doc.getEInvoice().getAckNumber() +
                    (doc.getEInvoice().getAckDate() != null
                            ? "   ·   Ack date: " + DATETIME_FMT.format(doc.getEInvoice().getAckDate())
                            : ""), BODY_MUTED));
        t.addCell(c);
        pdf.add(t);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Paragraph paragraph(String text, Font f) {
        Paragraph p = new Paragraph(text == null ? "" : text, f);
        p.setLeading(11);
        return p;
    }

    private Paragraph rightPara(String text, Font f) {
        Paragraph p = paragraph(text, f);
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private PdfPCell headerCell(String text) {
        PdfPCell c = new PdfPCell(new Paragraph(text != null ? text.toUpperCase() : "", TABLE_HEADER));
        c.setBackgroundColor(SECTION_LABEL_BG);
        c.setBorder(Rectangle.NO_BORDER);
        c.setBorderWidthBottom(0.8f);
        c.setBorderColorBottom(BORDER_LIGHT);
        c.setPadding(6f);
        return c;
    }

    private PdfPCell bodyCell(String text, Color bg, int align) {
        PdfPCell c = new PdfPCell(new Paragraph(text == null ? "—" : text, TABLE_CELL));
        c.setBackgroundColor(bg);
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(align);
        c.setPadding(5f);
        return c;
    }

    private PdfPCell metaCell(String text, Font f, int align) {
        PdfPCell c = new PdfPCell(new Paragraph(text == null ? "—" : text, f));
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(align);
        c.setPadding(2f);
        return c;
    }

    private String safe(String s) { return s == null ? "" : s; }
    private String qty(BigDecimal b) { return b == null ? "0" : IndianCurrencyFormat.formatQty(b); }
    private String money(BigDecimal b) { return b == null || b.compareTo(BigDecimal.ZERO) == 0
            ? "0.00" : IndianCurrencyFormat.formatNumber(b); }
    private boolean nonZero(BigDecimal b) { return b != null && b.compareTo(BigDecimal.ZERO) != 0; }
    private boolean equals(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.compareTo(b) == 0;
    }

    private String joinComma(String... parts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (!first) sb.append(", ");
            sb.append(p);
            first = false;
        }
        return sb.toString();
    }

    private String ellipsis(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private String humaniseSupply(String v) {
        if (v == null) return "";
        return v.replace('_', ' ').toLowerCase();
    }

    private String humaniseType(String v) {
        if (v == null) return "";
        switch (v) {
            case "TAX_INVOICE":       return "Invoice";
            case "PURCHASE_ORDER":    return "PO";
            case "GOODS_RECEIPT_NOTE":return "GRN";
            case "DELIVERY_CHALLAN":  return "Challan";
            case "PURCHASE_RETURN":   return "RTV";
            case "DEBIT_NOTE":        return "DN";
            case "CREDIT_NOTE":       return "CN";
            case "SALES_ORDER":       return "SO";
            case "QUOTATION":         return "Quote";
            default: return v.replace('_', ' ');
        }
    }

    private java.util.Map.Entry<String, PartyDto> entry(String k, PartyDto v) {
        return new java.util.AbstractMap.SimpleEntry<>(k, v);
    }

    private boolean partyEquivalent(PartyDto a, PartyDto b) {
        if (a == null || b == null) return false;
        return java.util.Objects.equals(a.getLegalName(), b.getLegalName())
            && java.util.Objects.equals(a.getGstin(), b.getGstin())
            && java.util.Objects.equals(a.getAddressLine1(), b.getAddressLine1());
    }

    // ── Page event: watermark + Page N of M + audit footer ───────────────

    static class PageEvent extends com.lowagie.text.pdf.PdfPageEventHelper {
        private final Color brand;
        private final EnterpriseDocumentDto doc;
        private final Font footerFont = new Font(Font.HELVETICA, 7, Font.NORMAL, TEXT_MUTED);

        PageEvent(Color brand, EnterpriseDocumentDto doc) {
            this.brand = brand;
            this.doc = doc;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContentUnder();

            // Watermark
            if (doc.getWatermark() != null && !doc.getWatermark().isBlank()) {
                try {
                    Font wf = new Font(Font.HELVETICA, 90, Font.BOLD, WATERMARK_COLOR);
                    Phrase p = new Phrase(doc.getWatermark().toUpperCase(), wf);
                    com.lowagie.text.pdf.ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, p,
                            document.getPageSize().getWidth() / 2f,
                            document.getPageSize().getHeight() / 2f, 30);
                } catch (Exception ignore) {}
            }

            // Footer separator
            com.lowagie.text.pdf.PdfContentByte over = writer.getDirectContent();
            over.setLineWidth(0.6f);
            over.setRGBColorStroke(BORDER_LIGHT.getRed(), BORDER_LIGHT.getGreen(), BORDER_LIGHT.getBlue());
            over.moveTo(36, 32);
            over.lineTo(document.getPageSize().getWidth() - 36, 32);
            over.stroke();

            // Left: audit
            String left = "Generated via Vyaparsathi";
            if (doc.getAudit() != null && doc.getAudit().getDocumentHash() != null) {
                left += "  ·  Hash: " + doc.getAudit().getDocumentHash().substring(0,
                        Math.min(12, doc.getAudit().getDocumentHash().length()));
            }
            com.lowagie.text.pdf.ColumnText.showTextAligned(over, Element.ALIGN_LEFT,
                    new Phrase(left, footerFont), 36, 22, 0);

            // Centre: printed timestamp
            String ts = "Printed: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"));
            com.lowagie.text.pdf.ColumnText.showTextAligned(over, Element.ALIGN_CENTER,
                    new Phrase(ts, footerFont),
                    document.getPageSize().getWidth() / 2f, 22, 0);

            // Right: Page N
            String page = "Page " + writer.getPageNumber();
            com.lowagie.text.pdf.ColumnText.showTextAligned(over, Element.ALIGN_RIGHT,
                    new Phrase(page, footerFont),
                    document.getPageSize().getWidth() - 36, 22, 0);
        }
    }
}
