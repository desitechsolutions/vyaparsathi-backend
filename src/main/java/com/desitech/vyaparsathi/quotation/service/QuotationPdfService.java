package com.desitech.vyaparsathi.quotation.service;

import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.common.exception.ExportAppException;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.invoice.service.InvoicePageEvent;
import com.desitech.vyaparsathi.invoice.utils.InvoiceUtil;
import com.desitech.vyaparsathi.quotation.entity.Quotation;
import com.desitech.vyaparsathi.quotation.entity.QuotationItem;
import com.desitech.vyaparsathi.quotation.repository.QuotationRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
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
 * Renders a Quotation as a PDF using the redesigned invoice design system:
 * shop logo via {@link InvoicePageEvent}, brand-color header rule, section-label
 * strips, zebra-striped item table with GST column, per-rate GST summary,
 * amount-in-words, signature block, and standard footer strip. Differences vs
 * an invoice: title is "QUOTATION", prominent "Valid Until" date, and no
 * payment / balance-due card (a quotation is not a bill).
 */
@Service
public class QuotationPdfService {

    private static final Logger logger = LoggerFactory.getLogger(QuotationPdfService.class);

    private static final Color BRAND_FALLBACK    = new Color(41, 128, 185);
    private static final Color SECTION_LABEL_BG  = new Color(243, 244, 246);
    private static final Color ROW_ALT_BG        = new Color(249, 250, 251);
    private static final Color BORDER_LIGHT      = new Color(229, 231, 235);
    private static final Color TEXT_MUTED        = new Color(107, 114, 128);
    private static final Color TEXT_STRONG       = new Color(17, 24, 39);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final java.text.NumberFormat currency =
            java.text.NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    private final QuotationRepository quotationRepo;

    @Autowired
    private InvoiceUtil invoiceUtil;

    public QuotationPdfService(QuotationRepository quotationRepo) {
        this.quotationRepo = quotationRepo;
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(Long quotationId) {
        Quotation q = quotationRepo.findById(quotationId)
                .orElseThrow(() -> new EntityNotFoundAppException("Quotation", quotationId));
        return render(q);
    }

    private byte[] render(Quotation q) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 75, 45);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);

            Shop shop = q.getShop();
            Color brand = InvoiceUtil.parseColor(shop != null ? shop.getBrandColor() : null, BRAND_FALLBACK);

            // Logo + page-footer strip (identical treatment to the invoice)
            byte[] logoBytes = shop != null ? invoiceUtil.loadImageBytes(shop.getLogoPath(), "logo") : null;
            writer.setPageEvent(new InvoicePageEvent(logoBytes, brand));

            doc.open();

            Font titleFont     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Font.NORMAL, brand);
            Font sectionLabel  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.NORMAL, TEXT_MUTED);
            Font metaLabel     = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);
            Font metaValue     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, TEXT_STRONG);
            Font body          = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, TEXT_STRONG);
            Font bodyBold      = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, TEXT_STRONG);
            Font small         = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);
            Font tableHeader   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.NORMAL, Color.WHITE);
            Font grandTotalWhite = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.NORMAL, Color.WHITE);
            Font shopNameFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.NORMAL, TEXT_STRONG);

            renderHeader(doc, shop, q, brand, titleFont, metaLabel, metaValue, small, shopNameFont);
            renderCustomerBlock(doc, q, sectionLabel, body, bodyBold, small);
            renderItemsTable(doc, q, brand, tableHeader, body, bodyBold);
            renderTotalsAndGstSummary(doc, q, brand, body, bodyBold, small, sectionLabel, grandTotalWhite);
            renderTermsAndSignature(doc, q, shop, sectionLabel, body, bodyBold, small);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to render quotation PDF for id={}", q.getId(), e);
            throw new ExportAppException("Failed to render quotation PDF: " + q.getId(), e);
        }
    }

    // ─── Sections ────────────────────────────────────────────────────────

    private void renderHeader(Document doc, Shop shop, Quotation q, Color brand,
                              Font titleFont, Font metaLabel, Font metaValue,
                              Font small, Font shopNameFont) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{60, 40});

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPaddingTop(8);  // room for logo drawn by page-event
        Paragraph name = new Paragraph(shop != null && shop.getName() != null ? shop.getName().toUpperCase() : "SHOP", shopNameFont);
        name.setLeading(16f);
        left.addElement(name);

        StringBuilder sub = new StringBuilder();
        if (isNonBlank(shop != null ? shop.getAddress() : null)) sub.append(shop.getAddress());
        if (isNonBlank(shop != null ? shop.getGstin() : null))   appendLine(sub, "GSTIN: " + shop.getGstin());
        if (isNonBlank(shop != null ? shop.getPhone() : null))   appendLine(sub, "Phone: " + shop.getPhone());
        if (isNonBlank(shop != null ? shop.getEmail() : null))   appendLine(sub, "Email: " + shop.getEmail());
        if (isNonBlank(shop != null ? shop.getCompanyWebsite() : null))
            appendLine(sub, shop.getCompanyWebsite());
        if (isNonBlank(shop != null ? shop.getSupportContact() : null))
            appendLine(sub, shop.getSupportContact());
        if (sub.length() > 0) {
            Paragraph subP = new Paragraph(sub.toString(), small);
            subP.setLeading(11f);
            left.addElement(subP);
        }
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph title = new Paragraph("QUOTATION", titleFont);
        title.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(title);

        PdfPTable meta = new PdfPTable(2);
        meta.setTotalWidth(200);
        meta.setLockedWidth(true);
        meta.setHorizontalAlignment(Element.ALIGN_RIGHT);
        meta.setSpacingBefore(6f);
        addMetaRow(meta, "Quotation No", q.getQuotationNo(), metaLabel, metaValue);
        addMetaRow(meta, "Date",
                q.getQuotationDate() != null ? DATE_FMT.format(q.getQuotationDate().toLocalDate()) : "-",
                metaLabel, metaValue);
        if (q.getExpiryDate() != null) {
            addMetaRow(meta, "Valid Until", DATE_FMT.format(q.getExpiryDate()), metaLabel, metaValue);
        }
        addMetaRow(meta, "Status", q.getStatus() != null ? q.getStatus().name() : "-", metaLabel, metaValue);
        right.addElement(meta);
        header.addCell(right);
        doc.add(header);

        // Brand rule below header
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

    private void renderCustomerBlock(Document doc, Quotation q, Font sectionLabel,
                                     Font body, Font bodyBold, Font small) throws DocumentException {
        PdfPTable wrap = new PdfPTable(1);
        wrap.setWidthPercentage(100);
        wrap.setSpacingBefore(14f);
        wrap.addCell(sectionLabelStrip("QUOTED TO", sectionLabel));

        PdfPCell body_ = new PdfPCell();
        body_.setBorder(Rectangle.BOX);
        body_.setBorderColor(BORDER_LIGHT);
        body_.setBorderWidth(0.5f);
        body_.setPadding(10);

        Customer c = q.getCustomer();
        if (c != null) {
            Paragraph nm = new Paragraph(c.getName() != null ? c.getName() : "-", bodyBold);
            nm.setLeading(12f);
            body_.addElement(nm);
            if (isNonBlank(c.getPhone())) body_.addElement(new Paragraph(c.getPhone(), body));

            StringBuilder addr = new StringBuilder();
            if (isNonBlank(c.getAddressLine1())) addr.append(c.getAddressLine1());
            if (isNonBlank(c.getAddressLine2())) appendLine(addr, c.getAddressLine2());
            String cityState = formatCityState(c.getCity(), c.getState());
            String postal = isNonBlank(c.getPostalCode()) ? c.getPostalCode().trim() : "";
            String line3 = (cityState + " " + postal).trim();
            if (!line3.isEmpty()) appendLine(addr, line3);
            if (isNonBlank(c.getCountry())) appendLine(addr, c.getCountry());
            if (addr.length() > 0) {
                Paragraph a = new Paragraph(addr.toString(), body);
                a.setLeading(11f);
                body_.addElement(a);
            }
            // Only show GSTIN when set — no dangling "GSTIN:" label
            if (isNonBlank(c.getGstNumber())) {
                body_.addElement(new Paragraph("GSTIN: " + c.getGstNumber(), small));
            }
        } else {
            body_.addElement(new Paragraph("Walk-in Customer", body));
        }
        wrap.addCell(body_);
        doc.add(wrap);
    }

    private void renderItemsTable(Document doc, Quotation q, Color brand,
                                  Font tableHeader, Font body, Font bodyBold) throws DocumentException {
        boolean withGst = Boolean.TRUE.equals(q.getIsGstRequired());
        // Columns: #, Description, HSN/SAC, Qty, Unit, Rate, Disc, GST %, Taxable, Total
        // Without GST: skip the "GST %" column
        int cols = withGst ? 10 : 9;
        PdfPTable table = new PdfPTable(cols);
        table.setWidthPercentage(100);
        table.setSpacingBefore(14f);
        try {
            // Widen HSN so 8-digit codes don't wrap; narrow Rate/Disc/Taxable
            table.setWidths(withGst
                    ? new float[]{3, 26, 11, 5, 5, 9, 7, 6, 10, 13}
                    : new float[]{3, 30, 12, 6, 6, 10, 8, 11, 14});
        } catch (DocumentException ignored) {}
        table.setHeaderRows(1);

        java.util.List<String> headers = new java.util.ArrayList<>(java.util.Arrays.asList(
                "#", "Item Description", "HSN/SAC", "Qty", "Unit", "Rate", "Disc"));
        if (withGst) headers.add("GST %");
        headers.add("Taxable");
        headers.add("Total");

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

        // Small/muted font used for the second line of the item-description cell.
        Font smallMuted = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);
        Font bodyBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, TEXT_STRONG);

        int rowNum = 1;
        for (QuotationItem it : q.getItems()) {
            Color rowBg = (rowNum % 2 == 0) ? ROW_ALT_BG : Color.WHITE;
            table.addCell(bodyCell(String.valueOf(rowNum++), body, Element.ALIGN_CENTER, rowBg));
            ItemDescriptionParts desc = buildItemDescriptionParts(it);
            table.addCell(itemDescCell(desc.name, desc.attrs, bodyBoldFont, smallMuted, rowBg));
            table.addCell(bodyCell(displayHsn(it), body, Element.ALIGN_CENTER, rowBg));
            table.addCell(bodyCell(it.getQty() != null ? it.getQty().toPlainString() : "-", body, Element.ALIGN_CENTER, rowBg));
            table.addCell(bodyCell(displayUnit(it), body, Element.ALIGN_CENTER, rowBg));
            table.addCell(bodyCell(currency.format(nz(it.getUnitPrice())), body, Element.ALIGN_RIGHT, rowBg));
            table.addCell(bodyCell(currency.format(nz(it.getDiscount())), body, Element.ALIGN_RIGHT, rowBg));
            if (withGst) {
                Integer rate = it.getGstRate() != null ? it.getGstRate() : 0;
                table.addCell(bodyCell(rate + "%", body, Element.ALIGN_CENTER, rowBg));
            }
            table.addCell(bodyCell(currency.format(nz(it.getTaxableValue())), body, Element.ALIGN_RIGHT, rowBg));
            table.addCell(bodyCell(currency.format(nz(it.getLineTotal())), bodyBold, Element.ALIGN_RIGHT, rowBg));
        }
        doc.add(table);
    }

    private void renderTotalsAndGstSummary(Document doc, Quotation q, Color brand,
                                           Font body, Font bodyBold, Font small,
                                           Font sectionLabel, Font grandTotalWhite) throws DocumentException {
        PdfPTable main = new PdfPTable(2);
        main.setWidthPercentage(100);
        main.setWidths(new float[]{60, 40});
        main.setSpacingBefore(14f);

        // LEFT: GST breakdown per rate + amount in words
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(0);

        if (Boolean.TRUE.equals(q.getIsGstRequired())) {
            Map<Integer, GstSummaryRow> gstMap = new LinkedHashMap<>();
            for (QuotationItem it : q.getItems()) {
                int rate = it.getGstRate() != null ? it.getGstRate() : 0;
                GstSummaryRow row = gstMap.computeIfAbsent(rate, GstSummaryRow::new);
                row.cgst = row.cgst.add(nz(it.getCgstAmt()));
                row.sgst = row.sgst.add(nz(it.getSgstAmt()));
                row.igst = row.igst.add(nz(it.getIgstAmt()));
                row.taxable = row.taxable.add(nz(it.getTaxableValue()));
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
        }

        // Amount in words
        PdfPTable wordsWrap = new PdfPTable(1);
        wordsWrap.setWidthPercentage(100);
        wordsWrap.setSpacingBefore(8f);
        wordsWrap.addCell(sectionLabelStrip("AMOUNT IN WORDS", sectionLabel));
        PdfPCell wordsBody = new PdfPCell(new Phrase(
                InvoiceUtil.numberToWords(nz(q.getTotalAmount())) + " Only", body));
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
        totalRow(totals, "Taxable Amount", currency.format(nz(q.getTotalTaxableAmount())), body);
        if (nz(q.getTotalCgst()).signum() > 0) totalRow(totals, "Total CGST", currency.format(q.getTotalCgst()), body);
        if (nz(q.getTotalSgst()).signum() > 0) totalRow(totals, "Total SGST", currency.format(q.getTotalSgst()), body);
        if (nz(q.getTotalIgst()).signum() > 0) totalRow(totals, "Total IGST", currency.format(q.getTotalIgst()), body);
        if (nz(q.getInvoiceDiscount()).signum() > 0) totalRow(totals, "Discount", "- " + currency.format(q.getInvoiceDiscount()), body);
        if (nz(q.getShippingCharges()).signum() > 0) totalRow(totals, "Shipping", currency.format(q.getShippingCharges()), body);
        if (nz(q.getOtherCharges()).signum() > 0) totalRow(totals, "Other Charges", currency.format(q.getOtherCharges()), body);
        if (nz(q.getRoundOff()).signum() != 0) {
            BigDecimal ro = q.getRoundOff();
            String prefix = ro.signum() > 0 ? "+ " : "- ";
            totalRow(totals, "Round Off", prefix + currency.format(ro.abs()), body);
        }

        PdfPCell gtl = new PdfPCell(new Phrase("GRAND TOTAL", grandTotalWhite));
        gtl.setBackgroundColor(brand);
        gtl.setBorder(Rectangle.NO_BORDER);
        gtl.setPaddingTop(7); gtl.setPaddingBottom(7); gtl.setPaddingLeft(8);
        totals.addCell(gtl);
        PdfPCell gtv = new PdfPCell(new Phrase(currency.format(nz(q.getTotalAmount())), grandTotalWhite));
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

    private void renderTermsAndSignature(Document doc, Quotation q, Shop shop,
                                         Font sectionLabel, Font body, Font bodyBold, Font small)
            throws DocumentException {
        PdfPTable footer = new PdfPTable(2);
        footer.setWidthPercentage(100);
        footer.setWidths(new float[]{65, 35});
        footer.setSpacingBefore(16f);

        // Left: Terms & conditions
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        String terms = q.getTerms();
        if (!isNonBlank(terms) && shop != null) terms = shop.getTermsAndConditions();

        PdfPTable termsWrap = new PdfPTable(1);
        termsWrap.setWidthPercentage(100);
        termsWrap.addCell(sectionLabelStrip("TERMS & CONDITIONS", sectionLabel));
        PdfPCell termsBody = new PdfPCell();
        termsBody.setBorder(Rectangle.BOX);
        termsBody.setBorderColor(BORDER_LIGHT);
        termsBody.setBorderWidth(0.5f);
        termsBody.setPadding(8);
        String toRender = isNonBlank(terms) ? terms : "This quotation is valid until the date shown above. Prices are subject to change after expiry.";
        for (String line : toRender.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            Paragraph p = new Paragraph("• " + trimmed, small);
            p.setLeading(11f);
            termsBody.addElement(p);
        }
        termsWrap.addCell(termsBody);
        leftCell.addElement(termsWrap);
        footer.addCell(leftCell);

        // Right: Signature block (For SHOP / signature image / Authorized Signatory)
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
                logger.warn("Failed to render signature on quotation", e);
            }
        } else {
            Paragraph spacer = new Paragraph(" ");
            spacer.setSpacingAfter(40f);
            rightCell.addElement(spacer);
        }
        Paragraph label = new Paragraph("Authorized Signatory", small);
        label.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(label);

        footer.addCell(rightCell);
        doc.add(footer);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    /** Split representation of an item description: main line vs. attribute line. */
    private static final class ItemDescriptionParts {
        final String name;
        final String attrs;
        ItemDescriptionParts(String name, String attrs) {
            this.name = name;
            this.attrs = attrs;
        }
    }

    /**
     * Builds a two-part item descriptor. Same conventions as
     * {@code InvoiceService.buildItemDescriptionParts}: main line = item name
     * (with brand if present), secondary = dot-separated attribute values
     * without labels. SKUs are excluded (internal identifier, not customer-facing).
     */
    private ItemDescriptionParts buildItemDescriptionParts(QuotationItem it) {
        if (it.getItemVariant() == null) {
            String name = it.getCustomItemName() != null ? it.getCustomItemName()
                    : (it.getItemName() != null ? it.getItemName() : "Custom Item");
            String attrs = isNonBlank(it.getCustomDescription()) ? it.getCustomDescription() : null;
            return new ItemDescriptionParts(name, attrs);
        }

        ItemVariant v = it.getItemVariant();
        StringBuilder name = new StringBuilder(
                v.getItem() != null && v.getItem().getName() != null ? v.getItem().getName()
                        : (it.getItemName() != null ? it.getItemName() : "Item"));
        if (v.getItem() != null && isNonBlank(v.getItem().getBrandName())) {
            name.append(" — ").append(v.getItem().getBrandName());
        }

        java.util.StringJoiner attrs = new java.util.StringJoiner(" · ");
        if (isNonBlank(v.getColor()))  attrs.add(v.getColor());
        if (isNonBlank(v.getSize()))   attrs.add(v.getSize());
        if (isNonBlank(v.getDesign())) attrs.add(v.getDesign());
        if (isNonBlank(v.getFit()))    attrs.add(v.getFit());
        // SKU intentionally omitted — customer-facing document

        String attrsStr = attrs.length() == 0 ? null : attrs.toString();
        return new ItemDescriptionParts(name.toString(), attrsStr);
    }

    /**
     * Two-line item description cell: bold name on line 1, small-muted
     * attribute list on line 2 (when attrs is non-empty). Same visual
     * treatment as the invoice.
     */
    private PdfPCell itemDescCell(String name, String attrs, Font nameFont, Font attrsFont, Color bg) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER_LIGHT);
        cell.setBorderWidth(0.5f);
        cell.setPaddingTop(5);
        cell.setPaddingBottom(5);
        cell.setPaddingLeft(4);
        cell.setPaddingRight(4);

        Paragraph p1 = new Paragraph(name != null ? name : "", nameFont);
        p1.setLeading(11f);
        cell.addElement(p1);

        if (attrs != null && !attrs.isBlank()) {
            Paragraph p2 = new Paragraph(attrs, attrsFont);
            p2.setLeading(10f);
            cell.addElement(p2);
        }
        return cell;
    }

    private String displayHsn(QuotationItem it) {
        if (it.getItemVariant() != null && isNonBlank(it.getItemVariant().getHsn())) return it.getItemVariant().getHsn();
        if (isNonBlank(it.getCustomHsnSac())) return it.getCustomHsnSac();
        if (isNonBlank(it.getHsnSac())) return it.getHsnSac();
        return "-";
    }

    private String displayUnit(QuotationItem it) {
        if (it.getItemVariant() != null && isNonBlank(it.getItemVariant().getUnit())) return it.getItemVariant().getUnit();
        if (isNonBlank(it.getCustomUnit())) return it.getCustomUnit();
        if (isNonBlank(it.getUnit())) return it.getUnit();
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

    private static String formatCityState(String city, String state) {
        String c = city != null ? city.trim() : "";
        String s = state != null ? state.trim() : "";
        if (c.isEmpty() && s.isEmpty()) return "";
        if (c.isEmpty()) return s;
        if (s.isEmpty()) return c;
        return c + ", " + s;
    }

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    /** Aggregation row for the per-rate GST summary. */
    private static class GstSummaryRow {
        final int rate;
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;
        GstSummaryRow(int rate) { this.rate = rate; }
    }
}
