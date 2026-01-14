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
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Service
public class InvoiceService {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceService.class);

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
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Fonts
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Font.BOLD, Color.BLACK);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.BOLD, Color.BLACK);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, Color.BLACK);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, Color.BLACK);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, Color.GRAY);

            // Title
            Paragraph title = new Paragraph("TAX INVOICE", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph("\n"));

            // Shop Details (Left Aligned)
            Paragraph shopHeader = new Paragraph(sale.getShop().getName(), headerFont);
            document.add(shopHeader);
           /* Paragraph shopName = new Paragraph("Name: " + sale.getShop().getName(), normalFont);
            document.add(shopName);*/
            Paragraph shopAddress = new Paragraph("Address: " + sale.getShop().getAddress(), normalFont);
            document.add(shopAddress);
            Paragraph shopGstin = new Paragraph("GSTIN: " + sale.getShop().getGstin(), normalFont);
            document.add(shopGstin);
            document.add(new Paragraph("\n"));

            // Customer Details (Right Aligned)
            Paragraph customerHeader = new Paragraph("Customer Details:", headerFont);
            customerHeader.setAlignment(Element.ALIGN_RIGHT);
            document.add(customerHeader);
            Paragraph customerName = new Paragraph("Name: " + sale.getCustomer().getName(), normalFont);
            customerName.setAlignment(Element.ALIGN_RIGHT);
            document.add(customerName);
            Paragraph customerAddress = new Paragraph("Address: " + sale.getCustomer().getAddressLine1() + ", " + sale.getCustomer().getCity() + ", " + sale.getCustomer().getState(), normalFont);
            customerAddress.setAlignment(Element.ALIGN_RIGHT);
            document.add(customerAddress);
            if (sale.getCustomer().getGstNumber() != null && !sale.getCustomer().getGstNumber().isEmpty()) {
                Paragraph customerGstin = new Paragraph("GSTIN: " + sale.getCustomer().getGstNumber(), normalFont);
                customerGstin.setAlignment(Element.ALIGN_RIGHT);
                document.add(customerGstin);
            }
            document.add(new Paragraph("\n"));

            // Invoice Information (Two-column table)
            PdfPTable invoiceInfoTable = new PdfPTable(2);
            invoiceInfoTable.setWidthPercentage(100);
            invoiceInfoTable.setWidths(new float[]{50, 50});
            invoiceInfoTable.addCell(getCell("Invoice No: " + sale.getInvoiceNo(), normalFont, Element.ALIGN_LEFT, false));
            invoiceInfoTable.addCell(getCell("Date: " + sale.getDate().toString(), normalFont, Element.ALIGN_RIGHT, false));
            document.add(invoiceInfoTable);
            document.add(new Paragraph("\n"));

            // Items Table
            PdfPTable itemTable = new PdfPTable(9);
            itemTable.setWidthPercentage(100);
            itemTable.setSpacingBefore(10f);
            itemTable.setSpacingAfter(10f);
            itemTable.setWidths(new float[]{5, 30, 10, 10, 10, 10, 10, 10, 10});

            // Headers
            String[] headers = {"S.No.", "Description of Goods", "HSN", "GST Rate", "Qty", "Unit", "Price", "Discount", "Amount"};
            for (String header : headers) {
                itemTable.addCell(getCell(header, boldFont, Element.ALIGN_CENTER, true));
            }

            BigDecimal totalQty = BigDecimal.ZERO;
            BigDecimal totalAmount = BigDecimal.ZERO;
            int serialNo = 1;

            for (SaleItem item : sale.getSaleItems()) {
                itemTable.addCell(getCell(String.valueOf(serialNo++), normalFont, Element.ALIGN_CENTER, false));
                itemTable.addCell(getCell(item.getItemVariant().getItem().getName(), normalFont, Element.ALIGN_LEFT, false));
                itemTable.addCell(getCell(item.getItemVariant().getHsn(), normalFont, Element.ALIGN_CENTER, false));
                itemTable.addCell(getCell(item.getGstType().getRate() + "%", normalFont, Element.ALIGN_CENTER, false));
                itemTable.addCell(getCell(item.getQty().toString(), normalFont, Element.ALIGN_CENTER, false));
                itemTable.addCell(getCell(item.getItemVariant().getUnit(), normalFont, Element.ALIGN_CENTER, false));

                BigDecimal qty = item.getQty() != null ? item.getQty() : BigDecimal.ZERO;
                BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
                BigDecimal discount = item.getDiscount() != null ? item.getDiscount() : BigDecimal.ZERO;
                // Compute amount for this row
                BigDecimal lineAmount = unitPrice.multiply(qty).subtract(discount);

                itemTable.addCell(getCell(unitPrice.toString(), normalFont, Element.ALIGN_RIGHT, false));
                itemTable.addCell(getCell(discount.toString(), normalFont, Element.ALIGN_RIGHT, false));
                itemTable.addCell(getCell(lineAmount.toString(), normalFont, Element.ALIGN_RIGHT, false));

                totalQty = totalQty.add(qty);
                totalAmount = totalAmount.add(lineAmount);
            }

            // Total Row
            itemTable.addCell(getCell("Total", boldFont, Element.ALIGN_CENTER, true));
            itemTable.addCell(getCell("", boldFont, Element.ALIGN_CENTER, true));
            itemTable.addCell(getCell("", boldFont, Element.ALIGN_CENTER, true));
            itemTable.addCell(getCell("", boldFont, Element.ALIGN_CENTER, true));
            itemTable.addCell(getCell(totalQty.toString(), boldFont, Element.ALIGN_CENTER, true));
            itemTable.addCell(getCell("", boldFont, Element.ALIGN_CENTER, true));
            itemTable.addCell(getCell("", boldFont, Element.ALIGN_CENTER, true));
            itemTable.addCell(getCell("", boldFont, Element.ALIGN_CENTER, true));
            itemTable.addCell(getCell(totalAmount.toString(), boldFont, Element.ALIGN_RIGHT, true));

            document.add(itemTable);

            // Taxation Summary
            document.add(new Paragraph("\n"));
            Paragraph taxHeader = new Paragraph("Taxation Summary:", headerFont);
            document.add(taxHeader);

            PdfPTable taxTable = new PdfPTable(4);
            taxTable.setWidthPercentage(100);
            taxTable.setSpacingBefore(10f);
            taxTable.setSpacingAfter(10f);
            taxTable.setWidths(new float[]{25, 25, 25, 25});

            // Tax Headers
            taxTable.addCell(getCell("Taxable Value", boldFont, Element.ALIGN_CENTER, true));
            taxTable.addCell(getCell("CGST", boldFont, Element.ALIGN_CENTER, true));
            taxTable.addCell(getCell("SGST", boldFont, Element.ALIGN_CENTER, true));
            taxTable.addCell(getCell("IGST", boldFont, Element.ALIGN_CENTER, true));

            BigDecimal totalTaxable = sale.getSaleItems().stream().map(SaleItem::getTaxableValue).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalCGST = sale.getSaleItems().stream().map(SaleItem::getCgstAmt).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalSGST = sale.getSaleItems().stream().map(SaleItem::getSgstAmt).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalIGST = sale.getSaleItems().stream().map(SaleItem::getIgstAmt).reduce(BigDecimal.ZERO, BigDecimal::add);

            taxTable.addCell(getCell(totalTaxable.toString(), normalFont, Element.ALIGN_RIGHT, false));
            taxTable.addCell(getCell(totalCGST.toString(), normalFont, Element.ALIGN_RIGHT, false));
            taxTable.addCell(getCell(totalSGST.toString(), normalFont, Element.ALIGN_RIGHT, false));
            taxTable.addCell(getCell(totalIGST.toString(), normalFont, Element.ALIGN_RIGHT, false));

            document.add(taxTable);

            // Total Amount
            Paragraph totalParagraph = new Paragraph("Total Amount: ₹" + sale.getTotalAmount().toString(), boldFont);
            totalParagraph.setAlignment(Element.ALIGN_RIGHT);
            document.add(totalParagraph);

            // Total in Words
            Paragraph totalInWords = new Paragraph("Total Amount in Words: " + numberToWords(sale.getTotalAmount()), normalFont);
            totalInWords.setAlignment(Element.ALIGN_RIGHT);
            document.add(totalInWords);

            // Round Off
            Paragraph roundOff = new Paragraph("Round Off: " + sale.getRoundOff().toString(), normalFont);
            roundOff.setAlignment(Element.ALIGN_RIGHT);
            document.add(roundOff);

            document.add(new Paragraph("\n"));

            // Payment Details
            if (sale.getId() != null) {
                List<PaymentDto> payments = paymentService.getPaymentsBySource(PaymentSourceType.SALE, sale.getId());
                BigDecimal totalPaid = payments.stream().map(PaymentDto::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal dueAmount = sale.getTotalAmount().subtract(totalPaid);

                Paragraph paymentHeader = new Paragraph("Payment Details:", headerFont);
                document.add(paymentHeader);

                PdfPTable paymentTable = new PdfPTable(3);
                paymentTable.setWidthPercentage(100);
                paymentTable.setSpacingBefore(10f);
                paymentTable.setSpacingAfter(10f);
                paymentTable.setWidths(new float[]{33, 33, 33});

                paymentTable.addCell(getCell("Method", boldFont, Element.ALIGN_CENTER, true));
                paymentTable.addCell(getCell("Amount", boldFont, Element.ALIGN_CENTER, true));
                paymentTable.addCell(getCell("Date", boldFont, Element.ALIGN_CENTER, true));

                for (PaymentDto payment : payments) {
                    paymentTable.addCell(getCell(payment.getPaymentMethod().name(), normalFont, Element.ALIGN_LEFT, false));
                    paymentTable.addCell(getCell("₹" + payment.getAmount().toString(), normalFont, Element.ALIGN_RIGHT, false));
                    paymentTable.addCell(getCell(payment.getPaymentDate().toString(), normalFont, Element.ALIGN_CENTER, false));
                }

                document.add(paymentTable);

                Paragraph totalPaidPara = new Paragraph("Total Paid: ₹" + totalPaid.toString(), boldFont);
                totalPaidPara.setAlignment(Element.ALIGN_RIGHT);
                document.add(totalPaidPara);

                if (dueAmount.compareTo(BigDecimal.ZERO) > 0) {
                    Paragraph dueAmountPara = new Paragraph("Amount Due: ₹" + dueAmount.toString(), boldFont);
                    dueAmountPara.setAlignment(Element.ALIGN_RIGHT);
                    document.add(dueAmountPara);
                } else {
                    Paragraph paidStatus = new Paragraph("Status: Fully Paid", boldFont);
                    paidStatus.setAlignment(Element.ALIGN_RIGHT);
                    document.add(paidStatus);
                }

                document.add(new Paragraph("\n"));
            }

            // Banking Details
            Paragraph bankingHeader = new Paragraph("Banking Details:", headerFont);
            document.add(bankingHeader);
            Paragraph banking = new Paragraph(bankingDetails, normalFont);
            document.add(banking);
            document.add(new Paragraph("\n"));

            // Terms and Conditions
            Paragraph termsHeader = new Paragraph("Terms and Conditions:", headerFont);
            document.add(termsHeader);
            Paragraph terms = new Paragraph(termsAndConditions, smallFont);
            document.add(terms);

            // Signatory
            document.add(new Paragraph("\n"));
            Paragraph signatory = new Paragraph("Authorized Signatory", normalFont);
            signatory.setAlignment(Element.ALIGN_RIGHT);
            document.add(signatory);
            Paragraph signature = new Paragraph("_________________________", normalFont);
            signature.setAlignment(Element.ALIGN_RIGHT);
            document.add(signature);

            document.close();
            return baos.toByteArray();
        } catch (DocumentException | IOException | ExportAppException e) {
            logger.error("PDF generation failed for sale {}", sale != null ? sale.getId() : null, e);
            throw new ExportAppException("PDF generation failed", e);
        }
    }

    private PdfPCell getCell(String text, Font font, int alignment, boolean isHeader) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        if (isHeader) {
            cell.setBackgroundColor(new Color(211, 211, 211)); // Light gray
            cell.setBorderWidth(1f);
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