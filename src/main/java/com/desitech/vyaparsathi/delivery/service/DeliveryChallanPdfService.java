package com.desitech.vyaparsathi.delivery.service;

import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.common.exception.ExportAppException;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.delivery.entity.Delivery;
import com.desitech.vyaparsathi.delivery.entity.DeliveryItem;
import com.desitech.vyaparsathi.delivery.repository.DeliveryRepository;
import com.desitech.vyaparsathi.document.mapper.DeliveryDocumentMapper;
import com.desitech.vyaparsathi.document.render.EnterpriseDocumentRenderer;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.invoice.service.InvoicePageEvent;
import com.desitech.vyaparsathi.invoice.utils.InvoiceUtil;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import com.desitech.vyaparsathi.shop.entity.Shop;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.StringJoiner;

/**
 * Renders a Delivery Challan PDF (compliant with Rule 55 of the CGST Rules).
 * Same design system as invoice / quotation / sales-order, adjusted for the
 * challan use case:
 *
 * <ul>
 *   <li>Title: "DELIVERY CHALLAN"</li>
 *   <li>Reference invoice number displayed prominently</li>
 *   <li>Item table with Qty column (no rate/tax columns — challan is a
 *       transport document, not a bill)</li>
 *   <li>Footer note referencing Rule 55</li>
 * </ul>
 *
 * <p>Falls back to the parent Sale's items if the delivery itself has no
 * per-item breakdown (i.e., legacy deliveries created before V66).
 */
@Service
public class DeliveryChallanPdfService {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryChallanPdfService.class);

    private static final Color BRAND_FALLBACK    = new Color(41, 128, 185);
    private static final Color SECTION_LABEL_BG  = new Color(243, 244, 246);
    private static final Color ROW_ALT_BG        = new Color(249, 250, 251);
    private static final Color BORDER_LIGHT      = new Color(229, 231, 235);
    private static final Color TEXT_MUTED        = new Color(107, 114, 128);
    private static final Color TEXT_STRONG       = new Color(17, 24, 39);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final DeliveryRepository deliveryRepo;
    private final DeliveryChallanNumberService numberService;
    private final DeliveryDocumentMapper deliveryDocumentMapper;
    private final EnterpriseDocumentRenderer enterpriseRenderer;

    @Autowired
    private InvoiceUtil invoiceUtil;

    @Autowired
    private com.desitech.vyaparsathi.document.service.DocumentPrintAuditService printAuditService;

    /** Feature flag matching the other doc suites — flip false to fall
     *  back to the legacy renderer preserved below as {@link #render(Delivery)}. */
    @Value("${delivery-challan.enterprise-pdf.enabled:true}")
    private boolean enterprisePdfEnabled;

    public DeliveryChallanPdfService(DeliveryRepository deliveryRepo,
                                     DeliveryChallanNumberService numberService,
                                     DeliveryDocumentMapper deliveryDocumentMapper,
                                     EnterpriseDocumentRenderer enterpriseRenderer) {
        this.deliveryRepo = deliveryRepo;
        this.numberService = numberService;
        this.deliveryDocumentMapper = deliveryDocumentMapper;
        this.enterpriseRenderer = enterpriseRenderer;
    }

    /**
     * Loads the delivery, lazy-assigns a {@code challanNo} if none exists yet
     * (so legacy pre-V66 rows get one on first PDF fetch), and renders.
     */
    @Transactional
    public byte[] generatePdf(Long deliveryId) {
        Delivery d = deliveryRepo.findById(deliveryId)
                .orElseThrow(() -> new EntityNotFoundAppException("Delivery", deliveryId));

        if (d.getChallanNo() == null || d.getChallanNo().isBlank()) {
            Long shopId = d.getShop() != null ? d.getShop().getId() : null;
            String no = numberService.nextChallanNumber(shopId, java.time.LocalDate.now());
            d.setChallanNo(no);
            deliveryRepo.save(d);
            logger.info("Lazy-assigned challan number {} to delivery id={}", no, deliveryId);
        }

        if (enterprisePdfEnabled) {
            try {
                com.desitech.vyaparsathi.document.dto.EnterpriseDocumentDto doc =
                        deliveryDocumentMapper.map(d);
                byte[] pdf = enterpriseRenderer.render(doc);
                printAuditService.recordPrint(doc.getDocumentType(), d.getId(),
                        d.getChallanNo(),
                        doc.getAudit() != null ? doc.getAudit().getDocumentHash() : null);
                return pdf;
            } catch (RuntimeException e) {
                logger.warn("Enterprise Delivery Challan renderer failed for id={} — falling back to legacy.", deliveryId, e);
                // Fall through to legacy on any renderer failure so users
                // never see a broken PDF endpoint.
            }
        }
        return render(d);
    }

    private byte[] render(Delivery d) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 75, 45);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);

            Shop shop = d.getShop();
            Color brand = InvoiceUtil.parseColor(shop != null ? shop.getBrandColor() : null, BRAND_FALLBACK);
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
            Font shopNameFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.NORMAL, TEXT_STRONG);
            Font attrsFont     = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);

            renderHeader(doc, shop, d, brand, titleFont, metaLabel, metaValue, small, shopNameFont);
            renderPartiesBlock(doc, d, sectionLabel, body, bodyBold, small);
            renderItemsTable(doc, d, brand, tableHeader, body, bodyBold, attrsFont);
            renderNotesAndSignature(doc, d, shop, sectionLabel, body, bodyBold, small);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to render delivery challan PDF for id={}", d.getId(), e);
            throw new ExportAppException("Failed to render delivery challan PDF: " + d.getId(), e);
        }
    }

    private void renderHeader(Document doc, Shop shop, Delivery d, Color brand,
                              Font titleFont, Font metaLabel, Font metaValue,
                              Font small, Font shopNameFont) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{60, 40});

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPaddingTop(8);
        left.addElement(new Paragraph(shop != null && shop.getName() != null ? shop.getName().toUpperCase() : "SHOP", shopNameFont));

        StringBuilder sub = new StringBuilder();
        if (isNonBlank(shop != null ? shop.getAddress() : null)) sub.append(shop.getAddress());
        if (isNonBlank(shop != null ? shop.getGstin() : null))   appendLine(sub, "GSTIN: " + shop.getGstin());
        if (isNonBlank(shop != null ? shop.getPhone() : null))   appendLine(sub, "Phone: " + shop.getPhone());
        if (sub.length() > 0) {
            Paragraph subP = new Paragraph(sub.toString(), small);
            subP.setLeading(11f);
            left.addElement(subP);
        }
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph title = new Paragraph("DELIVERY CHALLAN", titleFont);
        title.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(title);

        PdfPTable meta = new PdfPTable(2);
        meta.setTotalWidth(220);
        meta.setLockedWidth(true);
        meta.setHorizontalAlignment(Element.ALIGN_RIGHT);
        meta.setSpacingBefore(6f);
        addMetaRow(meta, "Challan No", d.getChallanNo(), metaLabel, metaValue);
        String dt = d.getCreatedAt() != null ? DATE_FMT.format(d.getCreatedAt().toLocalDate()) : "-";
        addMetaRow(meta, "Date", dt, metaLabel, metaValue);
        if (d.getSale() != null && d.getSale().getInvoiceNo() != null) {
            addMetaRow(meta, "Invoice Ref", d.getSale().getInvoiceNo(), metaLabel, metaValue);
        }
        if (d.getDeliveryStatus() != null) {
            addMetaRow(meta, "Status", d.getDeliveryStatus().name(), metaLabel, metaValue);
        }
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

    private void renderPartiesBlock(Document doc, Delivery d, Font sectionLabel,
                                    Font body, Font bodyBold, Font small) throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setSpacingBefore(14f);
        t.setWidths(new float[]{50, 50});

        t.addCell(sectionLabelStrip("CONSIGNEE (SHIP TO)", sectionLabel));
        t.addCell(sectionLabelStrip("DELIVERY PERSON", sectionLabel));

        PdfPCell shipCell = new PdfPCell();
        shipCell.setBorder(Rectangle.BOX);
        shipCell.setBorderColor(BORDER_LIGHT);
        shipCell.setBorderWidth(0.5f);
        shipCell.setPadding(10);

        Customer c = d.getSale() != null ? d.getSale().getCustomer() : null;
        String name = d.getCustomerName() != null ? d.getCustomerName()
                : (c != null ? c.getName() : "Walk-in Customer");
        Paragraph nm = new Paragraph(name != null ? name : "-", bodyBold);
        nm.setLeading(12f);
        shipCell.addElement(nm);
        if (isNonBlank(d.getDeliveryAddress())) {
            Paragraph a = new Paragraph(d.getDeliveryAddress(), body);
            a.setLeading(11f);
            shipCell.addElement(a);
        } else if (c != null && isNonBlank(c.getAddressLine1())) {
            Paragraph a = new Paragraph(c.getAddressLine1(), body);
            a.setLeading(11f);
            shipCell.addElement(a);
        }
        if (c != null && isNonBlank(c.getPhone())) shipCell.addElement(new Paragraph("Phone: " + c.getPhone(), small));
        if (c != null && isNonBlank(c.getGstNumber())) shipCell.addElement(new Paragraph("GSTIN: " + c.getGstNumber(), small));
        t.addCell(shipCell);

        PdfPCell dpCell = new PdfPCell();
        dpCell.setBorder(Rectangle.BOX);
        dpCell.setBorderColor(BORDER_LIGHT);
        dpCell.setBorderWidth(0.5f);
        dpCell.setPadding(10);
        if (d.getDeliveryPerson() != null) {
            Paragraph dpName = new Paragraph(d.getDeliveryPerson().getName() != null ? d.getDeliveryPerson().getName() : "-", bodyBold);
            dpName.setLeading(12f);
            dpCell.addElement(dpName);
            if (isNonBlank(d.getDeliveryPerson().getPhone())) {
                dpCell.addElement(new Paragraph("Phone: " + d.getDeliveryPerson().getPhone(), body));
            }
        } else {
            dpCell.addElement(new Paragraph("Not assigned", small));
        }
        if (d.getDeliveryPaidBy() != null) {
            dpCell.addElement(new Paragraph("Delivery paid by: " + d.getDeliveryPaidBy().name(), small));
        }
        if (d.getDeliveryCharge() != null && d.getDeliveryCharge() > 0) {
            dpCell.addElement(new Paragraph("Charge: ₹" + d.getDeliveryCharge(), small));
        }
        t.addCell(dpCell);
        doc.add(t);
    }

    private void renderItemsTable(Document doc, Delivery d, Color brand,
                                  Font tableHeader, Font body, Font bodyBold, Font attrsFont) throws DocumentException {
        // Header
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setSpacingBefore(14f);
        try { table.setWidths(new float[]{4, 44, 12, 8, 14, 18}); } catch (DocumentException ignored) {}
        table.setHeaderRows(1);

        for (String h : new String[]{"#", "Item Description", "HSN/SAC", "Qty", "Unit", "Batch / Expiry"}) {
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
        // Prefer per-line DeliveryItem when present; else fall back to parent Sale's items.
        boolean usedDeliveryItems = d.getItems() != null && !d.getItems().isEmpty();
        if (usedDeliveryItems) {
            for (DeliveryItem it : d.getItems()) {
                Color rowBg = (rowNum % 2 == 0) ? ROW_ALT_BG : Color.WHITE;
                table.addCell(bodyCell(String.valueOf(rowNum++), body, Element.ALIGN_CENTER, rowBg));
                ItemDescriptionParts desc = descFromDeliveryItem(it);
                table.addCell(itemDescCell(desc.name, desc.attrs, bodyBold, attrsFont, rowBg));
                table.addCell(bodyCell(isNonBlank(it.getHsnSac()) ? it.getHsnSac() : "-", body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(it.getQty() != null ? it.getQty().toPlainString() : "-", body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(isNonBlank(it.getUnit()) ? it.getUnit() : "-", body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(batchExpiry(it.getBatchNumber(), it.getExpiryDate()), body, Element.ALIGN_CENTER, rowBg));
            }
        } else if (d.getSale() != null && d.getSale().getSaleItems() != null) {
            // Fallback: entire sale is being delivered as one shipment
            for (SaleItem si : d.getSale().getSaleItems()) {
                Color rowBg = (rowNum % 2 == 0) ? ROW_ALT_BG : Color.WHITE;
                table.addCell(bodyCell(String.valueOf(rowNum++), body, Element.ALIGN_CENTER, rowBg));
                ItemDescriptionParts desc = descFromSaleItem(si);
                table.addCell(itemDescCell(desc.name, desc.attrs, bodyBold, attrsFont, rowBg));
                String hsn = si.getItemVariant() != null && isNonBlank(si.getItemVariant().getHsn())
                        ? si.getItemVariant().getHsn()
                        : (isNonBlank(si.getCustomHsnSac()) ? si.getCustomHsnSac() : "-");
                table.addCell(bodyCell(hsn, body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(si.getQty() != null ? si.getQty().toPlainString() : "-", body, Element.ALIGN_CENTER, rowBg));
                String unit = si.getItemVariant() != null && isNonBlank(si.getItemVariant().getUnit())
                        ? si.getItemVariant().getUnit()
                        : (isNonBlank(si.getCustomUnit()) ? si.getCustomUnit() : "-");
                table.addCell(bodyCell(unit, body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(batchExpiry(si.getBatchNumber(), si.getExpiryDate()), body, Element.ALIGN_CENTER, rowBg));
            }
        } else {
            // Absolute fallback — a delivery with no items
            PdfPCell empty = new PdfPCell(new Phrase("No items on this delivery", body));
            empty.setColspan(6);
            empty.setBorder(Rectangle.BOX);
            empty.setBorderColor(BORDER_LIGHT);
            empty.setBorderWidth(0.5f);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            empty.setPadding(10);
            table.addCell(empty);
        }
        doc.add(table);
    }

    private String batchExpiry(String batch, java.time.LocalDate expiry) {
        boolean hasBatch = isNonBlank(batch);
        boolean hasExpiry = expiry != null;
        if (!hasBatch && !hasExpiry) return "-";
        StringBuilder sb = new StringBuilder();
        if (hasBatch) sb.append(batch);
        if (hasExpiry) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(expiry.format(DateTimeFormatter.ofPattern("MMM yyyy")));
        }
        return sb.toString();
    }

    private void renderNotesAndSignature(Document doc, Delivery d, Shop shop,
                                         Font sectionLabel, Font body, Font bodyBold, Font small)
            throws DocumentException {
        PdfPTable footer = new PdfPTable(2);
        footer.setWidthPercentage(100);
        footer.setWidths(new float[]{65, 35});
        footer.setSpacingBefore(16f);

        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        PdfPTable notesWrap = new PdfPTable(1);
        notesWrap.setWidthPercentage(100);
        notesWrap.addCell(sectionLabelStrip("NOTES & DECLARATIONS", sectionLabel));
        PdfPCell notesBody = new PdfPCell();
        notesBody.setBorder(Rectangle.BOX);
        notesBody.setBorderColor(BORDER_LIGHT);
        notesBody.setBorderWidth(0.5f);
        notesBody.setPadding(8);
        if (isNonBlank(d.getDeliveryNotes())) {
            Paragraph p = new Paragraph(d.getDeliveryNotes(), body);
            p.setLeading(11f);
            notesBody.addElement(p);
        }
        // Rule 55 declaration
        Paragraph rule55 = new Paragraph(
                "Issued under Rule 55 of the CGST Rules, 2017. This challan accompanies the goods "
                        + "described above and is not a tax invoice.", small);
        rule55.setLeading(11f);
        rule55.setSpacingBefore(6f);
        notesBody.addElement(rule55);
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
                logger.warn("Failed to render signature on delivery challan", e);
            }
        } else {
            Paragraph spacer = new Paragraph(" ");
            spacer.setSpacingAfter(40f);
            rightCell.addElement(spacer);
        }
        Paragraph label = new Paragraph("Authorized Signatory", small);
        label.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(label);
        Paragraph recv = new Paragraph("Received in good condition:", small);
        recv.setSpacingBefore(18f);
        recv.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(recv);
        Paragraph recvLine = new Paragraph("___________________________", body);
        recvLine.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(recvLine);

        footer.addCell(rightCell);
        doc.add(footer);
    }

    // ─── Item description helpers ───────────────────────────────────────

    private static final class ItemDescriptionParts {
        final String name;
        final String attrs;
        ItemDescriptionParts(String name, String attrs) { this.name = name; this.attrs = attrs; }
    }

    private ItemDescriptionParts descFromDeliveryItem(DeliveryItem it) {
        if (it.getSaleItem() != null) {
            return descFromSaleItem(it.getSaleItem());
        }
        return new ItemDescriptionParts(it.getItemName() != null ? it.getItemName() : "Item", null);
    }

    private ItemDescriptionParts descFromSaleItem(SaleItem si) {
        if (si.getItemVariant() == null) {
            String name = si.getCustomItemName() != null ? si.getCustomItemName() : "Custom Item";
            String attrs = isNonBlank(si.getCustomDescription()) ? si.getCustomDescription() : null;
            return new ItemDescriptionParts(name, attrs);
        }
        ItemVariant v = si.getItemVariant();
        StringBuilder name = new StringBuilder(
                v.getItem() != null && v.getItem().getName() != null ? v.getItem().getName() : "Item");
        if (v.getItem() != null && isNonBlank(v.getItem().getBrandName())) {
            name.append(" — ").append(v.getItem().getBrandName());
        }
        StringJoiner attrs = new StringJoiner(" · ");
        if (isNonBlank(v.getColor()))  attrs.add(v.getColor());
        if (isNonBlank(v.getSize()))   attrs.add(v.getSize());
        if (isNonBlank(v.getDesign())) attrs.add(v.getDesign());
        if (isNonBlank(v.getFit()))    attrs.add(v.getFit());
        return new ItemDescriptionParts(name.toString(), attrs.length() == 0 ? null : attrs.toString());
    }

    private PdfPCell itemDescCell(String name, String attrs, Font nameFont, Font attrsFont, Color bg) {
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
        if (attrs != null && !attrs.isBlank()) {
            Paragraph p2 = new Paragraph(attrs, attrsFont);
            p2.setLeading(10f);
            cell.addElement(p2);
        }
        return cell;
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

    private static boolean isNonBlank(String s) { return s != null && !s.trim().isEmpty(); }

    private static void appendLine(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append("\n");
        sb.append(s);
    }
}
