package com.desitech.vyaparsathi.customer.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.CustomerNotFoundException;
import com.desitech.vyaparsathi.common.exception.ExportAppException;
import com.desitech.vyaparsathi.customer.entity.Customer;
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
import java.util.Locale;

/**
 * Renders the customer statement of account from real transactional
 * data (invoices, payments, credit notes) via {@link CustomerStatementBuilder}.
 *
 * <p>Prior implementation rendered {@code CustomerLedger} rows only —
 * the manual journal-entry table — which was empty for most tenants
 * so the PDF showed "No transactions" even for customers with dozens
 * of real invoices. This version uses the source-of-truth data plus
 * a receivable-aging summary block.</p>
 */
@Service
public class CustomerStatementPdfService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerStatementPdfService.class);

    private static final Color BRAND_COLOR    = new Color(37, 99, 235);
    private static final Color HEADER_BG      = new Color(243, 244, 246);
    private static final Color ROW_ALT_BG     = new Color(249, 250, 251);
    private static final Color BORDER_COLOR   = new Color(229, 231, 235);
    private static final Color TEXT_DARK      = new Color(17, 24, 39);
    private static final Color TEXT_MUTED     = new Color(107, 114, 128);
    private static final Color POSITIVE_COLOR = new Color(16, 185, 129);
    private static final Color NEGATIVE_COLOR = new Color(220, 38, 38);
    private static final Color AMBER_COLOR    = new Color(245, 158, 11);
    private static final Color ORANGE_COLOR   = new Color(249, 115, 22);

    private static final DateTimeFormatter DATE_FMT       = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final DateTimeFormatter SHORT_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CustomerRepository customerRepo;
    private final ShopRepository shopRepo;
    private final CustomerStatementBuilder builder;

    public CustomerStatementPdfService(CustomerRepository customerRepo,
                                       ShopRepository shopRepo,
                                       CustomerStatementBuilder builder) {
        this.customerRepo = customerRepo;
        this.shopRepo = shopRepo;
        this.builder = builder;
    }

    @Transactional(readOnly = true)
    public byte[] generateStatementPdf(Long customerId, LocalDateTime startDate, LocalDateTime endDate) {
        Long shopId = TenantContext.getCurrentShopId();
        Customer customer = customerRepo.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with id=" + customerId));

        Shop shop = shopId != null ? shopRepo.findById(shopId).orElse(customer.getShop()) : customer.getShop();

        CustomerStatementBuilder.Statement statement = builder.build(customerId, startDate, endDate);

        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            renderHeader(document, shop, startDate, endDate);
            renderCustomerAndBalanceCard(document, customer, statement, currency);
            renderAgingBlock(document, statement, currency);
            renderStatementTable(document, statement, currency);
            renderFooter(document);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to generate customer statement PDF for customerId={}: {}", customerId, e.getMessage(), e);
            throw new ExportAppException("Failed to generate statement PDF: " + e.getMessage(), e);
        }
    }

    // ─── Section renderers ────────────────────────────────────────────

    private void renderHeader(Document document, Shop shop, LocalDateTime startDate, LocalDateTime endDate)
            throws DocumentException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{60, 40});
        headerTable.setSpacingAfter(15);

        Font shopFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BRAND_COLOR);
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED);
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, TEXT_DARK);

        PdfPCell shopCell = new PdfPCell();
        shopCell.setBorder(Rectangle.NO_BORDER);
        String shopName = (shop != null && shop.getName() != null) ? shop.getName() : "VyaparSathi";
        shopCell.addElement(new Paragraph(shopName, shopFont));
        if (shop != null) {
            if (nonBlank(shop.getAddress())) shopCell.addElement(new Paragraph(shop.getAddress(), subFont));
            if (nonBlank(shop.getPhone()))   shopCell.addElement(new Paragraph("Phone: " + shop.getPhone(), subFont));
            if (nonBlank(shop.getGstin()))   shopCell.addElement(new Paragraph("GSTIN: " + shop.getGstin(), subFont));
        }
        headerTable.addCell(shopCell);

        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph titlePara = new Paragraph("STATEMENT OF ACCOUNT", titleFont);
        titlePara.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(titlePara);

        String periodStr = (startDate != null ? startDate.format(SHORT_DATE_FMT) : "All Time")
                + " to " + (endDate != null ? endDate.format(SHORT_DATE_FMT) : "Present");
        Paragraph periodPara = new Paragraph("Period: " + periodStr, subFont);
        periodPara.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(periodPara);

        Paragraph genDatePara = new Paragraph("Generated: " + LocalDateTime.now().format(DATE_FMT), subFont);
        genDatePara.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(genDatePara);

        headerTable.addCell(titleCell);
        document.add(headerTable);
    }

    private void renderCustomerAndBalanceCard(Document document, Customer customer,
                                              CustomerStatementBuilder.Statement statement,
                                              NumberFormat currency) throws DocumentException {
        PdfPTable custTable = new PdfPTable(2);
        custTable.setWidthPercentage(100);
        custTable.setWidths(new float[]{55, 45});
        custTable.setSpacingAfter(15);

        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BRAND_COLOR);
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, TEXT_DARK);
        Font regularFont = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_DARK);
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED);

        PdfPCell billToCell = new PdfPCell();
        billToCell.setBackgroundColor(HEADER_BG);
        billToCell.setPadding(10);
        billToCell.setBorderColor(BORDER_COLOR);
        billToCell.addElement(new Paragraph("STATEMENT TO", labelFont));
        billToCell.addElement(new Paragraph(customer.getName() != null ? customer.getName() : "—", boldFont));
        if (nonBlank(customer.getPhone())) billToCell.addElement(new Paragraph("Phone: " + customer.getPhone(), regularFont));
        if (nonBlank(customer.getEmail())) billToCell.addElement(new Paragraph("Email: " + customer.getEmail(), regularFont));
        if (nonBlank(customer.getGstNumber())) billToCell.addElement(new Paragraph("GSTIN: " + customer.getGstNumber(), regularFont));
        custTable.addCell(billToCell);

        PdfPCell balanceCardCell = new PdfPCell();
        balanceCardCell.setBackgroundColor(HEADER_BG);
        balanceCardCell.setPadding(10);
        balanceCardCell.setBorderColor(BORDER_COLOR);
        balanceCardCell.addElement(new Paragraph("CLOSING BALANCE", labelFont));
        BigDecimal closing = statement.closingBalance;
        Color balColor = closing.signum() > 0 ? NEGATIVE_COLOR : POSITIVE_COLOR;
        Paragraph balPara = new Paragraph(currency.format(closing.abs()),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, balColor));
        balanceCardCell.addElement(balPara);
        String balLabel = closing.signum() > 0 ? "Customer owes" : (closing.signum() < 0 ? "In credit / advance" : "Settled");
        balanceCardCell.addElement(new Paragraph(balLabel, subFont));
        custTable.addCell(balanceCardCell);
        document.add(custTable);
    }

    /**
     * Aging bucket block. Renders nothing when there's no outstanding
     * receivable — a settled account gets a cleaner statement.
     */
    private void renderAgingBlock(Document document, CustomerStatementBuilder.Statement statement,
                                  NumberFormat currency) throws DocumentException {
        CustomerStatementBuilder.Aging aging = statement.aging;
        BigDecimal total = aging.total();
        if (total.signum() <= 0) return;

        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, TEXT_DARK);
        Font miniFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, TEXT_DARK);

        PdfPTable agingTable = new PdfPTable(4);
        agingTable.setWidthPercentage(100);
        agingTable.setSpacingAfter(15);

        String[] labels = {"0–30 DAYS", "31–60 DAYS", "61–90 DAYS", "90+ DAYS"};
        BigDecimal[] values = {aging.current, aging.bucket31_60, aging.bucket61_90, aging.bucket90Plus};
        Color[] colors = {POSITIVE_COLOR, AMBER_COLOR, ORANGE_COLOR, NEGATIVE_COLOR};

        for (int i = 0; i < labels.length; i++) {
            PdfPCell headCell = new PdfPCell(new Phrase(labels[i], miniFont));
            headCell.setBackgroundColor(colors[i]);
            headCell.setPadding(5);
            headCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            headCell.setBorder(Rectangle.NO_BORDER);
            agingTable.addCell(headCell);
        }
        for (BigDecimal v : values) {
            PdfPCell valCell = new PdfPCell(new Phrase(currency.format(v), valueFont));
            valCell.setBackgroundColor(HEADER_BG);
            valCell.setPadding(8);
            valCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            valCell.setBorderColor(BORDER_COLOR);
            agingTable.addCell(valCell);
        }
        // Total row spanning all 4 columns
        PdfPCell totalCell = new PdfPCell(new Phrase(
                "Total outstanding: " + currency.format(total), labelFont));
        totalCell.setColspan(4);
        totalCell.setPadding(6);
        totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalCell.setBorder(Rectangle.NO_BORDER);
        agingTable.addCell(totalCell);

        document.add(agingTable);
    }

    private void renderStatementTable(Document document, CustomerStatementBuilder.Statement statement,
                                      NumberFormat currency) throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{12, 12, 30, 14, 14, 18});
        table.setSpacingAfter(15);

        Font regularFont = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_DARK);
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, TEXT_DARK);
        Font whiteBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);

        String[] headers = {"Date", "Reference", "Description", "Debit (₹)", "Credit (₹)", "Balance (₹)"};
        for (int i = 0; i < headers.length; i++) {
            PdfPCell cell = new PdfPCell(new Phrase(headers[i], whiteBold));
            cell.setBackgroundColor(BRAND_COLOR);
            cell.setPadding(6);
            cell.setBorderColor(BORDER_COLOR);
            if (i >= 3) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(cell);
        }

        if (statement.lines.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No transactions recorded in this period.", regularFont));
            empty.setColspan(6);
            empty.setPadding(12);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            empty.setBorderColor(BORDER_COLOR);
            table.addCell(empty);
        } else {
            boolean alt = false;
            for (CustomerStatementBuilder.StatementLine l : statement.lines) {
                Color bg = "OPENING".equals(l.type) ? HEADER_BG : (alt ? ROW_ALT_BG : Color.WHITE);
                alt = !alt;

                Font typeFont = "OPENING".equals(l.type) ? boldFont : regularFont;
                addCell(table, l.date != null ? l.date.format(SHORT_DATE_FMT) : "—", typeFont, bg, Element.ALIGN_LEFT);
                addCell(table, l.reference != null ? l.reference : "", typeFont, bg, Element.ALIGN_LEFT);
                addCell(table, l.description != null ? l.description : "", typeFont, bg, Element.ALIGN_LEFT);
                addCell(table, l.debit != null && l.debit.signum() > 0 ? currency.format(l.debit) : "—", typeFont, bg, Element.ALIGN_RIGHT);
                addCell(table, l.credit != null && l.credit.signum() > 0 ? currency.format(l.credit) : "—", typeFont, bg, Element.ALIGN_RIGHT);
                String balStr = currency.format(l.runningBalance != null ? l.runningBalance : BigDecimal.ZERO);
                addCell(table, balStr, typeFont, bg, Element.ALIGN_RIGHT);
            }

            // Totals row
            PdfPCell totalsLabel = new PdfPCell(new Phrase("PERIOD TOTALS", boldFont));
            totalsLabel.setColspan(3);
            totalsLabel.setBackgroundColor(HEADER_BG);
            totalsLabel.setPadding(6);
            totalsLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalsLabel.setBorderColor(BORDER_COLOR);
            table.addCell(totalsLabel);
            addCell(table, currency.format(statement.totalInvoiced), boldFont, HEADER_BG, Element.ALIGN_RIGHT);
            addCell(table, currency.format(statement.totalPaid.add(statement.totalCredits)), boldFont, HEADER_BG, Element.ALIGN_RIGHT);
            addCell(table, currency.format(statement.closingBalance), boldFont, HEADER_BG, Element.ALIGN_RIGHT);
        }
        document.add(table);
    }

    private void renderFooter(Document document) throws DocumentException {
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_MUTED);
        Paragraph footer = new Paragraph(
                "This is a computer-generated statement and does not require a physical signature.", subFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);
    }

    private void addCell(PdfPTable table, String text, Font font, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(5);
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(BORDER_COLOR);
        table.addCell(cell);
    }

    private static boolean nonBlank(String s) { return s != null && !s.isBlank(); }

    @Transactional(readOnly = true)
    public CustomerStatementBuilder.Statement getStatementData(Long customerId, LocalDateTime startDate, LocalDateTime endDate) {
        return builder.build(customerId, startDate, endDate);
    }
}
