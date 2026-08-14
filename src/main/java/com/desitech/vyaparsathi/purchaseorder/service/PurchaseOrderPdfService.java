package com.desitech.vyaparsathi.purchaseorder.service;

import com.desitech.vyaparsathi.common.exception.ExportAppException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.invoice.service.InvoicePageEvent;
import com.desitech.vyaparsathi.invoice.utils.InvoiceUtil;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a Purchase Order as a PDF using the same design system as the
 * quotation / invoice: shop logo via {@link InvoicePageEvent}, brand-color
 * header rule, section-label strips, zebra-striped item table with GST column,
 * per-rate GST summary, amount-in-words, signature block. Differences vs a
 * quotation: title is "PURCHASE ORDER", the "quoted to" block is renamed
 * "SUPPLIER" and pulls from the Supplier entity, expected-delivery replaces
 * valid-until, and there is no expiry / customer / payment status.
 */
@Service
public class PurchaseOrderPdfService {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderPdfService.class);

    private static final Color BRAND_FALLBACK    = new Color(41, 128, 185);
    private static final Color SECTION_LABEL_BG  = new Color(243, 244, 246);
    private static final Color ROW_ALT_BG        = new Color(249, 250, 251);
    private static final Color BORDER_LIGHT      = new Color(229, 231, 235);
    private static final Color TEXT_MUTED        = new Color(107, 114, 128);
    private static final Color TEXT_STRONG       = new Color(17, 24, 39);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final java.text.NumberFormat currency =
            java.text.NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    private final PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private InvoiceUtil invoiceUtil;

    public PurchaseOrderPdfService(PurchaseOrderRepository purchaseOrderRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(Long purchaseOrderId) {
        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found: " + purchaseOrderId));
        return render(po);
    }

    private byte[] render(PurchaseOrder po) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 75, 45);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);

            Shop shop = po.getShop();
            Color brand = InvoiceUtil.parseColor(shop != null ? shop.getBrandColor() : null, BRAND_FALLBACK);

            byte[] logoBytes = shop != null ? invoiceUtil.loadImageBytes(shop.getLogoPath(), "logo") : null;
            writer.setPageEvent(new InvoicePageEvent(logoBytes, brand));

            doc.open();

            Font titleFont       = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Font.NORMAL, brand);
            Font sectionLabel    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.NORMAL, TEXT_MUTED);
            Font metaLabel       = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);
            Font metaValue       = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, TEXT_STRONG);
            Font body            = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, TEXT_STRONG);
            Font bodyBold        = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, TEXT_STRONG);
            Font small           = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);
            Font tableHeader     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.NORMAL, Color.WHITE);
            Font grandTotalWhite = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.NORMAL, Color.WHITE);
            Font shopNameFont    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.NORMAL, TEXT_STRONG);

            renderHeader(doc, shop, po, brand, titleFont, metaLabel, metaValue, small, shopNameFont);
            renderSupplierBlock(doc, po, sectionLabel, body, bodyBold, small);
            renderItemsTable(doc, po, brand, tableHeader, body, bodyBold);
            renderTotalsAndGstSummary(doc, po, brand, body, bodyBold, small, sectionLabel, grandTotalWhite);
            renderSignature(doc, shop, sectionLabel, body, bodyBold, small);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to render PO PDF for id={}", po.getId(), e);
            throw new ExportAppException("Failed to render PO PDF: " + po.getId(), e);
        }
    }

    // ─── Sections ────────────────────────────────────────────────────────

    private void renderHeader(Document doc, Shop shop, PurchaseOrder po, Color brand,
                              Font titleFont, Font metaLabel, Font metaValue,
                              Font small, Font shopNameFont) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{60, 40});

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPaddingTop(8);
        Paragraph name = new Paragraph(shop != null && shop.getName() != null ? shop.getName().toUpperCase() : "SHOP", shopNameFont);
        name.setLeading(16f);
        left.addElement(name);

        StringBuilder sub = new StringBuilder();
        if (isNonBlank(shop != null ? shop.getAddress() : null)) sub.append(shop.getAddress());
        if (isNonBlank(shop != null ? shop.getGstin() : null))   appendLine(sub, "GSTIN: " + shop.getGstin());
        if (isNonBlank(shop != null ? shop.getPhone() : null))   appendLine(sub, "Phone: " + shop.getPhone());
        if (isNonBlank(shop != null ? shop.getEmail() : null))   appendLine(sub, "Email: " + shop.getEmail());
        if (sub.length() > 0) {
            Paragraph subP = new Paragraph(sub.toString(), small);
            subP.setLeading(11f);
            left.addElement(subP);
        }
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph title = new Paragraph("PURCHASE ORDER", titleFont);
        title.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(title);

        PdfPTable meta = new PdfPTable(2);
        meta.setTotalWidth(220);
        meta.setLockedWidth(true);
        meta.setHorizontalAlignment(Element.ALIGN_RIGHT);
        meta.setSpacingBefore(6f);
        addMetaRow(meta, "PO No", po.getPoNumber() != null ? po.getPoNumber() : "-", metaLabel, metaValue);
        addMetaRow(meta, "Order Date",
                po.getOrderDate() != null ? DATE_FMT.format(po.getOrderDate().toLocalDate()) : "-",
                metaLabel, metaValue);
        if (po.getExpectedDeliveryDate() != null) {
            addMetaRow(meta, "Expected Delivery",
                    DATE_FMT.format(po.getExpectedDeliveryDate().toLocalDate()),
                    metaLabel, metaValue);
        }
        addMetaRow(meta, "Status",
                po.getStatus() != null ? po.getStatus().name().replace('_', ' ') : "-",
                metaLabel, metaValue);
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

    private void renderSupplierBlock(Document doc, PurchaseOrder po, Font sectionLabel,
                                     Font body, Font bodyBold, Font small) throws DocumentException {
        PdfPTable wrap = new PdfPTable(1);
        wrap.setWidthPercentage(100);
        wrap.setSpacingBefore(14f);
        wrap.addCell(sectionLabelStrip("SUPPLIER", sectionLabel));

        PdfPCell body_ = new PdfPCell();
        body_.setBorder(Rectangle.BOX);
        body_.setBorderColor(BORDER_LIGHT);
        body_.setBorderWidth(0.5f);
        body_.setPadding(10);

        Supplier s = po.getSupplier();
        if (s != null) {
            Paragraph nm = new Paragraph(s.getName() != null ? s.getName() : "-", bodyBold);
            nm.setLeading(12f);
            body_.addElement(nm);
            if (isNonBlank(s.getPhone())) body_.addElement(new Paragraph(s.getPhone(), body));
            if (isNonBlank(s.getEmail())) body_.addElement(new Paragraph(s.getEmail(), body));
            if (isNonBlank(s.getAddress())) {
                Paragraph a = new Paragraph(s.getAddress(), body);
                a.setLeading(11f);
                body_.addElement(a);
            }
            if (isNonBlank(s.getGstin())) {
                body_.addElement(new Paragraph("GSTIN: " + s.getGstin(), small));
            }
        } else {
            body_.addElement(new Paragraph("(no supplier on record)", body));
        }
        wrap.addCell(body_);
        doc.add(wrap);
    }

    private void renderItemsTable(Document doc, PurchaseOrder po, Color brand,
                                  Font tableHeader, Font body, Font bodyBold) throws DocumentException {
        // Columns: #, Item, HSN, Qty, Unit Cost, Disc, GST %, Taxable, Line Total
        int cols = 9;
        PdfPTable table = new PdfPTable(cols);
        table.setWidthPercentage(100);
        table.setSpacingBefore(14f);
        try {
            table.setWidths(new float[]{3, 30, 11, 6, 9, 7, 6, 12, 16});
        } catch (DocumentException ignored) {}
        table.setHeaderRows(1);

        String[] headers = {"#", "Item", "HSN", "Qty", "Unit Cost", "Disc", "GST %", "Taxable", "Line Total"};
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

        Font smallMuted = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);

        int rowNum = 1;
        if (po.getItems() != null) {
            for (PurchaseOrderItem it : po.getItems()) {
                Color rowBg = (rowNum % 2 == 0) ? ROW_ALT_BG : Color.WHITE;
                table.addCell(bodyCell(String.valueOf(rowNum++), body, Element.ALIGN_CENTER, rowBg));
                table.addCell(itemDescCell(itemName(it), itemSku(it), bodyBold, smallMuted, rowBg));
                table.addCell(bodyCell(displayHsn(it), body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(it.getQuantity() != null ? it.getQuantity().toString() : "-",
                        body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(currency.format(nz(it.getUnitCost())), body, Element.ALIGN_RIGHT, rowBg));
                table.addCell(bodyCell(currency.format(nz(it.getDiscount())), body, Element.ALIGN_RIGHT, rowBg));
                Integer rate = it.getGstRate() != null ? it.getGstRate() : 0;
                table.addCell(bodyCell(rate > 0 ? rate + "%" : "-", body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(currency.format(nz(it.getTaxableValue())), body, Element.ALIGN_RIGHT, rowBg));
                table.addCell(bodyCell(currency.format(nz(it.getLineTotal())), bodyBold, Element.ALIGN_RIGHT, rowBg));
            }
        }
        doc.add(table);
    }

    private void renderTotalsAndGstSummary(Document doc, PurchaseOrder po, Color brand,
                                           Font body, Font bodyBold, Font small,
                                           Font sectionLabel, Font grandTotalWhite) throws DocumentException {
        PdfPTable main = new PdfPTable(2);
        main.setWidthPercentage(100);
        main.setWidths(new float[]{60, 40});
        main.setSpacingBefore(14f);

        // LEFT: per-rate GST breakdown + amount in words
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(0);

        Map<Integer, GstSummaryRow> gstMap = new LinkedHashMap<>();
        if (po.getItems() != null) {
            for (PurchaseOrderItem it : po.getItems()) {
                int rate = it.getGstRate() != null ? it.getGstRate() : 0;
                if (rate == 0 && nz(it.getCgstAmt()).signum() == 0
                        && nz(it.getSgstAmt()).signum() == 0 && nz(it.getIgstAmt()).signum() == 0) {
                    continue; // no tax on this line — skip from the summary
                }
                GstSummaryRow row = gstMap.computeIfAbsent(rate, GstSummaryRow::new);
                row.cgst = row.cgst.add(nz(it.getCgstAmt()));
                row.sgst = row.sgst.add(nz(it.getSgstAmt()));
                row.igst = row.igst.add(nz(it.getIgstAmt()));
                row.taxable = row.taxable.add(nz(it.getTaxableValue()));
            }
        }
        if (!gstMap.isEmpty()) {
            PdfPTable gstWrap = new PdfPTable(1);
            gstWrap.setWidthPercentage(100);
            gstWrap.addCell(sectionLabelStrip("GST SUMMARY", sectionLabel));

            PdfPTable gstTable = new PdfPTable(5);
            gstTable.setWidthPercentage(100);
            try { gstTable.setWidths(new float[]{12, 22, 22, 22, 22}); } catch (DocumentException ignored) {}
            for (String h : new String[]{"Rate", "Taxable", "CGST", "SGST", "IGST"}) {
                PdfPCell c = new PdfPCell(new Phrase(h, bodyBold));
                c.setBackgroundColor(SECTION_LABEL_BG);
                c.setBorder(Rectangle.BOX);
                c.setBorderColor(BORDER_LIGHT);
                c.setBorderWidth(0.5f);
                c.setPadding(5);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                gstTable.addCell(c);
            }
            for (GstSummaryRow r : gstMap.values()) {
                gstTable.addCell(bodyCell(r.rate + "%", body, Element.ALIGN_CENTER, Color.WHITE));
                gstTable.addCell(bodyCell(currency.format(r.taxable), body, Element.ALIGN_RIGHT, Color.WHITE));
                gstTable.addCell(bodyCell(currency.format(r.cgst),    body, Element.ALIGN_RIGHT, Color.WHITE));
                gstTable.addCell(bodyCell(currency.format(r.sgst),    body, Element.ALIGN_RIGHT, Color.WHITE));
                gstTable.addCell(bodyCell(currency.format(r.igst),    body, Element.ALIGN_RIGHT, Color.WHITE));
            }
            PdfPCell gstBox = new PdfPCell(gstTable);
            gstBox.setBorder(Rectangle.NO_BORDER);
            gstBox.setPadding(0);
            gstWrap.addCell(gstBox);
            left.addElement(gstWrap);
        }

        // Amount in words
        PdfPTable wordsWrap = new PdfPTable(1);
        wordsWrap.setWidthPercentage(100);
        wordsWrap.setSpacingBefore(8f);
        wordsWrap.addCell(sectionLabelStrip("AMOUNT IN WORDS", sectionLabel));
        PdfPCell wordsBody = new PdfPCell(new Phrase(
                InvoiceUtil.numberToWords(nz(po.getTotalAmount())) + " Only", body));
        wordsBody.setBorder(Rectangle.BOX);
        wordsBody.setBorderColor(BORDER_LIGHT);
        wordsBody.setBorderWidth(0.5f);
        wordsBody.setPadding(8);
        wordsWrap.addCell(wordsBody);
        left.addElement(wordsWrap);

        main.addCell(left);

        // RIGHT: totals stack
        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(100);
        totals.setWidths(new float[]{55, 45});
        totalRow(totals, "Subtotal", currency.format(nz(po.getSubtotal())), body);
        if (nz(po.getTotalDiscount()).signum() > 0) {
            totalRow(totals, "Line Discounts", "- " + currency.format(po.getTotalDiscount()), body);
        }
        if (nz(po.getTotalTax()).signum() > 0) {
            totalRow(totals, "Total GST", currency.format(po.getTotalTax()), body);
        }
        if (nz(po.getFreightCharges()).signum() > 0) {
            totalRow(totals, "Freight", "+ " + currency.format(po.getFreightCharges()), body);
        }
        if (nz(po.getRoundOff()).signum() != 0) {
            BigDecimal ro = po.getRoundOff();
            String prefix = ro.signum() > 0 ? "+ " : "- ";
            totalRow(totals, "Round Off", prefix + currency.format(ro.abs()), body);
        }

        PdfPCell gtl = new PdfPCell(new Phrase("GRAND TOTAL", grandTotalWhite));
        gtl.setBackgroundColor(brand);
        gtl.setBorder(Rectangle.NO_BORDER);
        gtl.setPaddingTop(7); gtl.setPaddingBottom(7); gtl.setPaddingLeft(8);
        totals.addCell(gtl);
        PdfPCell gtv = new PdfPCell(new Phrase(currency.format(nz(po.getTotalAmount())), grandTotalWhite));
        gtv.setBackgroundColor(brand);
        gtv.setBorder(Rectangle.NO_BORDER);
        gtv.setHorizontalAlignment(Element.ALIGN_RIGHT);
        gtv.setPaddingTop(7); gtv.setPaddingBottom(7); gtv.setPaddingRight(8);
        totals.addCell(gtv);

        PdfPCell rightCell = new PdfPCell(totals);
        rightCell.setBorder(Rectangle.BOX);
        rightCell.setBorderColor(BORDER_LIGHT);
        rightCell.setBorderWidth(0.5f);
        rightCell.setPadding(6);
        main.addCell(rightCell);
        doc.add(main);
    }

    private void renderSignature(Document doc, Shop shop, Font sectionLabel,
                                 Font body, Font bodyBold, Font small) throws DocumentException {
        PdfPTable footer = new PdfPTable(2);
        footer.setWidthPercentage(100);
        footer.setWidths(new float[]{65, 35});
        footer.setSpacingBefore(16f);

        // Left: PO notes (matches the on-screen "Notes" block if present)
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);

        PdfPTable notesWrap = new PdfPTable(1);
        notesWrap.setWidthPercentage(100);
        notesWrap.addCell(sectionLabelStrip("NOTES", sectionLabel));
        PdfPCell notesBody = new PdfPCell();
        notesBody.setBorder(Rectangle.BOX);
        notesBody.setBorderColor(BORDER_LIGHT);
        notesBody.setBorderWidth(0.5f);
        notesBody.setPadding(8);
        notesBody.setFixedHeight(60f);
        Paragraph placeholder = new Paragraph("Please dispatch to the address above. Confirm receipt via email.", small);
        placeholder.setLeading(11f);
        notesBody.addElement(placeholder);
        notesWrap.addCell(notesBody);
        leftCell.addElement(notesWrap);
        footer.addCell(leftCell);

        // Right: authorised signatory block
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rightCell.setVerticalAlignment(Element.ALIGN_TOP);
        rightCell.setPaddingLeft(10);

        Paragraph shopName = new Paragraph(
                "For " + (shop != null && shop.getName() != null ? shop.getName().toUpperCase() : "SHOP"),
                bodyBold);
        shopName.setAlignment(Element.ALIGN_RIGHT);
        shopName.setSpacingAfter(4f);
        rightCell.addElement(shopName);

        byte[] sigBytes = shop != null ? invoiceUtil.loadImageBytes(shop.getSignaturePath(), "signature") : null;
        if (sigBytes != null) {
            try {
                Image sigImg = Image.getInstance(sigBytes);
                sigImg.scaleToFit(130, 60);
                sigImg.setAlignment(Image.RIGHT);
                rightCell.addElement(sigImg);
            } catch (Exception e) {
                logger.warn("Failed to render signature on PO", e);
            }
        } else {
            Paragraph spacer = new Paragraph(" ");
            spacer.setSpacingAfter(40f);
            rightCell.addElement(spacer);
        }
        Paragraph label = new Paragraph("Authorised Signatory", small);
        label.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(label);

        footer.addCell(rightCell);
        doc.add(footer);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private String itemName(PurchaseOrderItem it) {
        if (it.getItemVariant() != null && it.getItemVariant().getItem() != null
                && isNonBlank(it.getItemVariant().getItem().getName())) {
            return it.getItemVariant().getItem().getName();
        }
        return "Item";
    }

    private String itemSku(PurchaseOrderItem it) {
        if (it.getItemVariant() != null && isNonBlank(it.getItemVariant().getSku())) {
            return it.getItemVariant().getSku();
        }
        return null;
    }

    private String displayHsn(PurchaseOrderItem it) {
        if (isNonBlank(it.getHsnCode())) return it.getHsnCode();
        if (it.getItemVariant() != null && isNonBlank(it.getItemVariant().getHsn())) {
            return it.getItemVariant().getHsn();
        }
        return "-";
    }

    private void addMetaRow(PdfPTable meta, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell l = new PdfPCell(new Phrase(label, labelFont));
        l.setBorder(Rectangle.NO_BORDER);
        l.setHorizontalAlignment(Element.ALIGN_RIGHT);
        l.setPaddingRight(6); l.setPaddingTop(2); l.setPaddingBottom(2);
        meta.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(value, valueFont));
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPaddingTop(2); v.setPaddingBottom(2);
        meta.addCell(v);
    }

    private PdfPCell sectionLabelStrip(String label, Font labelFont) {
        PdfPCell cell = new PdfPCell(new Phrase(label, labelFont));
        cell.setBackgroundColor(SECTION_LABEL_BG);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER_LIGHT);
        cell.setBorderWidth(0.5f);
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        return cell;
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

    private PdfPCell itemDescCell(String name, String sku, Font nameFont, Font subFont, Color bg) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER_LIGHT);
        cell.setBorderWidth(0.5f);
        cell.setPaddingTop(5); cell.setPaddingBottom(5);
        cell.setPaddingLeft(4); cell.setPaddingRight(4);

        Paragraph p1 = new Paragraph(name != null ? name : "", nameFont);
        p1.setLeading(11f);
        cell.addElement(p1);

        if (sku != null && !sku.isBlank()) {
            Paragraph p2 = new Paragraph(sku, subFont);
            p2.setLeading(10f);
            cell.addElement(p2);
        }
        return cell;
    }

    private void totalRow(PdfPTable table, String label, String value, Font font) {
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

    private static boolean isNonBlank(String s) { return s != null && !s.trim().isEmpty(); }

    private static void appendLine(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append("\n");
        sb.append(s);
    }

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private static class GstSummaryRow {
        final int rate;
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;
        GstSummaryRow(int rate) { this.rate = rate; }
    }
}