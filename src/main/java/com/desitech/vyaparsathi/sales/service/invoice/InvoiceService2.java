package com.desitech.vyaparsathi.sales.service.invoice;

import com.desitech.vyaparsathi.common.exception.ExportAppException;
import com.desitech.vyaparsathi.payment.dto.PaymentDto;
import com.desitech.vyaparsathi.payment.enums.PaymentSourceType;
import com.desitech.vyaparsathi.payment.service.PaymentService;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Service
public class InvoiceService2 {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceService2.class);

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private SaleRepository saleRepository;

    @Value("${shop.banking.details:Bank Name: XYZ Bank, Account: 123456789, IFSC: XYZB0001234}")
    private String bankingDetails;

    @Value("${invoice.terms:1. Goods once sold will not be taken back.\n2. Payment due within 30 days.\n3. For clothing items, exchange within 7 days with tags intact.}")
    private String termsAndConditions;

    public byte[] generatePdf(Sale sale) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 90, 54);
            /*PdfWriter.getInstance(document, baos);*/
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            String logoPath = sale.getShop().getLogoPath() != null
                    ? sale.getShop().getLogoPath()
                    : "src/main/resources/static/logo.png";
            writer.setPageEvent(new InvoicePageEvent(logoPath));
            document.open();

            // Fonts
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Font.BOLD, Color.BLACK);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.BOLD, Color.BLACK);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, Color.BLACK);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, Color.BLACK);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, Color.GRAY);

            addInvoiceHeader(document, sale, titleFont, headerFont, normalFont);
            addItemTable(document, sale, boldFont, normalFont);
            addTaxSummary(document, sale, headerFont, normalFont, boldFont);
            addPaymentDetails(document, sale, headerFont, normalFont, boldFont);
            addFooter(document, smallFont, headerFont, normalFont);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("PDF generation failed for sale {}", sale != null ? sale.getId() : null, e);
            throw new ExportAppException("PDF generation failed", e);
        }
    }
    private void addInvoiceHeader(Document document, Sale sale, Font titleFont, Font headerFont, Font normalFont) throws DocumentException {
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, Color.BLACK);
        Paragraph title = new Paragraph("TAX INVOICE", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        document.add(new Paragraph("\n"));

        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{50, 50});

        // Shop details
        StringBuilder shopDetails = new StringBuilder()
                .append(sale.getShop().getName()).append("\n")
                .append("Address: ").append(sale.getShop().getAddress()).append("\n")
                .append("GSTIN: ").append(sale.getShop().getGstin());

        PdfPCell shopCell = getCell(shopDetails.toString(), normalFont, Element.ALIGN_LEFT, false);
        shopCell.setBorder(Rectangle.NO_BORDER);
        headerTable.addCell(shopCell);

        // Customer details
        StringBuilder custDetails = new StringBuilder()
                .append("Customer: ").append(sale.getCustomer().getName()).append("\n")
                .append("Address: ").append(sale.getCustomer().getAddressLine1()).append(", ")
                .append(sale.getCustomer().getCity()).append(", ").append(sale.getCustomer().getState());
        if (sale.getCustomer().getGstNumber() != null && !sale.getCustomer().getGstNumber().isEmpty()) {
            custDetails.append("\nGSTIN: ").append(sale.getCustomer().getGstNumber());
        }

        PdfPCell custCell = getCell(custDetails.toString(), normalFont, Element.ALIGN_RIGHT, false);
        custCell.setBorder(Rectangle.NO_BORDER);
        headerTable.addCell(custCell);
        document.add(headerTable);
        document.add(new Paragraph("\n"));

        // Invoice Info
        PdfPTable invoiceInfo = new PdfPTable(2);
        invoiceInfo.setWidthPercentage(100);
        invoiceInfo.addCell(getCell("Invoice No: " + sale.getInvoiceNo(), boldFont, Element.ALIGN_LEFT, false));
        invoiceInfo.addCell(getCell("Date: " + sale.getDate().toLocalDate(), boldFont, Element.ALIGN_RIGHT, false));
        document.add(invoiceInfo);
        document.add(new Paragraph("\n"));
    }

    private void addItemTable(Document document, Sale sale, Font boldFont, Font normalFont) throws DocumentException {
        PdfPTable itemTable = new PdfPTable(9);
        itemTable.setWidthPercentage(100);
        itemTable.setSpacingBefore(10f);
        itemTable.setWidths(new float[]{5, 26, 10, 8, 8, 10, 10, 8, 15});

        String[] headers = {"S.No", "Description", "HSN", "GST%", "Qty", "Unit", "Price", "Disc", "Amount"};
        for (String header : headers) {
            PdfPCell headerCell = getCell(header, boldFont, Element.ALIGN_CENTER, true);
            headerCell.setNoWrap(true);
            itemTable.addCell(headerCell);
        }

        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        int serial = 1;
        for (SaleItem item : sale.getSaleItems()) {
            BigDecimal qty = item.getQty() == null ? BigDecimal.ZERO : item.getQty();
            BigDecimal price = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
            BigDecimal discount = item.getDiscount() == null ? BigDecimal.ZERO : item.getDiscount();
            BigDecimal lineTotal = price.multiply(qty).subtract(discount);

            itemTable.addCell(getCell(String.valueOf(serial++), normalFont, Element.ALIGN_CENTER, false));
            itemTable.addCell(getCell(item.getItemVariant().getItem().getName(), normalFont, Element.ALIGN_LEFT, false));
            itemTable.addCell(getCell(item.getItemVariant().getHsn(), normalFont, Element.ALIGN_CENTER, false));
            itemTable.addCell(getCell(item.getGstType().getRate() + "%", normalFont, Element.ALIGN_CENTER, false));
            itemTable.addCell(getCell(qty.toString(), normalFont, Element.ALIGN_CENTER, false));
            itemTable.addCell(getCell(item.getItemVariant().getUnit(), normalFont, Element.ALIGN_CENTER, false));
            itemTable.addCell(getCell(currency.format(price), normalFont, Element.ALIGN_RIGHT, false));
            itemTable.addCell(getCell(currency.format(discount), normalFont, Element.ALIGN_RIGHT, false));
            itemTable.addCell(getCell(currency.format(lineTotal), normalFont, Element.ALIGN_RIGHT, false));

            totalQty = totalQty.add(qty);
            totalAmount = totalAmount.add(lineTotal);
        }

        // Total Row
        itemTable.addCell(getCell("", boldFont, Element.ALIGN_CENTER, true));
        PdfPCell totalLabel = getCell("Total", boldFont, Element.ALIGN_CENTER, true);
        itemTable.addCell(totalLabel);
        itemTable.addCell(getCell("", boldFont, Element.ALIGN_CENTER, true));
        itemTable.addCell(getCell("", boldFont, Element.ALIGN_CENTER, true));
        itemTable.addCell(getCell(totalQty.toString(), boldFont, Element.ALIGN_CENTER, true));
        itemTable.addCell(getCell("", boldFont, Element.ALIGN_CENTER, true));
        itemTable.addCell(getCell("", boldFont, Element.ALIGN_CENTER, true));
        itemTable.addCell(getCell("", boldFont, Element.ALIGN_CENTER, true));
        itemTable.addCell(getCell(currency.format(totalAmount), boldFont, Element.ALIGN_RIGHT, true));

        document.add(itemTable);
    }

    private void addTaxSummary(Document document, Sale sale, Font headerFont, Font normalFont, Font boldFont) throws DocumentException {
        document.add(new Paragraph("\n"));
        Paragraph taxHeader = new Paragraph("Tax Summary", headerFont);
        document.add(taxHeader);

        PdfPTable taxTable = new PdfPTable(4);
        taxTable.setWidthPercentage(100);
        taxTable.setWidths(new float[]{25, 25, 25, 25});

        taxTable.addCell(getCell("Taxable", boldFont, Element.ALIGN_CENTER, true));
        taxTable.addCell(getCell("CGST", boldFont, Element.ALIGN_CENTER, true));
        taxTable.addCell(getCell("SGST", boldFont, Element.ALIGN_CENTER, true));
        taxTable.addCell(getCell("IGST", boldFont, Element.ALIGN_CENTER, true));

        BigDecimal totalTaxable = sale.getSaleItems().stream().map(SaleItem::getTaxableValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCGST = sale.getSaleItems().stream().map(SaleItem::getCgstAmt).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSGST = sale.getSaleItems().stream().map(SaleItem::getSgstAmt).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIGST = sale.getSaleItems().stream().map(SaleItem::getIgstAmt).reduce(BigDecimal.ZERO, BigDecimal::add);

        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        taxTable.addCell(getCell(currency.format(totalTaxable), normalFont, Element.ALIGN_RIGHT, false));
        taxTable.addCell(getCell(currency.format(totalCGST), normalFont, Element.ALIGN_RIGHT, false));
        taxTable.addCell(getCell(currency.format(totalSGST), normalFont, Element.ALIGN_RIGHT, false));
        taxTable.addCell(getCell(currency.format(totalIGST), normalFont, Element.ALIGN_RIGHT, false));
        document.add(taxTable);

        Paragraph total = new Paragraph("Total Amount: ₹" + currency.format(sale.getTotalAmount()), boldFont);
        total.setAlignment(Element.ALIGN_RIGHT);
        document.add(total);

        Paragraph words = new Paragraph("Amount in Words: " + numberToWords(sale.getTotalAmount()) + " only", normalFont);
        words.setAlignment(Element.ALIGN_RIGHT);
        document.add(words);
    }

    private void addPaymentDetails(Document document, Sale sale, Font headerFont, Font normalFont, Font boldFont) throws DocumentException, ExportAppException {
        document.add(new Paragraph("\n"));
        Paragraph paymentHeader = new Paragraph("Payment Details", headerFont);
        document.add(paymentHeader);

        List<PaymentDto> payments = paymentService.getPaymentsBySource(PaymentSourceType.SALE, sale.getId());
        BigDecimal totalPaid = payments.stream().map(PaymentDto::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal due = sale.getTotalAmount().subtract(totalPaid);

        PdfPTable paymentTable = new PdfPTable(3);
        paymentTable.setWidthPercentage(100);
        paymentTable.setWidths(new float[]{40, 30, 30});
        paymentTable.addCell(getCell("Method", boldFont, Element.ALIGN_CENTER, true));
        paymentTable.addCell(getCell("Amount", boldFont, Element.ALIGN_CENTER, true));
        paymentTable.addCell(getCell("Date", boldFont, Element.ALIGN_CENTER, true));

        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        for (PaymentDto payment : payments) {
            paymentTable.addCell(getCell(payment.getPaymentMethod().name(), normalFont, Element.ALIGN_LEFT, false));
            paymentTable.addCell(getCell(currency.format(payment.getAmount()), normalFont, Element.ALIGN_RIGHT, false));
            paymentTable.addCell(getCell(payment.getPaymentDate().toString(), normalFont, Element.ALIGN_CENTER, false));
        }
        document.add(paymentTable);

        Paragraph paid = new Paragraph("Total Paid: ₹" + currency.format(totalPaid), boldFont);
        paid.setAlignment(Element.ALIGN_RIGHT);
        document.add(paid);

        Paragraph duePara = new Paragraph("Amount Due: ₹" + currency.format(due), boldFont);
        duePara.setAlignment(Element.ALIGN_RIGHT);
        document.add(duePara);
    }
    private void addFooter(Document document, Font smallFont, Font headerFont, Font normalFont) throws DocumentException {
        document.add(new Paragraph("\n"));
        Paragraph bankHeader = new Paragraph("Banking Details", headerFont);
        document.add(bankHeader);
        document.add(new Paragraph(bankingDetails, normalFont));

        document.add(new Paragraph("\n"));
        Paragraph termsHeader = new Paragraph("Terms & Conditions", headerFont);
        document.add(termsHeader);
        document.add(new Paragraph(termsAndConditions, smallFont));

        document.add(new Paragraph("\n"));
        Paragraph sign = new Paragraph("Authorized Signatory", normalFont);
        sign.setAlignment(Element.ALIGN_RIGHT);
        document.add(sign);
        Paragraph signature = new Paragraph("_________________________", normalFont);
        signature.setAlignment(Element.ALIGN_RIGHT);
        document.add(signature);
    }


    private PdfPCell getCell(String text, Font font, int alignment, boolean header) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        cell.setNoWrap(true);
        if (header) {
            cell.setBackgroundColor(new Color(220, 220, 220));
            cell.setBorderWidth(1f);
        } else {
            cell.setBorderWidth(0.5f);
        }
        return cell;
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

        // Handle decimal part
        int decimalPart = number.subtract(new BigDecimal(number.longValue())).movePointRight(2).abs().intValue();
        if (decimalPart > 0) {
            result.append(" and ");
            result.append(convertChunk(decimalPart, units, tens)).append(" Paise");
        }

        return result.toString();
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

    /**
     * Find Sale by saleId or invoiceNo and generate PDF. Throws if not found or neither param provided.
     */
    public byte[] generatePdfBySaleIdOrInvoiceNo(Long saleId, String invoiceNo) {
        Sale sale = null;
        if (saleId != null) {
            sale = saleRepository.findById(saleId).orElseThrow(() -> new RuntimeException("Sale not found"));
        } else if (invoiceNo != null) {
            sale = saleRepository.findByInvoiceNo(invoiceNo);
            if (sale == null) throw new RuntimeException("Sale not found for invoiceNo");
        } else {
            throw new IllegalArgumentException("saleId or invoiceNo required");
        }
        return generatePdf(sale);
    }
}