package com.desitech.vyaparsathi.accounting.service;

import com.desitech.vyaparsathi.accounting.entity.CreditNote;
import com.desitech.vyaparsathi.accounting.entity.CreditNoteItem;
import com.desitech.vyaparsathi.accounting.entity.DebitNote;
import com.desitech.vyaparsathi.accounting.entity.DebitNoteItem;
import com.desitech.vyaparsathi.accounting.repository.CreditNoteRepository;
import com.desitech.vyaparsathi.accounting.repository.DebitNoteRepository;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.common.exception.ExportAppException;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.invoice.utils.InvoiceUtil;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renders CreditNote and DebitNote PDFs.
 *
 * <p>Layout is a slim invoice: header + counterparty block + item table +
 * totals + footer. Uses the same OpenPDF design system as the redesigned
 * invoice/receipt PDFs so all Vyaparsathi documents look consistent.
 */
@Service
public class NotePdfService {

    private static final Logger logger = LoggerFactory.getLogger(NotePdfService.class);

    private static final Color BRAND_FALLBACK    = new Color(41, 128, 185);
    private static final Color SECTION_LABEL_BG  = new Color(243, 244, 246);
    private static final Color ROW_ALT_BG        = new Color(249, 250, 251);
    private static final Color BORDER_LIGHT      = new Color(229, 231, 235);
    private static final Color TEXT_MUTED        = new Color(107, 114, 128);
    private static final Color TEXT_STRONG       = new Color(17, 24, 39);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final java.text.NumberFormat currency =
            java.text.NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    private final CreditNoteRepository creditRepo;
    private final DebitNoteRepository debitRepo;

    public NotePdfService(CreditNoteRepository creditRepo, DebitNoteRepository debitRepo) {
        this.creditRepo = creditRepo;
        this.debitRepo = debitRepo;
    }

    @Transactional(readOnly = true)
    public byte[] generateCreditNotePdf(Long creditNoteId) {
        CreditNote note = creditRepo.findById(creditNoteId)
                .orElseThrow(() -> new EntityNotFoundAppException("Credit Note", creditNoteId));
        return renderCredit(note);
    }

    @Transactional(readOnly = true)
    public byte[] generateDebitNotePdf(Long debitNoteId) {
        DebitNote note = debitRepo.findById(debitNoteId)
                .orElseThrow(() -> new EntityNotFoundAppException("Debit Note", debitNoteId));
        return renderDebit(note);
    }

    // ─── Credit Note rendering ──────────────────────────────────────────

    private byte[] renderCredit(CreditNote note) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 40, 40);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Shop shop = note.getShop();
            Color brand = InvoiceUtil.parseColor(shop != null ? shop.getBrandColor() : null, BRAND_FALLBACK);

            renderHeader(doc, shop, "CREDIT NOTE", note.getCreditNoteNo(),
                    note.getCreditNoteDate() != null ? DATE_FMT.format(note.getCreditNoteDate()) : "", brand);

            // Counterparty
            Customer c = note.getCustomer();
            String counterpartyName = c != null ? c.getName() : "Walk-in Customer";
            String counterpartyPhone = c != null ? c.getPhone() : null;
            String reference = note.getSale() != null && note.getSale().getInvoiceNo() != null
                    ? "Against Invoice " + note.getSale().getInvoiceNo() : null;
            renderCounterparty(doc, "CREDITED TO", counterpartyName, counterpartyPhone,
                    c != null ? c.getAddressLine1() : null, reference, note.getReason());

            // Item table
            renderCreditItems(doc, note, brand);

            // Totals
            renderTotals(doc, note.getTaxableAmount(), note.getCgstAmount(), note.getSgstAmount(),
                    note.getIgstAmount(), note.getTotalAmount(), brand);

            renderFooter(doc, "This credit note has been issued in accordance with Section 34 of the CGST Act.");

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to render credit note PDF for id={}", note.getId(), e);
            throw new ExportAppException("Failed to render credit note PDF: " + note.getId(), e);
        }
    }

    private void renderCreditItems(Document doc, CreditNote note, Color brand) throws DocumentException {
        Font tableHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.NORMAL, Color.WHITE);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, TEXT_STRONG);
        Font bodyBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, TEXT_STRONG);

        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setSpacingBefore(14f);
        try { table.setWidths(new float[]{4, 30, 10, 8, 10, 12, 12, 14}); } catch (DocumentException ignored) {}
        table.setHeaderRows(1);

        String[] headers = {"#", "Item", "HSN/SAC", "Qty", "Rate", "Taxable", "GST", "Total"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, tableHeader));
            cell.setBackgroundColor(brand);
            cell.setBorder(Rectangle.BOX);
            cell.setBorderColor(brand);
            cell.setBorderWidth(0.5f);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPaddingTop(6);
            cell.setPaddingBottom(6);
            table.addCell(cell);
        }

        int rowNum = 1;
        if (note.getItems() != null) {
            for (CreditNoteItem item : note.getItems()) {
                Color rowBg = (rowNum % 2 == 0) ? ROW_ALT_BG : Color.WHITE;
                BigDecimal gst = safe(item.getCgstAmt()).add(safe(item.getSgstAmt())).add(safe(item.getIgstAmt()));
                table.addCell(bodyCell(String.valueOf(rowNum++), body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(item.getItemName() != null ? item.getItemName() : "-", body, Element.ALIGN_LEFT, rowBg));
                table.addCell(bodyCell(item.getHsnSac() != null ? item.getHsnSac() : "-", body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(item.getQty() != null ? item.getQty().toPlainString() : "-", body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(currency.format(safe(item.getUnitPrice())), body, Element.ALIGN_RIGHT, rowBg));
                table.addCell(bodyCell(currency.format(safe(item.getTaxableValue())), body, Element.ALIGN_RIGHT, rowBg));
                table.addCell(bodyCell(currency.format(gst), body, Element.ALIGN_RIGHT, rowBg));
                table.addCell(bodyCell(currency.format(safe(item.getTotalAmount())), bodyBold, Element.ALIGN_RIGHT, rowBg));
            }
        }
        doc.add(table);
    }

    // ─── Debit Note rendering ───────────────────────────────────────────

    private byte[] renderDebit(DebitNote note) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 40, 40);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Shop shop = note.getShop();
            Color brand = InvoiceUtil.parseColor(shop != null ? shop.getBrandColor() : null, BRAND_FALLBACK);

            renderHeader(doc, shop, "DEBIT NOTE", note.getDebitNoteNo(),
                    note.getDebitNoteDate() != null ? DATE_FMT.format(note.getDebitNoteDate()) : "", brand);

            Supplier s = note.getSupplier();
            String counterpartyName = s != null ? s.getSupplierName() : "Supplier";
            String counterpartyPhone = s != null ? s.getPhone() : null;
            String reference = note.getPurchaseReturn() != null && note.getPurchaseReturn().getReturnNo() != null
                    ? "Against Return " + note.getPurchaseReturn().getReturnNo()
                    : (note.getPurchaseInvoice() != null && note.getPurchaseInvoice().getPurchaseInvoiceNo() != null
                            ? "Against Purchase Invoice " + note.getPurchaseInvoice().getPurchaseInvoiceNo() : null);
            renderCounterparty(doc, "DEBITED TO", counterpartyName, counterpartyPhone, null, reference, note.getReason());

            renderDebitItems(doc, note, brand);

            renderTotals(doc, note.getTaxableAmount(), note.getCgstAmount(), note.getSgstAmount(),
                    note.getIgstAmount(), note.getTotalAmount(), brand);

            renderFooter(doc, "This debit note reduces the payable due to the supplier by the amount shown above.");

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to render debit note PDF for id={}", note.getId(), e);
            throw new ExportAppException("Failed to render debit note PDF: " + note.getId(), e);
        }
    }

    private void renderDebitItems(Document doc, DebitNote note, Color brand) throws DocumentException {
        Font tableHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.NORMAL, Color.WHITE);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, TEXT_STRONG);
        Font bodyBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, TEXT_STRONG);

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setSpacingBefore(14f);
        try { table.setWidths(new float[]{4, 34, 12, 10, 8, 14, 14}); } catch (DocumentException ignored) {}
        table.setHeaderRows(1);

        String[] headers = {"#", "Item", "HSN/SAC", "Batch", "Qty", "Unit Cost", "Total"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, tableHeader));
            cell.setBackgroundColor(brand);
            cell.setBorder(Rectangle.BOX);
            cell.setBorderColor(brand);
            cell.setBorderWidth(0.5f);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPaddingTop(6);
            cell.setPaddingBottom(6);
            table.addCell(cell);
        }

        int rowNum = 1;
        if (note.getItems() != null) {
            for (DebitNoteItem item : note.getItems()) {
                Color rowBg = (rowNum % 2 == 0) ? ROW_ALT_BG : Color.WHITE;
                table.addCell(bodyCell(String.valueOf(rowNum++), body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(item.getItemName() != null ? item.getItemName() : "-", body, Element.ALIGN_LEFT, rowBg));
                table.addCell(bodyCell(item.getHsnSac() != null ? item.getHsnSac() : "-", body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(item.getBatchNumber() != null ? item.getBatchNumber() : "-", body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(item.getQty() != null ? item.getQty().toPlainString() : "-", body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(currency.format(safe(item.getUnitCost())), body, Element.ALIGN_RIGHT, rowBg));
                table.addCell(bodyCell(currency.format(safe(item.getTotalAmount())), bodyBold, Element.ALIGN_RIGHT, rowBg));
            }
        }
        doc.add(table);
    }

    // ─── Shared rendering pieces ────────────────────────────────────────

    private void renderHeader(Document doc, Shop shop, String docTitle, String docNumber, String docDate, Color brand)
            throws DocumentException {
        Font titleFont     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Font.NORMAL, brand);
        Font metaLabel     = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);
        Font metaValue     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, TEXT_STRONG);
        Font small         = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);
        Font shopNameFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.NORMAL, TEXT_STRONG);

        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{60, 40});

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.addElement(new Paragraph(shop != null && shop.getName() != null ? shop.getName().toUpperCase() : "SHOP", shopNameFont));
        if (shop != null && shop.getAddress() != null) left.addElement(new Paragraph(shop.getAddress(), small));
        if (shop != null && shop.getGstin() != null) left.addElement(new Paragraph("GSTIN: " + shop.getGstin(), small));
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph title = new Paragraph(docTitle, titleFont);
        title.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(title);

        PdfPTable meta = new PdfPTable(2);
        meta.setTotalWidth(180);
        meta.setLockedWidth(true);
        meta.setHorizontalAlignment(Element.ALIGN_RIGHT);
        meta.setSpacingBefore(6f);
        addMetaRow(meta, "Note No", docNumber, metaLabel, metaValue);
        addMetaRow(meta, "Date", docDate, metaLabel, metaValue);
        right.addElement(meta);
        header.addCell(right);
        doc.add(header);

        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        rule.setSpacingBefore(10f);
        PdfPCell rc = new PdfPCell();
        rc.setFixedHeight(2f);
        rc.setBorder(Rectangle.BOTTOM);
        rc.setBorderColorBottom(brand);
        rc.setBorderWidthBottom(1.5f);
        rule.addCell(rc);
        doc.add(rule);
    }

    private void renderCounterparty(Document doc, String label, String name, String phone, String address,
                                    String reference, String reason) throws DocumentException {
        Font sectionLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.NORMAL, TEXT_MUTED);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, TEXT_STRONG);
        Font bodyBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.NORMAL, TEXT_STRONG);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);

        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(14f);

        PdfPCell strip = new PdfPCell(new Phrase(label, sectionLabel));
        strip.setBackgroundColor(SECTION_LABEL_BG);
        strip.setBorder(Rectangle.BOX);
        strip.setBorderColor(BORDER_LIGHT);
        strip.setBorderWidth(0.5f);
        strip.setPadding(6);
        t.addCell(strip);

        PdfPCell body_ = new PdfPCell();
        body_.setBorder(Rectangle.BOX);
        body_.setBorderColor(BORDER_LIGHT);
        body_.setBorderWidth(0.5f);
        body_.setPadding(10);
        body_.addElement(new Paragraph(name != null ? name : "-", bodyBold));
        if (phone != null && !phone.isBlank()) body_.addElement(new Paragraph(phone, body));
        if (address != null && !address.isBlank()) body_.addElement(new Paragraph(address, small));
        if (reference != null && !reference.isBlank()) body_.addElement(new Paragraph(reference, small));
        if (reason != null && !reason.isBlank()) body_.addElement(new Paragraph("Reason: " + reason, small));
        t.addCell(body_);
        doc.add(t);
    }

    private void renderTotals(Document doc, BigDecimal taxable, BigDecimal cgst, BigDecimal sgst,
                              BigDecimal igst, BigDecimal total, Color brand) throws DocumentException {
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, TEXT_STRONG);
        Font grandTotalWhite = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.NORMAL, Color.WHITE);

        PdfPTable wrap = new PdfPTable(2);
        wrap.setWidthPercentage(100);
        wrap.setSpacingBefore(14f);
        wrap.setWidths(new float[]{60, 40});

        // Empty left cell (amount in words could go here later)
        PdfPCell empty = new PdfPCell(new Phrase(""));
        empty.setBorder(Rectangle.NO_BORDER);
        wrap.addCell(empty);

        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(100);
        totals.setWidths(new float[]{55, 45});

        row(totals, "Taxable", currency.format(safe(taxable)), body);
        if (safe(cgst).compareTo(BigDecimal.ZERO) > 0) row(totals, "CGST", currency.format(cgst), body);
        if (safe(sgst).compareTo(BigDecimal.ZERO) > 0) row(totals, "SGST", currency.format(sgst), body);
        if (safe(igst).compareTo(BigDecimal.ZERO) > 0) row(totals, "IGST", currency.format(igst), body);

        // Grand Total bar
        PdfPCell l = new PdfPCell(new Phrase("TOTAL", grandTotalWhite));
        l.setBackgroundColor(brand);
        l.setBorder(Rectangle.NO_BORDER);
        l.setPaddingTop(7);
        l.setPaddingBottom(7);
        l.setPaddingLeft(8);
        totals.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(currency.format(safe(total)), grandTotalWhite));
        v.setBackgroundColor(brand);
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPaddingTop(7);
        v.setPaddingBottom(7);
        v.setPaddingRight(8);
        totals.addCell(v);

        PdfPCell right = new PdfPCell(totals);
        right.setBorder(Rectangle.BOX);
        right.setBorderColor(BORDER_LIGHT);
        right.setBorderWidth(0.5f);
        right.setPadding(6);
        wrap.addCell(right);
        doc.add(wrap);
    }

    private void renderFooter(Document doc, String note) throws DocumentException {
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Font.NORMAL, TEXT_MUTED);
        Paragraph p = new Paragraph(note, footerFont);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(24f);
        doc.add(p);
    }

    private void row(PdfPTable table, String label, String value, Font font) {
        PdfPCell l = new PdfPCell(new Phrase(label, font));
        l.setBorder(Rectangle.NO_BORDER);
        l.setPaddingTop(4); l.setPaddingBottom(4); l.setPaddingLeft(6);
        table.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(value, font));
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPaddingTop(4); v.setPaddingBottom(4); v.setPaddingRight(6);
        table.addCell(v);
    }

    private void addMetaRow(PdfPTable meta, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell l = new PdfPCell(new Phrase(label, labelFont));
        l.setBorder(Rectangle.NO_BORDER);
        l.setHorizontalAlignment(Element.ALIGN_RIGHT);
        l.setPaddingRight(6);
        l.setPaddingTop(2);
        l.setPaddingBottom(2);
        meta.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(value, valueFont));
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPaddingTop(2);
        v.setPaddingBottom(2);
        meta.addCell(v);
    }

    private PdfPCell bodyCell(String text, Font font, int align, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBackgroundColor(bg);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER_LIGHT);
        cell.setBorderWidth(0.5f);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPaddingTop(5); cell.setPaddingBottom(5);
        cell.setPaddingLeft(4); cell.setPaddingRight(4);
        return cell;
    }

    private static BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
}
