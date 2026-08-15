package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.common.exception.ExportAppException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

/**
 * QR + human-readable label sheet for item variants. Renders a grid of labels
 * (default 3×8 = 24 per A4 page) using OpenPDF + its built-in QR code
 * generator — no extra dependency needed. Each cell shows: QR (encoding
 * SKU:ID), product name, SKU, and MRP.
 */
@Service
public class BarcodeLabelService {

    private static final Logger logger = LoggerFactory.getLogger(BarcodeLabelService.class);
    private static final int COLS = 3;
    private static final int ROWS_PER_PAGE = 8;

    private final ItemVariantRepository itemVariantRepository;

    public BarcodeLabelService(ItemVariantRepository itemVariantRepository) {
        this.itemVariantRepository = itemVariantRepository;
    }

    @Transactional(readOnly = true)
    public byte[] renderLabels(List<Long> variantIds, int copiesPerVariant) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 24, 24, 24, 24);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            doc.open();
            PdfContentByte cb = writer.getDirectContent();

            PdfPTable table = new PdfPTable(COLS);
            table.setWidthPercentage(100);
            Font nameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font infoFont = FontFactory.getFont(FontFactory.HELVETICA, 8);

            int copies = Math.max(1, copiesPerVariant);
            for (Long variantId : variantIds) {
                ItemVariant v = itemVariantRepository.findById(variantId).orElse(null);
                if (v == null) continue;
                String label = v.getSku() + ":" + v.getId();
                for (int c = 0; c < copies; c++) {
                    PdfPCell cell = new PdfPCell();
                    cell.setBorderColor(new Color(200, 200, 200));
                    cell.setPadding(6);
                    cell.setFixedHeight(90);

                    Barcode128 code = new Barcode128();
                    code.setCode(label);
                    code.setBarHeight(24f);
                    code.setX(0.8f);
                    Image qrImg = code.createImageWithBarcode(cb, null, null);
                    qrImg.scaleToFit(120, 40);

                    PdfPTable inner = new PdfPTable(2);
                    inner.setWidthPercentage(100);
                    inner.setWidths(new float[]{35, 65});
                    PdfPCell qrCell = new PdfPCell(qrImg);
                    qrCell.setBorder(Rectangle.NO_BORDER);
                    qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    inner.addCell(qrCell);

                    PdfPCell textCell = new PdfPCell();
                    textCell.setBorder(Rectangle.NO_BORDER);
                    textCell.setPaddingLeft(4);
                    Paragraph name = new Paragraph(
                            v.getItem() != null && v.getItem().getName() != null ? v.getItem().getName() : "Item",
                            nameFont);
                    name.setLeading(11f);
                    textCell.addElement(name);
                    textCell.addElement(new Paragraph("SKU: " + Optional.ofNullable(v.getSku()).orElse("-"), infoFont));
                    if (v.getMrp() != null) {
                        textCell.addElement(new Paragraph("MRP: ₹" + v.getMrp().toPlainString(), infoFont));
                    }
                    if (v.getBarcode() != null && !v.getBarcode().isBlank()) {
                        textCell.addElement(new Paragraph(v.getBarcode(), infoFont));
                    }
                    inner.addCell(textCell);

                    cell.addElement(inner);
                    table.addCell(cell);
                }
            }
            // Pad the final row so the grid closes cleanly.
            int remainder = table.getRows().size() * COLS - (variantIds.size() * copies);
            for (int i = 0; i < remainder; i++) {
                PdfPCell blank = new PdfPCell();
                blank.setBorder(Rectangle.NO_BORDER);
                blank.setFixedHeight(90);
                table.addCell(blank);
            }
            doc.add(table);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to render label sheet", e);
            throw new ExportAppException("Failed to render labels", e);
        }
    }
}
