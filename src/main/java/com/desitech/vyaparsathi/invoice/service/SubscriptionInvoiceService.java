package com.desitech.vyaparsathi.invoice.service;

import com.desitech.vyaparsathi.subscriptions.entity.PaymentVerification;
import com.desitech.vyaparsathi.subscriptions.enums.PaymentVerificationStatus;
import com.desitech.vyaparsathi.subscriptions.repository.SubscriptionPayRepository;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class SubscriptionInvoiceService {

    @Autowired
    private SubscriptionPayRepository subscriptionPayRepository;

    @Autowired
    private ShopRepository shopRepository;

    public byte[] generateSubscriptionInvoicePdf(Long paymentId) {
        PaymentVerification payment = subscriptionPayRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment record not found with ID: " + paymentId));

        if (payment.getStatus() != PaymentVerificationStatus.APPROVED) {
            throw new IllegalStateException("Invoice is only available for APPROVED payments.");
        }

        String shopName = shopRepository.findById(payment.getShopId())
                .map(Shop::getName)
                .orElse("Valued Customer");

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            // --- STYLING (Using com.lowagie.text.Font) ---
            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            Font boldFont = new Font(Font.HELVETICA, 12, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);

            // --- HEADER ---
            document.add(new Paragraph("TAX INVOICE", titleFont));
            document.add(new Paragraph("DesiTech Solutions (VyaparSathi)", boldFont));
            document.add(new Paragraph("Email: support@desitechsolutions.com", normalFont));
            document.add(new Paragraph("GSTIN:", normalFont));
            document.add(new Chunk(new LineSeparator()));

            // --- INVOICE DETAILS ---
            document.add(new Paragraph("\n"));
            document.add(new Paragraph("Invoice No: VS/SUB/" + payment.getId(), normalFont));
            document.add(new Paragraph("Date: " + payment.getSubmittedAt().toLocalDate(), normalFont));
            document.add(new Paragraph("UTR/Transaction ID: " + payment.getUtrNumber(), normalFont));
            document.add(new Paragraph("Customer / Shop: " + shopName, boldFont));
            document.add(new Paragraph("\n"));

            // --- TABLE ---
            PdfPTable table = new PdfPTable(3);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);

            table.addCell(new PdfPCell(new Phrase("Description", boldFont)));
            table.addCell(new PdfPCell(new Phrase("Cycle", boldFont)));
            table.addCell(new PdfPCell(new Phrase("Amount (INR)", boldFont)));

            double totalAmount = payment.getAmount() != null ? payment.getAmount() : 0.0;
            double basePrice = Math.round((totalAmount / 1.18) * 100.0) / 100.0;
            double gst = Math.round((totalAmount - basePrice) * 100.0) / 100.0;

            table.addCell(new Phrase("VyaparSathi Subscription - " + payment.getPlanRequested(), normalFont));
            table.addCell(new Phrase(payment.getBillingCycle().toString(), normalFont));
            table.addCell(new Phrase("INR " + basePrice, normalFont));
            document.add(table);

            // --- TOTALS ---
            Paragraph totals = new Paragraph();
            totals.setAlignment(Element.ALIGN_RIGHT);
            totals.add(new Chunk("\nBase Amount: INR " + basePrice, normalFont));
            totals.add(new Chunk("\nGST (18%): INR " + gst, normalFont));
            totals.add(new Chunk("\nTotal Paid: INR " + totalAmount, boldFont));
            document.add(totals);

            // --- FOOTER ---
            Paragraph footer = new Paragraph("\n\nThis is a computer-generated invoice and does not require a physical signature.", normalFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Error generating PDF: " + e.getMessage());
        }
    }
}