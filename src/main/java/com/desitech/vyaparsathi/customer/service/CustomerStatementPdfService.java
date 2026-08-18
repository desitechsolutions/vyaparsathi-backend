package com.desitech.vyaparsathi.customer.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.CustomerNotFoundException;
import com.desitech.vyaparsathi.common.exception.ExportAppException;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.entity.CustomerLedger;
import com.desitech.vyaparsathi.customer.entity.CustomerLedgerType;
import com.desitech.vyaparsathi.customer.repository.CustomerLedgerRepository;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class CustomerStatementPdfService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerStatementPdfService.class);

    private static final Color BRAND_COLOR       = new Color(37, 99, 235);   // Modern Indigo / Blue
    private static final Color HEADER_BG          = new Color(243, 244, 246);
    private static final Color ROW_ALT_BG         = new Color(249, 250, 251);
    private static final Color BORDER_COLOR       = new Color(229, 231, 235);
    private static final Color TEXT_DARK          = new Color(17, 24, 39);
    private static final Color TEXT_MUTED         = new Color(107, 114, 128);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final DateTimeFormatter SHORT_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CustomerRepository customerRepo;
    private final CustomerLedgerRepository ledgerRepo;
    private final ShopRepository shopRepo;

    public CustomerStatementPdfService(CustomerRepository customerRepo,
                                       CustomerLedgerRepository ledgerRepo,
                                       ShopRepository shopRepo) {
        this.customerRepo = customerRepo;
        this.ledgerRepo = ledgerRepo;
        this.shopRepo = shopRepo;
    }

    @Transactional(readOnly = true)
    public byte[] generateStatementPdf(Long customerId, LocalDateTime startDate, LocalDateTime endDate) {
        Long shopId = TenantContext.getCurrentShopId();
        Customer customer = customerRepo.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with id=" + customerId));

        Shop shop = shopId != null ? shopRepo.findById(shopId).orElse(customer.getShop()) : customer.getShop();

        List<CustomerLedger> entries = ledgerRepo.findByCustomerAndDateRange(customer, startDate, endDate);
        // Sort chronologically ascending for statement running balance
        entries.sort(Comparator.comparing(CustomerLedger::getCreatedAt));

        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            // ─── 1. Header & Title ─────────────────────────────────────────────
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{60, 40});
            headerTable.setSpacingAfter(15);

            // Left: Shop Info
            PdfPCell shopCell = new PdfPCell();
            shopCell.setBorder(Rectangle.NO_BORDER);
            Font shopFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BRAND_COLOR);
            Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED);

            String shopName = (shop != null && shop.getName() != null) ? shop.getName() : "VyaparSathi";
            shopCell.addElement(new Paragraph(shopName, shopFont));
            if (shop != null) {
                if (shop.getAddress() != null && !shop.getAddress().isBlank()) shopCell.addElement(new Paragraph(shop.getAddress(), subFont));
                if (shop.getPhone() != null && !shop.getPhone().isBlank()) shopCell.addElement(new Paragraph("Phone: " + shop.getPhone(), subFont));
                if (shop.getGstin() != null && !shop.getGstin().isBlank()) shopCell.addElement(new Paragraph("GSTIN: " + shop.getGstin(), subFont));
            }
            headerTable.addCell(shopCell);


            // Right: Statement Title & Period
            PdfPCell titleCell = new PdfPCell();
            titleCell.setBorder(Rectangle.NO_BORDER);
            titleCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, TEXT_DARK);
            Paragraph titlePara = new Paragraph("STATEMENT OF ACCOUNT", titleFont);
            titlePara.setAlignment(Element.ALIGN_RIGHT);
            titleCell.addElement(titlePara);

            String periodStr = (startDate != null ? startDate.format(SHORT_DATE_FMT) : "All Time") +
                    " to " + (endDate != null ? endDate.format(SHORT_DATE_FMT) : "Present");
            Paragraph periodPara = new Paragraph("Period: " + periodStr, subFont);
            periodPara.setAlignment(Element.ALIGN_RIGHT);
            titleCell.addElement(periodPara);

            Paragraph genDatePara = new Paragraph("Generated: " + LocalDateTime.now().format(DATE_FMT), subFont);
            genDatePara.setAlignment(Element.ALIGN_RIGHT);
            titleCell.addElement(genDatePara);

            headerTable.addCell(titleCell);
            document.add(headerTable);

            // ─── 2. Customer Summary Card ──────────────────────────────────────
            PdfPTable custTable = new PdfPTable(2);
            custTable.setWidthPercentage(100);
            custTable.setWidths(new float[]{55, 45});
            custTable.setSpacingAfter(15);

            PdfPCell billToCell = new PdfPCell();
            billToCell.setBackgroundColor(HEADER_BG);
            billToCell.setPadding(10);
            billToCell.setBorderColor(BORDER_COLOR);

            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BRAND_COLOR);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, TEXT_DARK);
            Font regularFont = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_DARK);

            billToCell.addElement(new Paragraph("STATEMENT TO", labelFont));
            billToCell.addElement(new Paragraph(customer.getName() != null ? customer.getName() : "—", boldFont));
            if (customer.getPhone() != null && !customer.getPhone().isBlank()) {
                billToCell.addElement(new Paragraph("Phone: " + customer.getPhone(), regularFont));
            }
            if (customer.getEmail() != null && !customer.getEmail().isBlank()) {
                billToCell.addElement(new Paragraph("Email: " + customer.getEmail(), regularFont));
            }
            if (customer.getGstNumber() != null && !customer.getGstNumber().isBlank()) {
                billToCell.addElement(new Paragraph("GSTIN: " + customer.getGstNumber(), regularFont));
            }
            custTable.addCell(billToCell);

            PdfPCell balanceCardCell = new PdfPCell();
            balanceCardCell.setBackgroundColor(HEADER_BG);
            balanceCardCell.setPadding(10);
            balanceCardCell.setBorderColor(BORDER_COLOR);

            balanceCardCell.addElement(new Paragraph("CURRENT BALANCE SUMMARY", labelFont));
            BigDecimal currentBal = customer.getCreditBalance() != null ? customer.getCreditBalance() : BigDecimal.ZERO;
            Paragraph balPara = new Paragraph(currency.format(currentBal), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14,
                    currentBal.compareTo(BigDecimal.ZERO) < 0 ? new Color(220, 38, 38) : new Color(16, 185, 129)));
            balanceCardCell.addElement(balPara);
            balanceCardCell.addElement(new Paragraph(currentBal.compareTo(BigDecimal.ZERO) < 0 ? "Customer Owes" : "In Credit / Advance", subFont));

            custTable.addCell(balanceCardCell);
            document.add(custTable);

            // ─── 3. Ledger Table ───────────────────────────────────────────────
            PdfPTable ledgerTable = new PdfPTable(5);
            ledgerTable.setWidthPercentage(100);
            ledgerTable.setWidths(new float[]{18, 42, 14, 13, 13});
            ledgerTable.setSpacingAfter(15);

            String[] headers = {"Date", "Description", "Type", "Debit (-)", "Credit (+)"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
                cell.setBackgroundColor(BRAND_COLOR);
                cell.setPadding(6);
                cell.setBorderColor(BORDER_COLOR);
                if (h.contains("Debit") || h.contains("Credit")) {
                    cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                }
                ledgerTable.addCell(cell);
            }

            BigDecimal totalDebit = BigDecimal.ZERO;
            BigDecimal totalCredit = BigDecimal.ZERO;
            boolean alt = false;

            if (entries.isEmpty()) {
                PdfPCell emptyCell = new PdfPCell(new Phrase("No transactions recorded in this period.", regularFont));
                emptyCell.setColspan(5);
                emptyCell.setPadding(12);
                emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                emptyCell.setBorderColor(BORDER_COLOR);
                ledgerTable.addCell(emptyCell);
            } else {
                for (CustomerLedger entry : entries) {
                    Color bg = alt ? ROW_ALT_BG : Color.WHITE;
                    alt = !alt;

                    PdfPCell dateCell = new PdfPCell(new Phrase(entry.getCreatedAt() != null ? entry.getCreatedAt().format(SHORT_DATE_FMT) : "—", regularFont));
                    dateCell.setBackgroundColor(bg);
                    dateCell.setPadding(5);
                    dateCell.setBorderColor(BORDER_COLOR);
                    ledgerTable.addCell(dateCell);

                    PdfPCell descCell = new PdfPCell(new Phrase(entry.getDescription() != null ? entry.getDescription() : "Ledger entry", regularFont));
                    descCell.setBackgroundColor(bg);
                    descCell.setPadding(5);
                    descCell.setBorderColor(BORDER_COLOR);
                    ledgerTable.addCell(descCell);

                    PdfPCell typeCell = new PdfPCell(new Phrase(entry.getType() != null ? entry.getType().name() : "—", regularFont));
                    typeCell.setBackgroundColor(bg);
                    typeCell.setPadding(5);
                    typeCell.setBorderColor(BORDER_COLOR);
                    ledgerTable.addCell(typeCell);

                    BigDecimal amt = entry.getAmount() != null ? entry.getAmount() : BigDecimal.ZERO;
                    String debitStr = "—";
                    String creditStr = "—";

                    if (entry.getType() == CustomerLedgerType.DEBIT) {
                        debitStr = currency.format(amt);
                        totalDebit = totalDebit.add(amt);
                    } else if (entry.getType() == CustomerLedgerType.CREDIT) {
                        creditStr = currency.format(amt);
                        totalCredit = totalCredit.add(amt);
                    }

                    PdfPCell debitCell = new PdfPCell(new Phrase(debitStr, regularFont));
                    debitCell.setBackgroundColor(bg);
                    debitCell.setPadding(5);
                    debitCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    debitCell.setBorderColor(BORDER_COLOR);
                    ledgerTable.addCell(debitCell);

                    PdfPCell creditCell = new PdfPCell(new Phrase(creditStr, regularFont));
                    creditCell.setBackgroundColor(bg);
                    creditCell.setPadding(5);
                    creditCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    creditCell.setBorderColor(BORDER_COLOR);
                    ledgerTable.addCell(creditCell);
                }

                // Total Row
                PdfPCell totalLabelCell = new PdfPCell(new Phrase("PERIOD TOTALS", boldFont));
                totalLabelCell.setColspan(3);
                totalLabelCell.setBackgroundColor(HEADER_BG);
                totalLabelCell.setPadding(6);
                totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                totalLabelCell.setBorderColor(BORDER_COLOR);
                ledgerTable.addCell(totalLabelCell);

                PdfPCell totalDebitCell = new PdfPCell(new Phrase(currency.format(totalDebit), boldFont));
                totalDebitCell.setBackgroundColor(HEADER_BG);
                totalDebitCell.setPadding(6);
                totalDebitCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                totalDebitCell.setBorderColor(BORDER_COLOR);
                ledgerTable.addCell(totalDebitCell);

                PdfPCell totalCreditCell = new PdfPCell(new Phrase(currency.format(totalCredit), boldFont));
                totalCreditCell.setBackgroundColor(HEADER_BG);
                totalCreditCell.setPadding(6);
                totalCreditCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                totalCreditCell.setBorderColor(BORDER_COLOR);
                ledgerTable.addCell(totalCreditCell);
            }

            document.add(ledgerTable);

            // ─── 4. Footer Note ────────────────────────────────────────────────
            Paragraph footer = new Paragraph("This is a computer generated statement and does not require a physical signature.", subFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to generate customer statement PDF for customerId={}: {}", customerId, e.getMessage(), e);
            throw new ExportAppException("Failed to generate statement PDF: " + e.getMessage(), e);
        }
    }
}
