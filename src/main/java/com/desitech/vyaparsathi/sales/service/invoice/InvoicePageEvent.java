package com.desitech.vyaparsathi.sales.service.invoice;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.IOException;

public class InvoicePageEvent extends PdfPageEventHelper {

    private final Image logo;
    private final Font footerFont = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.GRAY);

    public InvoicePageEvent(byte[] logoData) throws IOException, BadElementException {
        this.logo = Image.getInstance(logoData);
        this.logo.scaleToFit(75, 75);
    }

    @Override
    public void onStartPage(PdfWriter writer, Document document) {
        try {
            // Positioned at top-left with proper spacing
            logo.setAbsolutePosition(36, document.getPageSize().getHeight() - 80);
            writer.getDirectContent().addImage(logo);
        } catch (Exception e) {
            // Handle missing image
        }
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        PdfContentByte cb = writer.getDirectContent();
        cb.setLineWidth(0.5f);
        cb.moveTo(36, 40);
        cb.lineTo(document.getPageSize().getWidth() - 36, 40);
        cb.stroke();

        Phrase footer = new Phrase("Generated via VyaparSathi - Powered by DesiTech Solutions India Pvt Ltd", footerFont);
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, footer, 36, 30, 0);

        String pageText = "Page " + writer.getPageNumber();
        ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase(pageText, footerFont), document.getPageSize().getWidth() - 36, 30, 0);
    }
}