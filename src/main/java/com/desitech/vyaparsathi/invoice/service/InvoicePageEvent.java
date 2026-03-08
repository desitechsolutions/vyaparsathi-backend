package com.desitech.vyaparsathi.invoice.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class InvoicePageEvent extends PdfPageEventHelper {

    private static final Logger logger = LoggerFactory.getLogger(InvoicePageEvent.class);
    private Image logo;
    private final Color themeColor;
    private final Font footerFont = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.GRAY);

    // Modified constructor to accept the theme color from the Shop settings
    public InvoicePageEvent(byte[] logoData, Color themeColor) {
        this.themeColor = (themeColor != null) ? themeColor : new Color(41, 128, 185);
        if (logoData != null && logoData.length > 0) {
            try {
                this.logo = Image.getInstance(logoData);
                this.logo.scaleToFit(80, 80); // Slightly larger logo for better visibility
            } catch (Exception e) {
                logger.warn("Failed to initialize logo in PageEvent: {}", e.getMessage());
                this.logo = null;
            }
        }
    }

    @Override
    public void onStartPage(PdfWriter writer, Document document) {
        if (logo != null) {
            try {
                // Better positioning relative to the top edge
                float x = 36;
                float y = document.getPageSize().getHeight() - 70;
                logo.setAbsolutePosition(x, y);
                writer.getDirectContent().addImage(logo);
            } catch (DocumentException e) {
                logger.error("Error adding logo to page: {}", e.getMessage());
            }
        }
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        PdfContentByte cb = writer.getDirectContent();
        float width = document.getPageSize().getWidth();
        float margin = 36;
        float bottomPos = 30;

        // Draw the separator line using the Shop's brand color (subtle version)
        cb.setLineWidth(0.8f);
        cb.setRGBColorStroke(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue());
        cb.moveTo(margin, bottomPos + 12);
        cb.lineTo(width - margin, bottomPos + 12);
        cb.stroke();

        // Left Footer: System Branding
        Phrase footer = new Phrase("Generated via VyaparSathi - Powered by DesiTech Solutions", footerFont);
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, footer, margin, bottomPos, 0);

        // Center Footer: Generation Timestamp (Good for audits)
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Phrase timePhrase = new Phrase("Printed on: " + timestamp, footerFont);
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, timePhrase, width / 2, bottomPos, 0);

        // Right Footer: Page Numbers
        String pageText = String.format("Page %d", writer.getPageNumber());
        Phrase pagePhrase = new Phrase(pageText, footerFont);
        ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, pagePhrase, width - margin, bottomPos, 0);
    }
}