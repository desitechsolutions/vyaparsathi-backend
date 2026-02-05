package com.desitech.vyaparsathi.sales.service.invoice;

import com.desitech.vyaparsathi.common.exception.ExportAppException;
import com.desitech.vyaparsathi.delivery.entity.Delivery;
import com.desitech.vyaparsathi.payment.dto.PaymentDto;
import com.desitech.vyaparsathi.payment.enums.PaymentSourceType;
import com.desitech.vyaparsathi.payment.service.PaymentService;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InvoiceService2 {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceService2.class);

    private static final Color THEME_COLOR = new Color(41, 128, 185);
    private static final Color LIGHT_GREY = new Color(245, 245, 245);
    private static final Color SUCCESS_GREEN = new Color(39, 174, 96);
    private static final Color WARNING_ORANGE = new Color(243, 156, 18);
    private static final Color DANGER_RED = new Color(231, 76, 60);

    private final NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private SaleRepository saleRepository;

    @Value("${shop.banking.details:Bank Name: XYZ Bank\nAccount: 123456789\nIFSC: XYZB0001234}")
    private String bankingDetails;

    @Value("${invoice.terms:1. Goods once sold will not be taken back.\n2. Payment due within 30 days.\n3. Subject to local jurisdiction.}")
    private String termsAndConditions;

    public byte[] generatePdf(Sale sale) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 65, 45);
            PdfWriter writer = PdfWriter.getInstance(document, baos);

            String logoPath = sale.getShop().getLogoPath() != null
                    ? sale.getShop().getLogoPath()
                    : "src/main/resources/static/logo.png";

            writer.setPageEvent(new InvoicePageEvent(logoPath));
            document.open();

            // Fonts (fixed: added Font.NORMAL where missing)
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Font.NORMAL, THEME_COLOR);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, Color.WHITE);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, Color.BLACK);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, Color.BLACK);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, Color.DARK_GRAY);

            addProfessionalHeader(document, sale, titleFont, normalFont, boldFont);
            addAddressSection(document, sale, normalFont, boldFont);
            addItemTable(document, sale, headerFont, normalFont, boldFont);
            addCalculationSection(document, sale, normalFont, boldFont);
            addFinalFooter(document, sale, normalFont, boldFont, smallFont);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("PDF generation failed for sale ID: {}", sale.getId(), e);
            throw new ExportAppException("Failed to generate invoice PDF for sale ID: " + sale.getId(), e);
        }
    }

    private void addProfessionalHeader(Document document, Sale sale, Font titleFont, Font normalFont, Font boldFont) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{60, 40});

        // Left: Shop Details
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.addElement(new Phrase(sale.getShop().getName().toUpperCase(), boldFont));
        left.addElement(new Phrase("\n" + sale.getShop().getAddress(), normalFont));
        if (sale.getShop().getGstin() != null) {
            left.addElement(new Phrase("\nGSTIN: " + sale.getShop().getGstin(), normalFont));
        }
        table.addCell(left);

        // Right: Invoice Info + Status Badge
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setPadding(4);

        // Payment Status calculation
        List<PaymentDto> payments = paymentService.getPaymentsBySource(PaymentSourceType.SALE, sale.getId());
        BigDecimal totalPaid = payments.stream()
                .map(PaymentDto::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal balanceDue = sale.getTotalAmount().subtract(totalPaid).max(BigDecimal.ZERO);
        String status = totalPaid.compareTo(sale.getTotalAmount()) >= 0 ? "PAID"
                : totalPaid.compareTo(BigDecimal.ZERO) > 0 ? "PARTIALLY PAID" : "DUE";

        Color statusColor = status.equals("PAID") ? SUCCESS_GREEN
                : status.equals("PARTIALLY PAID") ? WARNING_ORANGE : DANGER_RED;

        // Add invoice metadata
        right.addElement(new Paragraph("TAX INVOICE", titleFont));
        right.addElement(new Paragraph("Invoice No: " + sale.getInvoiceNo(), boldFont));
        right.addElement(new Paragraph("Date: " + sale.getDate().toLocalDate(), normalFont));

        // Badge using nested 1-cell table
        PdfPTable badgeTable = new PdfPTable(1);
        badgeTable.setWidthPercentage(100);

        PdfPCell badgeCell = new PdfPCell(new Phrase(" STATUS: " + status + " ",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, Color.WHITE)));
        badgeCell.setBackgroundColor(statusColor);
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badgeCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        badgeCell.setPadding(4);
        badgeCell.setFixedHeight(20f); // makes it look like a badge

        badgeTable.addCell(badgeCell);
        right.addElement(badgeTable);

        table.addCell(right);

        document.add(table);
        document.add(new Paragraph("\n"));
    }

    private void addAddressSection(Document document, Sale sale, Font normalFont, Font boldFont) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{50, 50});

        PdfPCell billHead = new PdfPCell(new Phrase("BILL TO", boldFont));
        billHead.setBackgroundColor(THEME_COLOR);
        billHead.setPadding(6);
        table.addCell(billHead);

        PdfPCell shipHead = new PdfPCell(new Phrase("SHIP TO", boldFont));
        shipHead.setBackgroundColor(THEME_COLOR);
        shipHead.setPadding(6);
        table.addCell(shipHead);

        // Billing
        PdfPCell billCell = new PdfPCell();
        billCell.setPadding(8);
        billCell.addElement(new Phrase(sale.getCustomer().getName(), boldFont));
        billCell.addElement(new Phrase("\n" + sale.getCustomer().getAddressLine1(), normalFont));
        billCell.addElement(new Phrase("\n" + sale.getCustomer().getCity() + ", " + sale.getCustomer().getState(), normalFont));
        if (sale.getCustomer().getGstNumber() != null) {
            billCell.addElement(new Phrase("\nGSTIN: " + sale.getCustomer().getGstNumber(), normalFont));
        }
        table.addCell(billCell);

        // Shipping - Use latest delivery (if exists)
        PdfPCell shipCell = new PdfPCell();
        shipCell.setPadding(8);

        Delivery latestDelivery = sale.getLatestDelivery();
        String shipAddr = (latestDelivery != null && latestDelivery.getDeliveryAddress() != null
                && !latestDelivery.getDeliveryAddress().trim().isEmpty())
                ? latestDelivery.getDeliveryAddress()
                : sale.getCustomer().getAddressLine1() + "\n" +
                sale.getCustomer().getCity() + ", " +
                sale.getCustomer().getState();

        shipCell.addElement(new Phrase(sale.getCustomer().getName(), boldFont));
        shipCell.addElement(new Phrase("\n" + shipAddr, normalFont));

        if (latestDelivery != null) {
            shipCell.addElement(new Phrase("\nDelivery Status: " + latestDelivery.getDeliveryStatus(), normalFont));
        }

        table.addCell(shipCell);

        document.add(table);
        document.add(new Paragraph("\n"));
    }

    private void addItemTable(Document document, Sale sale, Font headerFont, Font normalFont, Font boldFont) throws DocumentException {
        PdfPTable table = new PdfPTable(9);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{4, 28, 10, 7, 10, 7, 10, 10, 14});

        String[] headers = {"#", "Item Description", "HSN/SAC", "Qty", "Rate", "GST %", "Disc", "Taxable Amt", "Total"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(THEME_COLOR);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(5);
            table.addCell(cell);
        }

        BigDecimal grandTotalCheck = BigDecimal.ZERO;
        int count = 1;
        for (SaleItem item : sale.getSaleItems()) {
            BigDecimal qty = item.getQty() != null ? item.getQty() : BigDecimal.ZERO;
            BigDecimal rate = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal discount = item.getDiscount() != null ? item.getDiscount() : BigDecimal.ZERO;
            BigDecimal taxable = item.getTaxableValue() != null ? item.getTaxableValue() : BigDecimal.ZERO;

            BigDecimal cgst = item.getCgstAmt() != null ? item.getCgstAmt() : BigDecimal.ZERO;
            BigDecimal sgst = item.getSgstAmt() != null ? item.getSgstAmt() : BigDecimal.ZERO;
            BigDecimal igst = item.getIgstAmt() != null ? item.getIgstAmt() : BigDecimal.ZERO;
            BigDecimal taxes = cgst.add(sgst).add(igst);

            BigDecimal lineTotal = taxable.add(taxes);

            grandTotalCheck = grandTotalCheck.add(lineTotal);

            table.addCell(createCell(String.valueOf(count++), normalFont, Element.ALIGN_CENTER));
            table.addCell(createCell(item.getItemVariant().getItem().getName(), normalFont, Element.ALIGN_LEFT));
            table.addCell(createCell(item.getItemVariant().getHsn(), normalFont, Element.ALIGN_CENTER));
            table.addCell(createCell(qty.toString(), normalFont, Element.ALIGN_CENTER));
            table.addCell(createCell(currency.format(rate), normalFont, Element.ALIGN_RIGHT));
            table.addCell(createCell(item.getGstType().getRate() + "%", normalFont, Element.ALIGN_CENTER));
            table.addCell(createCell(currency.format(discount), normalFont, Element.ALIGN_RIGHT));
            table.addCell(createCell(currency.format(taxable), normalFont, Element.ALIGN_RIGHT));
            table.addCell(createCell(currency.format(lineTotal), boldFont, Element.ALIGN_RIGHT));
        }

        if (grandTotalCheck.compareTo(sale.getTotalAmount()) != 0) {
            logger.warn("Sale ID {}: Calculated line total {} does not match stored totalAmount {}",
                    sale.getId(), grandTotalCheck, sale.getTotalAmount());
        }

        document.add(table);
    }

    private void addCalculationSection(Document document, Sale sale, Font normalFont, Font boldFont) throws DocumentException {
        // GST Summary - Group by rate
        Map<BigDecimal, GstSummary> gstMap = new LinkedHashMap<>();
        for (SaleItem item : sale.getSaleItems()) {
            BigDecimal rate = BigDecimal.valueOf(item.getGstType().getRate());
            GstSummary summary = gstMap.computeIfAbsent(rate, GstSummary::new);
            summary.addCgst(item.getCgstAmt());
            summary.addSgst(item.getSgstAmt());
            summary.addIgst(item.getIgstAmt());
        }

        PdfPTable gstTable = new PdfPTable(4);
        gstTable.setWidthPercentage(100);
        String[] gstHeaders = {"GST Rate", "CGST Amt", "SGST Amt", "IGST Amt"};
        for (String h : gstHeaders) {
            PdfPCell c = new PdfPCell(new Phrase(h, boldFont));
            c.setBackgroundColor(LIGHT_GREY);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            gstTable.addCell(c);
        }

        BigDecimal totalCgst = BigDecimal.ZERO, totalSgst = BigDecimal.ZERO, totalIgst = BigDecimal.ZERO;
        for (GstSummary s : gstMap.values()) {
            gstTable.addCell(createCell(s.rate + "%", normalFont, Element.ALIGN_CENTER));
            gstTable.addCell(createCell(currency.format(s.cgst), normalFont, Element.ALIGN_RIGHT));
            gstTable.addCell(createCell(currency.format(s.sgst), normalFont, Element.ALIGN_RIGHT));
            gstTable.addCell(createCell(currency.format(s.igst), normalFont, Element.ALIGN_RIGHT));
            totalCgst = totalCgst.add(s.cgst);
            totalSgst = totalSgst.add(s.sgst);
            totalIgst = totalIgst.add(s.igst);
        }

        // Amount in Words
        String amountInWords = numberToWords(sale.getTotalAmount());
        Paragraph words = new Paragraph("\nAmount in Words: " + amountInWords + " Only", normalFont);
        words.setAlignment(Element.ALIGN_LEFT);

        // Totals Section
        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(100);

        BigDecimal taxableTotal = sale.getSaleItems().stream()
                .map(SaleItem::getTaxableValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        addTotalRow(totals, "Taxable Amount:", currency.format(taxableTotal), normalFont);
        addTotalRow(totals, "Total CGST:", currency.format(totalCgst), normalFont);
        addTotalRow(totals, "Total SGST:", currency.format(totalSgst), normalFont);
        addTotalRow(totals, "Total IGST:", currency.format(totalIgst), normalFont);

        // Payment Info
        List<PaymentDto> payments = paymentService.getPaymentsBySource(PaymentSourceType.SALE, sale.getId());
        BigDecimal totalPaid = payments.stream()
                .map(PaymentDto::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal balanceDue = sale.getTotalAmount().subtract(totalPaid).max(BigDecimal.ZERO);

        addTotalRow(totals, "Grand Total:", currency.format(sale.getTotalAmount()), boldFont);
        addTotalRow(totals, "Amount Paid:", currency.format(totalPaid), boldFont);

        // Balance Due with color
        Font dueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.NORMAL,
                balanceDue.compareTo(BigDecimal.ZERO) > 0 ? DANGER_RED : SUCCESS_GREEN);
        addTotalRow(totals, "Balance Due:", currency.format(balanceDue), dueFont);

        // Combine GST + Totals
        PdfPTable main = new PdfPTable(2);
        main.setWidthPercentage(100);
        main.setWidths(new float[]{60, 40});
        main.setSpacingBefore(10);

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.addElement(new Paragraph("GST SUMMARY", boldFont));
        left.addElement(gstTable);
        left.addElement(words);

        PdfPCell right = new PdfPCell(totals);
        right.setBorder(Rectangle.NO_BORDER);

        main.addCell(left);
        main.addCell(right);
        document.add(main);
    }

    private void addFinalFooter(Document document, Sale sale, Font normalFont, Font boldFont, Font smallFont) throws DocumentException {
        document.add(new Paragraph("\n"));

        PdfPTable footer = new PdfPTable(2);
        footer.setWidthPercentage(100);

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.addElement(new Phrase("BANKING DETAILS", boldFont));
        left.addElement(new Phrase(bankingDetails, smallFont));

        left.addElement(new Phrase("\n\nTERMS & CONDITIONS", boldFont));
        String[] terms = termsAndConditions.split("\n");
        for (String t : terms) {
            left.addElement(new Phrase("• " + t.trim(), smallFont));
        }
        footer.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setVerticalAlignment(Element.ALIGN_BOTTOM);
        right.addElement(new Paragraph("\n\n\nFor " + sale.getShop().getName(), boldFont));
        right.addElement(new Paragraph("Authorized Signatory", normalFont));
        footer.addCell(right);

        document.add(footer);
    }

    // ────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────

    private PdfPCell createCell(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private void addTotalRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private String numberToWords(BigDecimal number) {
        if (number == null || number.compareTo(BigDecimal.ZERO) == 0) {
            return "Zero";
        }

        String[] units = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
                "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
        String[] tens = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};
        String[] scales = {"", "Thousand", "Lakh", "Crore"};

        long wholePart = number.longValue();
        StringBuilder result = new StringBuilder();

        if (wholePart == 0) {
            return "Zero";
        }

        int scaleIndex = 0;
        while (wholePart > 0) {
            long chunk = wholePart % 1000;
            if (chunk > 0) {
                String chunkText = convertChunk((int) chunk, units, tens);
                if (scaleIndex > 0) {
                    chunkText += " " + scales[scaleIndex];
                }
                result.insert(0, chunkText + (result.length() > 0 ? " " : ""));
            }
            wholePart /= 1000;
            scaleIndex++;
        }

        // Handle decimal part (paise)
        int decimalPart = number.subtract(new BigDecimal(number.longValue())).movePointRight(2).abs().intValue();
        if (decimalPart > 0) {
            result.append(" and ").append(convertChunk(decimalPart, units, tens)).append(" Paise");
        }

        return result.toString().trim();
    }

    private String convertChunk(int number, String[] units, String[] tens) {
        StringBuilder result = new StringBuilder();
        if (number >= 100) {
            result.append(units[number / 100]).append(" Hundred");
            number %= 100;
            if (number > 0) {
                result.append(" and ");
            }
        }
        if (number >= 20) {
            result.append(tens[number / 10]);
            number %= 10;
            if (number > 0) {
                result.append(" ").append(units[number]);
            }
        } else if (number > 0) {
            result.append(units[number]);
        }
        return result.toString();
    }

    public byte[] generatePdfBySaleIdOrInvoiceNo(Long saleId, String invoiceNo) {
        Sale sale;
        if (saleId != null) {
            sale = saleRepository.findById(saleId)
                    .orElseThrow(() -> new RuntimeException("Sale not found with ID: " + saleId));
        } else if (invoiceNo != null) {
            sale = saleRepository.findByInvoiceNo(invoiceNo);
            if (sale == null) {
                throw new RuntimeException("Sale not found with Invoice No: " + invoiceNo);
            }
        } else {
            throw new IllegalArgumentException("Either saleId or invoiceNo must be provided");
        }
        return generatePdf(sale);
    }

    private static class GstSummary {
        final BigDecimal rate;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;

        GstSummary(BigDecimal rate) {
            this.rate = rate;
        }

        void addCgst(BigDecimal amt) {
            if (amt != null) cgst = cgst.add(amt);
        }

        void addSgst(BigDecimal amt) {
            if (amt != null) sgst = sgst.add(amt);
        }

        void addIgst(BigDecimal amt) {
            if (amt != null) igst = igst.add(amt);
        }
    }
}