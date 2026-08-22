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
            Font nameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f);
            Font infoFont = FontFactory.getFont(FontFactory.HELVETICA, 6.5f, Color.DARK_GRAY);
            Font mrpFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.0f);

            int copies = Math.max(1, copiesPerVariant);
            int renderedCount = 0;
            if (variantIds != null) {
                for (Long variantId : variantIds) {
                    ItemVariant v = itemVariantRepository.findById(variantId).orElse(null);
                    if (v == null) continue;
                    String label = (v.getBarcode() != null && !v.getBarcode().isBlank())
                            ? v.getBarcode()
                            : (v.getSku() != null ? v.getSku() + ":" + v.getId() : String.valueOf(v.getId()));

                    for (int c = 0; c < copies; c++) {
                        PdfPCell cell = new PdfPCell();
                        cell.setBorderColor(new Color(210, 210, 210));
                        cell.setPadding(4);
                        cell.setFixedHeight(95);

                        // 1. Product Name (Top, Centered)
                        String itemName = v.getItem() != null && v.getItem().getName() != null
                                ? v.getItem().getName() : "Item";
                        Paragraph namePara = new Paragraph(itemName, nameFont);
                        namePara.setAlignment(Element.ALIGN_CENTER);
                        namePara.setLeading(10f);
                        namePara.setSpacingAfter(2f);
                        cell.addElement(namePara);

                        // 2. Barcode128 (Middle, Centered)
                        Barcode128 code = new Barcode128();
                        code.setCode(label);
                        code.setCodeType(Barcode128.CODE128);
                        code.setBarHeight(22f);
                        code.setX(0.7f);
                        code.setSize(7f);
                        code.setBaseline(8f);
                        Image barcodeImg = code.createImageWithBarcode(cb, null, null);
                        barcodeImg.setAlignment(Element.ALIGN_CENTER);
                        barcodeImg.scaleToFit(145, 32);
                        cell.addElement(barcodeImg);

                        // 3. Footer (Bottom: SKU on left, MRP on right)
                        PdfPTable footer = new PdfPTable(2);
                        footer.setWidthPercentage(100);
                        footer.setWidths(new float[]{60, 40});

                        String skuText = v.getSku() != null ? v.getSku() : "-";
                        PdfPCell skuCell = new PdfPCell(new Paragraph("SKU: " + skuText, infoFont));
                        skuCell.setBorder(Rectangle.NO_BORDER);
                        skuCell.setHorizontalAlignment(Element.ALIGN_LEFT);
                        skuCell.setVerticalAlignment(Element.ALIGN_BOTTOM);
                        footer.addCell(skuCell);

                        String mrpText = v.getMrp() != null ? "MRP: Rs. " + v.getMrp().toPlainString() : "";
                        PdfPCell mrpCell = new PdfPCell(new Paragraph(mrpText, mrpFont));
                        mrpCell.setBorder(Rectangle.NO_BORDER);
                        mrpCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                        mrpCell.setVerticalAlignment(Element.ALIGN_BOTTOM);
                        footer.addCell(mrpCell);

                        footer.setSpacingBefore(3f);
                        cell.addElement(footer);

                        table.addCell(cell);
                        renderedCount++;
                    }
                }
            }

            if (renderedCount == 0) {
                doc.add(new Paragraph("No items to generate labels.", infoFont));
            } else {
                // Pad the final row so the grid completes and renders cleanly.
                int remainder = (COLS - (renderedCount % COLS)) % COLS;
                for (int i = 0; i < remainder; i++) {
                    PdfPCell blank = new PdfPCell();
                    blank.setBorder(Rectangle.NO_BORDER);
                    blank.setFixedHeight(90);
                    table.addCell(blank);
                }
                doc.add(table);
            }
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to render label sheet", e);
            throw new ExportAppException("Failed to render labels", e);
        }
    }
}
