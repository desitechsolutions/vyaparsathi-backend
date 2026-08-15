package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.common.exception.ExportAppException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.invoice.service.InvoicePageEvent;
import com.desitech.vyaparsathi.invoice.utils.InvoiceUtil;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.entity.ReceivingItem;
import com.desitech.vyaparsathi.receiving.repository.ReceivingRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Renders a Goods Receipt Note (GRN) as a PDF using the same design system
 * as the PO / invoice / quotation PDFs (shared {@link InvoicePageEvent},
 * brand-color header rule, section-label strips, zebra-striped item table).
 *
 * <p>Key differences vs the PO PDF: title is "GOODS RECEIPT NOTE", metadata
 * shows GR number + received date + supplier invoice ref, and the items
 * table records Ordered / Received / Damaged / Rejected / Accepted (not
 * unit cost / GST — the PO already carries the financials).
 */
@Service
public class GrnPdfService {

    private static final Logger logger = LoggerFactory.getLogger(GrnPdfService.class);

    private static final Color BRAND_FALLBACK    = new Color(41, 128, 185);
    private static final Color SECTION_LABEL_BG  = new Color(243, 244, 246);
    private static final Color ROW_ALT_BG        = new Color(249, 250, 251);
    private static final Color BORDER_LIGHT      = new Color(229, 231, 235);
    private static final Color TEXT_MUTED        = new Color(107, 114, 128);
    private static final Color TEXT_STRONG       = new Color(17, 24, 39);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final ReceivingRepository receivingRepository;

    @Autowired
    private InvoiceUtil invoiceUtil;

    @Autowired
    private com.desitech.vyaparsathi.document.mapper.ReceivingDocumentMapper receivingDocumentMapper;

    @Autowired
    private com.desitech.vyaparsathi.document.render.EnterpriseDocumentRenderer enterpriseRenderer;

    @org.springframework.beans.factory.annotation.Value("${grn.enterprise-pdf.enabled:true}")
    private boolean enterprisePdfEnabled;

    public GrnPdfService(ReceivingRepository receivingRepository) {
        this.receivingRepository = receivingRepository;
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(Long receivingId) {
        Receiving receiving = receivingRepository.findById(receivingId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiving not found: " + receivingId));
        if (enterprisePdfEnabled) {
            com.desitech.vyaparsathi.document.dto.EnterpriseDocumentDto d =
                    receivingDocumentMapper.map(receiving);
            if (receiving.getShop() != null && receiving.getShop().getLogoPath() != null) {
                d.setLogoBytes(invoiceUtil.loadImageBytes(receiving.getShop().getLogoPath(), "logo"));
            }
            if (receiving.getShop() != null && receiving.getShop().getSignaturePath() != null) {
                d.setSignatureBytes(invoiceUtil.loadImageBytes(receiving.getShop().getSignaturePath(), "signature"));
            }
            return enterpriseRenderer.render(d);
        }
        return render(receiving);
    }

    private byte[] render(Receiving receiving) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 75, 45);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);

            Shop shop = receiving.getShop();
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
            Font shopNameFont    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.NORMAL, TEXT_STRONG);

            renderHeader(doc, shop, receiving, brand, titleFont, metaLabel, metaValue, small, shopNameFont);
            renderSupplierBlock(doc, receiving, sectionLabel, body, bodyBold, small);
            renderItemsTable(doc, receiving, brand, tableHeader, body, bodyBold);
            renderNotesAndSignature(doc, shop, receiving, sectionLabel, body, bodyBold, small);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to render GRN PDF for id={}", receiving.getId(), e);
            throw new ExportAppException("Failed to render GRN PDF: " + receiving.getId(), e);
        }
    }

    // ─── Sections ────────────────────────────────────────────────────────

    private void renderHeader(Document doc, Shop shop, Receiving r, Color brand,
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
        Paragraph title = new Paragraph("GOODS RECEIPT NOTE", titleFont);
        title.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(title);

        PurchaseOrder po = r.getPurchaseOrder();
        PdfPTable meta = new PdfPTable(2);
        meta.setTotalWidth(220);
        meta.setLockedWidth(true);
        meta.setHorizontalAlignment(Element.ALIGN_RIGHT);
        meta.setSpacingBefore(6f);
        addMetaRow(meta, "GR No", r.getGrNumber() != null ? r.getGrNumber() : "-", metaLabel, metaValue);
        addMetaRow(meta, "Received",
                r.getReceivedAt() != null ? DATETIME_FMT.format(r.getReceivedAt()) : "-",
                metaLabel, metaValue);
        if (po != null && po.getPoNumber() != null) {
            addMetaRow(meta, "PO Ref", po.getPoNumber(), metaLabel, metaValue);
        }
        if (isNonBlank(r.getSupplierInvoiceNo())) {
            addMetaRow(meta, "Supplier Inv", r.getSupplierInvoiceNo(), metaLabel, metaValue);
        }
        if (r.getSupplierInvoiceDate() != null) {
            addMetaRow(meta, "Inv Date", DATE_FMT.format(r.getSupplierInvoiceDate()), metaLabel, metaValue);
        }
        if (isNonBlank(r.getDeliveryChallanNo())) {
            addMetaRow(meta, "Challan No", r.getDeliveryChallanNo(), metaLabel, metaValue);
        }
        if (isNonBlank(r.getVehicleNo())) {
            addMetaRow(meta, "Vehicle", r.getVehicleNo(), metaLabel, metaValue);
        }
        addMetaRow(meta, "Status",
                r.getStatus() != null ? r.getStatus().name().replace('_', ' ') : "-",
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

    private void renderSupplierBlock(Document doc, Receiving r, Font sectionLabel,
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

        Supplier s = r.getPurchaseOrder() != null ? r.getPurchaseOrder().getSupplier() : null;
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

    private void renderItemsTable(Document doc, Receiving r, Color brand,
                                  Font tableHeader, Font body, Font bodyBold) throws DocumentException {
        // Columns: #, Item, HSN, Ordered, Received, Damaged, Rejected, Accepted
        int cols = 8;
        PdfPTable table = new PdfPTable(cols);
        table.setWidthPercentage(100);
        table.setSpacingBefore(14f);
        try {
            table.setWidths(new float[]{3, 34, 12, 10, 10, 10, 10, 11});
        } catch (DocumentException ignored) {}
        table.setHeaderRows(1);

        String[] headers = {"#", "Item", "HSN", "Ordered", "Received", "Damaged", "Rejected", "Accepted"};
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
            for (ReceivingItem it : r.getItems()) {
                Color rowBg = (rowNum % 2 == 0) ? ROW_ALT_BG : Color.WHITE;
                table.addCell(bodyCell(String.valueOf(rowNum++), body, Element.ALIGN_CENTER, rowBg));
                table.addCell(itemDescCell(itemName(it), itemSku(it), bodyBold, smallMuted, rowBg));
                table.addCell(bodyCell(displayHsn(it), body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(String.valueOf(Optional.ofNullable(it.getExpectedQty()).orElse(0)),
                        body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(String.valueOf(Optional.ofNullable(it.getReceivedQty()).orElse(0)),
                        body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(String.valueOf(Optional.ofNullable(it.getDamagedQty()).orElse(0)),
                        body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(String.valueOf(Optional.ofNullable(it.getRejectedQty()).orElse(0)),
                        body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(String.valueOf(it.getAcceptedQty()),
                        bodyBold, Element.ALIGN_CENTER, rowBg));
            }
        }
        doc.add(table);
    }

    private void renderNotesAndSignature(Document doc, Shop shop, Receiving r, Font sectionLabel,
                                         Font body, Font bodyBold, Font small) throws DocumentException {
        PdfPTable footer = new PdfPTable(2);
        footer.setWidthPercentage(100);
        footer.setWidths(new float[]{65, 35});
        footer.setSpacingBefore(16f);

        // Left: notes + received-by
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
        String noteText = isNonBlank(r.getNotes()) ? r.getNotes() : "Goods received in acceptable condition.";
        Paragraph notesP = new Paragraph(noteText, small);
        notesP.setLeading(11f);
        notesBody.addElement(notesP);
        notesWrap.addCell(notesBody);
        leftCell.addElement(notesWrap);

        if (isNonBlank(r.getReceivedBy())) {
            Paragraph rcv = new Paragraph("Received by: " + r.getReceivedBy(), small);
            rcv.setSpacingBefore(6f);
            leftCell.addElement(rcv);
        }
        if (r.getApprovedByUser() != null && r.getApprovedAt() != null) {
            String approver = Optional.ofNullable(r.getApprovedByUser().getUsername()).orElse("-");
            Paragraph appr = new Paragraph(
                    "Approved by: " + approver + " on " + DATETIME_FMT.format(r.getApprovedAt()), small);
            leftCell.addElement(appr);
        }
        footer.addCell(leftCell);

        // Right: signature block
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
                logger.warn("Failed to render signature on GRN", e);
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

    private String itemName(ReceivingItem it) {
        PurchaseOrderItem poItem = it.getPurchaseOrderItem();
        if (poItem != null && poItem.getItemVariant() != null && poItem.getItemVariant().getItem() != null
                && isNonBlank(poItem.getItemVariant().getItem().getName())) {
            return poItem.getItemVariant().getItem().getName();
        }
        return "Item";
    }

    private String itemSku(ReceivingItem it) {
        PurchaseOrderItem poItem = it.getPurchaseOrderItem();
        ItemVariant v = poItem != null ? poItem.getItemVariant() : null;
        if (v != null && isNonBlank(v.getSku())) return v.getSku();
        return null;
    }

    private String displayHsn(ReceivingItem it) {
        PurchaseOrderItem poItem = it.getPurchaseOrderItem();
        if (poItem != null && isNonBlank(poItem.getHsnCode())) return poItem.getHsnCode();
        if (poItem != null && poItem.getItemVariant() != null && isNonBlank(poItem.getItemVariant().getHsn())) {
            return poItem.getItemVariant().getHsn();
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

    private static boolean isNonBlank(String s) { return s != null && !s.trim().isEmpty(); }

    private static void appendLine(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append("\n");
        sb.append(s);
    }
}