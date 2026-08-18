package com.desitech.vyaparsathi.purchasereturn.service;

import com.desitech.vyaparsathi.common.exception.ExportAppException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.invoice.service.InvoicePageEvent;
import com.desitech.vyaparsathi.invoice.utils.InvoiceUtil;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.purchasereturn.entity.PurchaseReturn;
import com.desitech.vyaparsathi.purchasereturn.entity.PurchaseReturnItem;
import com.desitech.vyaparsathi.purchasereturn.repository.PurchaseReturnRepository;
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

/**
 * Server-rendered PDF for the Goods Return Note / Debit-back document. Uses
 * the same design system as GRN / PO PDFs — shared {@link InvoicePageEvent},
 * brand-colour header rule, section-label strips, zebra-striped line table.
 * Delivered via a signed URL so shops can email it to suppliers without
 * exposing session cookies.
 */
@Service
public class PurchaseReturnPdfService {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseReturnPdfService.class);

    private static final Color BRAND_FALLBACK    = new Color(41, 128, 185);
    private static final Color SECTION_LABEL_BG  = new Color(243, 244, 246);
    private static final Color ROW_ALT_BG        = new Color(249, 250, 251);
    private static final Color BORDER_LIGHT      = new Color(229, 231, 235);
    private static final Color TEXT_MUTED        = new Color(107, 114, 128);
    private static final Color TEXT_STRONG       = new Color(17, 24, 39);
    private static final Color ALERT_RED         = new Color(220, 38, 38);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final PurchaseReturnRepository repository;

    @Autowired
    private InvoiceUtil invoiceUtil;

    @Autowired
    private com.desitech.vyaparsathi.document.mapper.PurchaseReturnDocumentMapper prDocumentMapper;

    @Autowired
    private com.desitech.vyaparsathi.document.render.EnterpriseDocumentRenderer enterpriseRenderer;

    @Autowired
    private com.desitech.vyaparsathi.document.service.DocumentPrintAuditService printAuditService;

    @org.springframework.beans.factory.annotation.Value("${purchase-return.enterprise-pdf.enabled:true}")
    private boolean enterprisePdfEnabled;

    public PurchaseReturnPdfService(PurchaseReturnRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(Long id) {
        PurchaseReturn r = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Return not found: " + id));
        if (enterprisePdfEnabled) {
            com.desitech.vyaparsathi.document.dto.EnterpriseDocumentDto d = prDocumentMapper.map(r);
            if (r.getShop() != null && r.getShop().getLogoPath() != null) {
                d.setLogoBytes(invoiceUtil.loadImageBytes(r.getShop().getLogoPath(), "logo"));
            }
            if (r.getShop() != null && r.getShop().getSignaturePath() != null) {
                d.setSignatureBytes(invoiceUtil.loadImageBytes(r.getShop().getSignaturePath(), "signature"));
            }
            byte[] pdf = enterpriseRenderer.render(d);
            printAuditService.recordPrint(d.getDocumentType(), r.getId(),
                    r.getReturnNo(),
                    d.getAudit() != null ? d.getAudit().getDocumentHash() : null);
            return pdf;
        }
        return render(r);
    }

    private byte[] render(PurchaseReturn r) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 75, 45);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);

            Shop shop = r.getShop();
            Color brand = InvoiceUtil.parseColor(shop != null ? shop.getBrandColor() : null, BRAND_FALLBACK);

            byte[] logoBytes = shop != null ? invoiceUtil.loadImageBytes(shop.getLogoPath(), "logo") : null;
            writer.setPageEvent(new InvoicePageEvent(logoBytes, brand));

            doc.open();

            Font titleFont    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Font.NORMAL, brand);
            Font sectionLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.NORMAL, TEXT_MUTED);
            Font metaLabel    = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);
            Font metaValue    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, TEXT_STRONG);
            Font body         = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, TEXT_STRONG);
            Font bodyBold     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, TEXT_STRONG);
            Font small        = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);
            Font tableHeader  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.NORMAL, Color.WHITE);
            Font shopNameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.NORMAL, TEXT_STRONG);
            Font statusBanner = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.NORMAL, ALERT_RED);

            renderHeader(doc, shop, r, brand, titleFont, metaLabel, metaValue, small, shopNameFont);
            renderSupplierBlock(doc, r, sectionLabel, body, bodyBold, small);
            if ("DRAFT".equals(r.getStatus() != null ? r.getStatus().name() : "")) {
                Paragraph draft = new Paragraph("DRAFT — NOT YET APPROVED", statusBanner);
                draft.setAlignment(Element.ALIGN_CENTER);
                draft.setSpacingBefore(10f);
                doc.add(draft);
            }
            renderItemsTable(doc, r, brand, tableHeader, body, bodyBold);
            renderTotals(doc, r, sectionLabel, body, bodyBold);
            renderNotesAndSignature(doc, shop, r, sectionLabel, body, bodyBold, small);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to render Purchase Return PDF for id={}", r.getId(), e);
            throw new ExportAppException("Failed to render Purchase Return PDF: " + r.getId(), e);
        }
    }

    private void renderHeader(Document doc, Shop shop, PurchaseReturn r, Color brand,
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
        Paragraph title = new Paragraph("GOODS RETURN NOTE", titleFont);
        title.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(title);

        PdfPTable meta = new PdfPTable(2);
        meta.setTotalWidth(220);
        meta.setLockedWidth(true);
        meta.setHorizontalAlignment(Element.ALIGN_RIGHT);
        meta.setSpacingBefore(6f);
        addMetaRow(meta, "Return No", r.getReturnNo() != null ? r.getReturnNo() : "-", metaLabel, metaValue);
        addMetaRow(meta, "Return Date",
                r.getReturnDate() != null ? DATETIME_FMT.format(r.getReturnDate()) : "-",
                metaLabel, metaValue);
        if (r.getPurchaseOrder() != null && r.getPurchaseOrder().getPoNumber() != null) {
            addMetaRow(meta, "PO Ref", r.getPurchaseOrder().getPoNumber(), metaLabel, metaValue);
        }
        if (r.getReceiving() != null && r.getReceiving().getGrNumber() != null) {
            addMetaRow(meta, "GRN Ref", r.getReceiving().getGrNumber(), metaLabel, metaValue);
        }
        addMetaRow(meta, "Status",
                r.getStatus() != null ? r.getStatus().name() : "-",
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

    private void renderSupplierBlock(Document doc, PurchaseReturn r, Font sectionLabel,
                                     Font body, Font bodyBold, Font small) throws DocumentException {
        PdfPTable wrap = new PdfPTable(1);
        wrap.setWidthPercentage(100);
        wrap.setSpacingBefore(14f);
        wrap.addCell(sectionLabelStrip("RETURN TO SUPPLIER", sectionLabel));

        PdfPCell body_ = new PdfPCell();
        body_.setBorder(Rectangle.BOX);
        body_.setBorderColor(BORDER_LIGHT);
        body_.setBorderWidth(0.5f);
        body_.setPadding(10);

        Supplier s = r.getSupplier();
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

    private void renderItemsTable(Document doc, PurchaseReturn r, Color brand,
                                  Font tableHeader, Font body, Font bodyBold) throws DocumentException {
        int cols = 6;
        PdfPTable table = new PdfPTable(cols);
        table.setWidthPercentage(100);
        table.setSpacingBefore(14f);
        try {
            table.setWidths(new float[]{3, 34, 12, 10, 12, 15});
        } catch (DocumentException ignored) {}
        table.setHeaderRows(1);

        String[] headers = {"#", "Item / Reason", "Batch", "Qty", "Unit Cost", "Line Total"};
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
        if (r.getItems() != null) {
            for (PurchaseReturnItem it : r.getItems()) {
                Color rowBg = (rowNum % 2 == 0) ? ROW_ALT_BG : Color.WHITE;
                table.addCell(bodyCell(String.valueOf(rowNum++), body, Element.ALIGN_CENTER, rowBg));
                table.addCell(itemDescCell(itemName(it), it.getReason(), bodyBold, smallMuted, rowBg));
                table.addCell(bodyCell(it.getBatchNumber() != null ? it.getBatchNumber() : "-", body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(String.valueOf(it.getQuantity() != null ? it.getQuantity() : 0),
                        body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell("₹" + safeAmount(it.getUnitCost()),
                        body, Element.ALIGN_RIGHT, rowBg));
                table.addCell(bodyCell("₹" + safeAmount(it.getTotalCost()),
                        bodyBold, Element.ALIGN_RIGHT, rowBg));
            }
        }
        doc.add(table);
    }

    private void renderTotals(Document doc, PurchaseReturn r, Font sectionLabel,
                              Font body, Font bodyBold) throws DocumentException {
        PdfPTable wrap = new PdfPTable(2);
        wrap.setWidthPercentage(100);
        wrap.setWidths(new float[]{60, 40});
        wrap.setSpacingBefore(10f);

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        wrap.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);

        PdfPTable totals = new PdfPTable(2);
        totals.setWidths(new float[]{50, 50});
        totals.setWidthPercentage(100);

        PdfPCell l = new PdfPCell(new Phrase("Return Total", bodyBold));
        l.setBorder(Rectangle.NO_BORDER);
        l.setPaddingTop(6);
        l.setPaddingBottom(6);
        totals.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase("₹" + safeAmount(r.getTotalAmount()), bodyBold));
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPaddingTop(6);
        v.setPaddingBottom(6);
        totals.addCell(v);
        right.addElement(totals);
        wrap.addCell(right);
        doc.add(wrap);
    }

    private void renderNotesAndSignature(Document doc, Shop shop, PurchaseReturn r, Font sectionLabel,
                                         Font body, Font bodyBold, Font small) throws DocumentException {
        PdfPTable footer = new PdfPTable(2);
        footer.setWidthPercentage(100);
        footer.setWidths(new float[]{65, 35});
        footer.setSpacingBefore(16f);

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
        String noteText = isNonBlank(r.getNotes()) ? r.getNotes() : "Returned to supplier per receiving inspection.";
        Paragraph notesP = new Paragraph(noteText, small);
        notesP.setLeading(11f);
        notesBody.addElement(notesP);
        notesWrap.addCell(notesBody);
        leftCell.addElement(notesWrap);
        footer.addCell(leftCell);

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
                logger.warn("Failed to render signature on RTV", e);
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

    // ── Helpers ──────────────────────────────────────────────────────────

    private String itemName(PurchaseReturnItem it) {
        ItemVariant v = it.getItemVariant();
        if (v != null && v.getItem() != null && isNonBlank(v.getItem().getName())) {
            return v.getItem().getName() + (isNonBlank(v.getSku()) ? " (" + v.getSku() + ")" : "");
        }
        return "Item";
    }

    private String safeAmount(BigDecimal v) {
        return v != null ? v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "0.00";
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

    private PdfPCell itemDescCell(String name, String reason, Font nameFont, Font subFont, Color bg) {
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

        if (reason != null && !reason.isBlank()) {
            Paragraph p2 = new Paragraph(reason, subFont);
            p2.setLeading(10f);
            cell.addElement(p2);
        }
        return cell;
    }

    private static boolean isNonBlank(String s) { return s != null && !s.trim().isEmpty(); }
    private static void appendLine(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append("\n");
        sb.append(s);
    }
}
