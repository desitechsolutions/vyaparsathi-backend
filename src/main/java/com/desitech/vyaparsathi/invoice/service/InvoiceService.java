package com.desitech.vyaparsathi.invoice.service;

import com.desitech.vyaparsathi.common.exception.ExportAppException;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.delivery.entity.Delivery;
import com.desitech.vyaparsathi.invoice.dto.GstSummary;
import com.desitech.vyaparsathi.invoice.utils.InvoiceUtil;
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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

import static com.desitech.vyaparsathi.invoice.utils.InvoiceUtil.*;
import static java.math.BigDecimal.ZERO;

@Service
public class InvoiceService {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceService.class);

    private static final Color SUCCESS_GREEN     = new Color(39, 174, 96);
    private static final Color WARNING_ORANGE    = new Color(243, 156, 18);
    private static final Color DANGER_RED        = new Color(231, 76, 60);
    private static final Color SECTION_LABEL_BG  = new Color(243, 244, 246);
    private static final Color ROW_ALT_BG        = new Color(249, 250, 251);
    private static final Color BORDER_LIGHT      = new Color(229, 231, 235);
    private static final Color TEXT_MUTED        = new Color(107, 114, 128);
    private static final Color TEXT_STRONG       = new Color(17, 24, 39);

    private static final DateTimeFormatter EXPIRY_FMT = DateTimeFormatter.ofPattern("MMM yyyy");

    private final NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private InvoiceUtil invoiceUtil;

    @Autowired
    private com.desitech.vyaparsathi.gst.service.GstJurisdictionService gstJurisdictionService;

    @Value("${shop.banking.details:Bank Name: XYZ Bank\nAccount: 123456789\nIFSC: XYZB0001234}")
    private String defaultBankingDetails;

    @Value("${invoice.default.terms:1. Goods once sold will not be taken back without original bill.\n2. Warranty/guarantee as per manufacturer terms.\n3. All disputes subject to local jurisdiction.}")
    private String defaultTermsAndConditions;

    private static class Fonts {
        Font title;
        Font sectionLabel;
        Font metaLabel;
        Font metaValue;
        Font body;
        Font bodyBold;
        Font small;
        Font smallMuted;
        Font tableHeader;
        Font badge;
        Font grandTotalWhite;
        Font summaryValue;
    }

    public byte[] generatePdf(Sale sale) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 75, 45);
            PdfWriter writer = PdfWriter.getInstance(document, baos);

            byte[] logoBytes = invoiceUtil.loadImageBytes(sale.getShop().getLogoPath(), "logo");
            Color brandColor = parseColor(sale.getShop().getBrandColor(), new Color(41, 128, 185));

            writer.setPageEvent(new InvoicePageEvent(logoBytes, brandColor));
            document.open();

            Fonts f = buildFonts(brandColor);

            BigDecimal paid = paymentService.getTotalPaidBySaleIds(Set.of(sale.getId()))
                    .getOrDefault(sale.getId(), ZERO);

            addProfessionalHeader(document, sale, f, paid, brandColor);
            addPaymentSummaryCard(document, sale, f, paid, brandColor);
            addAddressSection(document, sale, f);
            addItemTable(document, sale, f, brandColor);
            addCalculationSection(document, sale, f, paid, brandColor);
            addFinalFooter(document, sale, f, paid);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("PDF generation failed for sale ID: {}", sale.getId(), e);
            throw new ExportAppException("Failed to generate invoice PDF for sale ID: " + sale.getId(), e);
        }
    }

    private Fonts buildFonts(Color brandColor) {
        Fonts f = new Fonts();
        f.title            = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Font.NORMAL, brandColor);
        f.sectionLabel     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.NORMAL, TEXT_MUTED);
        f.metaLabel        = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);
        f.metaValue        = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, TEXT_STRONG);
        f.body             = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, TEXT_STRONG);
        f.bodyBold         = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, TEXT_STRONG);
        f.small            = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, TEXT_STRONG);
        f.smallMuted       = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);
        f.tableHeader      = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.NORMAL, Color.WHITE);
        f.badge            = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.NORMAL, Color.WHITE);
        f.grandTotalWhite  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.NORMAL, Color.WHITE);
        f.summaryValue     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Font.NORMAL, TEXT_STRONG);
        return f;
    }

    private boolean saleHasGst(Sale sale) {
        return Boolean.TRUE.equals(sale.getIsGstRequired());
    }

    // ============================================================
    // 1. HEADER
    // ============================================================
    private void addProfessionalHeader(Document document, Sale sale, Fonts f, BigDecimal paid, Color brandColor)
            throws DocumentException {

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{60, 40});

        // --- LEFT: Shop identity ---
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPaddingTop(8);  // give room below logo drawn by page event

        Font shopNameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.NORMAL, TEXT_STRONG);
        Paragraph shopName = new Paragraph(sale.getShop().getName().toUpperCase(), shopNameFont);
        shopName.setLeading(16f);
        left.addElement(shopName);

        StringBuilder subtitle = new StringBuilder();
        if (isNonBlank(sale.getShop().getAddress())) subtitle.append(sale.getShop().getAddress());
        if (isNonBlank(sale.getShop().getGstin()))   appendLine(subtitle, "GSTIN: " + sale.getShop().getGstin());
        if (isNonBlank(sale.getShop().getCompanyWebsite()))
            appendLine(subtitle, sale.getShop().getCompanyWebsite());
        if (isNonBlank(sale.getShop().getSupportContact()))
            appendLine(subtitle, sale.getShop().getSupportContact());
        if (subtitle.length() > 0) {
            Paragraph sub = new Paragraph(subtitle.toString(), f.smallMuted);
            sub.setLeading(11f);
            left.addElement(sub);
        }
        table.addCell(left);

        // --- RIGHT: Invoice title + meta grid + status pill ---
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setPadding(0);

        boolean isComposition = !saleHasGst(sale);
        boolean isProforma = sale.getSaleType() == com.desitech.vyaparsathi.sales.enums.SaleType.PROFORMA;

        // Title precedence: PROFORMA overrides everything (a proforma is neither
        // a tax invoice nor a bill of supply — it's a non-binding quote-like doc).
        String invoiceTitle;
        if (isProforma) {
            invoiceTitle = "PROFORMA INVOICE";
        } else if (isComposition) {
            invoiceTitle = "BILL OF SUPPLY";
        } else {
            invoiceTitle = "TAX INVOICE";
        }
        Paragraph titlePara = new Paragraph(invoiceTitle, f.title);
        titlePara.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(titlePara);

        Font declFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7, Font.NORMAL, TEXT_MUTED);
        if (isProforma) {
            Paragraph decl = new Paragraph(
                    "Not a tax invoice — no ownership of goods has transferred", declFont);
            decl.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(decl);
        } else if (isComposition) {
            Paragraph decl = new Paragraph("Composition taxable person, not eligible to collect tax on supplies", declFont);
            decl.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(decl);
        }

        // Meta grid: label + value pairs, right-aligned
        PdfPTable meta = new PdfPTable(2);
        meta.setTotalWidth(180);
        meta.setLockedWidth(true);
        meta.setHorizontalAlignment(Element.ALIGN_RIGHT);
        meta.setSpacingBefore(6f);
        addMetaRow(meta, "Invoice No", sale.getInvoiceNo(), f.metaLabel, f.metaValue);
        addMetaRow(meta, "Date", sale.getDate().toLocalDate().toString(), f.metaLabel, f.metaValue);
        if (sale.getDueDate() != null) {
            addMetaRow(meta, "Due Date", sale.getDueDate().toString(), f.metaLabel, f.metaValue);
        }
        right.addElement(meta);

        // Status pill
        String status = statusLabel(sale, paid);
        Color statusColor = statusColor(status);

        PdfPTable badgeTable = new PdfPTable(1);
        badgeTable.setTotalWidth(100);
        badgeTable.setLockedWidth(true);
        badgeTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        badgeTable.setSpacingBefore(6f);

        PdfPCell badgeCell = new PdfPCell(new Phrase(status, f.badge));
        badgeCell.setBackgroundColor(statusColor);
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badgeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        badgeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        badgeCell.setPadding(5);
        badgeTable.addCell(badgeCell);
        right.addElement(badgeTable);

        table.addCell(right);
        document.add(table);

        // Brand-color horizontal rule below header
        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        rule.setSpacingBefore(10f);
        PdfPCell ruleCell = new PdfPCell();
        ruleCell.setFixedHeight(2f);
        ruleCell.setBorder(Rectangle.BOTTOM);
        ruleCell.setBorderColorBottom(brandColor);
        ruleCell.setBorderWidthBottom(1.5f);
        rule.addCell(ruleCell);
        document.add(rule);
    }

    private void addMetaRow(PdfPTable meta, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell l = new PdfPCell(new Phrase(label, labelFont));
        l.setBorder(Rectangle.NO_BORDER);
        l.setHorizontalAlignment(Element.ALIGN_RIGHT);
        l.setPaddingRight(6);
        l.setPaddingTop(2);
        l.setPaddingBottom(2);
        meta.addCell(l);

        PdfPCell v = new PdfPCell(new Phrase(value, valueFont));
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPaddingTop(2);
        v.setPaddingBottom(2);
        meta.addCell(v);
    }

    private String statusLabel(Sale sale, BigDecimal paid) {
        if (paid.compareTo(sale.getTotalAmount()) >= 0) return "PAID";
        if (paid.compareTo(ZERO) > 0) return "PARTIALLY PAID";
        return "DUE";
    }

    private Color statusColor(String status) {
        if ("PAID".equals(status)) return SUCCESS_GREEN;
        if ("PARTIALLY PAID".equals(status)) return WARNING_ORANGE;
        return DANGER_RED;
    }

    // ============================================================
    // 2. PAYMENT SUMMARY CARD (new)
    // ============================================================
    private void addPaymentSummaryCard(Document document, Sale sale, Fonts f, BigDecimal paid, Color brandColor)
            throws DocumentException {

        BigDecimal due = sale.getTotalAmount().subtract(paid).max(ZERO);

        PdfPTable outer = new PdfPTable(1);
        outer.setWidthPercentage(100);
        outer.setSpacingBefore(12f);

        PdfPTable inner = new PdfPTable(3);
        inner.setWidthPercentage(100);
        inner.setWidths(new float[]{1, 1, 1});

        Font balanceFont = due.compareTo(ZERO) > 0
                ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Font.NORMAL, DANGER_RED)
                : FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Font.NORMAL, SUCCESS_GREEN);

        inner.addCell(summaryTile("TOTAL AMOUNT", currency.format(sale.getTotalAmount()), f.sectionLabel, f.summaryValue, false));
        inner.addCell(summaryTile("AMOUNT PAID", currency.format(paid), f.sectionLabel, f.summaryValue, false));
        inner.addCell(summaryTile("BALANCE DUE", currency.format(due), f.sectionLabel, balanceFont, true));

        PdfPCell wrapper = new PdfPCell(inner);
        wrapper.setPadding(0);
        wrapper.setBackgroundColor(SECTION_LABEL_BG);
        wrapper.setBorder(Rectangle.BOX);
        wrapper.setBorderColor(BORDER_LIGHT);
        wrapper.setBorderWidth(1f);
        wrapper.setBorderColorLeft(brandColor);
        wrapper.setBorderWidthLeft(3f);
        outer.addCell(wrapper);

        document.add(outer);
    }

    private PdfPCell summaryTile(String label, String value, Font labelFont, Font valueFont, boolean lastTile) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(SECTION_LABEL_BG);
        cell.setBorder(lastTile ? Rectangle.NO_BORDER : Rectangle.RIGHT);
        cell.setBorderColorRight(BORDER_LIGHT);
        cell.setBorderWidthRight(1f);
        cell.setPadding(10);

        Paragraph l = new Paragraph(label, labelFont);
        l.setAlignment(Element.ALIGN_LEFT);
        l.setLeading(11f);
        cell.addElement(l);

        Paragraph v = new Paragraph(value, valueFont);
        v.setAlignment(Element.ALIGN_LEFT);
        v.setLeading(16f);
        v.setSpacingBefore(2f);
        cell.addElement(v);

        return cell;
    }

    // ============================================================
    // 3. ADDRESS SECTION (Bill To / Ship To — merged when equal)
    // ============================================================
    private void addAddressSection(Document document, Sale sale, Fonts f) throws DocumentException {
        boolean merged = shipEqualsBill(sale);

        if (merged) {
            PdfPTable t = new PdfPTable(1);
            t.setWidthPercentage(100);
            t.setSpacingBefore(14f);

            t.addCell(sectionLabelStrip("BILL TO & SHIP TO", f.sectionLabel));
            t.addCell(addressBodyCell(buildCustomerBlock(sale, f), 1));

            document.add(t);
        } else {
            PdfPTable t = new PdfPTable(2);
            t.setWidthPercentage(100);
            t.setWidths(new float[]{50, 50});
            t.setSpacingBefore(14f);

            t.addCell(sectionLabelStrip("BILL TO", f.sectionLabel));
            t.addCell(sectionLabelStrip("SHIP TO", f.sectionLabel));

            t.addCell(addressBodyCell(buildCustomerBlock(sale, f), 1));
            t.addCell(addressBodyCell(buildShipBlock(sale, f), 1));

            document.add(t);
        }
    }

    private PdfPCell sectionLabelStrip(String label, Font labelFont) {
        PdfPCell cell = new PdfPCell(new Phrase(label, labelFont));
        cell.setBackgroundColor(SECTION_LABEL_BG);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER_LIGHT);
        cell.setBorderWidth(0.5f);
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        return cell;
    }

    private PdfPCell addressBodyCell(List<Element> content, int colspan) {
        PdfPCell cell = new PdfPCell();
        cell.setColspan(colspan);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER_LIGHT);
        cell.setBorderWidth(0.5f);
        cell.setPadding(8);
        for (Element e : content) {
            cell.addElement(e);
        }
        return cell;
    }

    private List<Element> buildCustomerBlock(Sale sale, Fonts f) {
        List<Element> out = new ArrayList<>();
        Customer c = sale.getCustomer();
        if (c != null) {
            Paragraph name = new Paragraph(c.getName(), f.bodyBold);
            name.setLeading(12f);
            out.add(name);

            String addr = buildFullCustomerAddress(c);
            if (!addr.isEmpty()) {
                Paragraph ap = new Paragraph(addr, f.body);
                ap.setLeading(11f);
                out.add(ap);
            }
            if (isNonBlank(c.getGstNumber())) {
                Paragraph g = new Paragraph("GSTIN: " + c.getGstNumber(), f.smallMuted);
                g.setLeading(11f);
                out.add(g);
            }
        } else {
            out.add(new Paragraph("Walk-in Customer", f.body));
        }
        return out;
    }

    private List<Element> buildShipBlock(Sale sale, Fonts f) {
        List<Element> out = new ArrayList<>();
        Customer c = sale.getCustomer();
        Delivery latest = sale.getLatestDelivery();
        String customShip = (latest != null && isNonBlank(latest.getDeliveryAddress()))
                ? latest.getDeliveryAddress().trim() : null;

        if (c != null) {
            Paragraph name = new Paragraph(c.getName(), f.bodyBold);
            name.setLeading(12f);
            out.add(name);

            String addr = customShip != null ? customShip : buildFullCustomerAddress(c);
            if (!addr.isEmpty()) {
                Paragraph ap = new Paragraph(addr, f.body);
                ap.setLeading(11f);
                out.add(ap);
            }
        } else if (customShip != null) {
            Paragraph name = new Paragraph("Walk-in Customer", f.bodyBold);
            name.setLeading(12f);
            out.add(name);
            Paragraph ap = new Paragraph(customShip, f.body);
            ap.setLeading(11f);
            out.add(ap);
        } else {
            out.add(new Paragraph("Walk-in Customer", f.body));
        }
        return out;
    }

    private boolean shipEqualsBill(Sale sale) {
        Delivery latest = sale.getLatestDelivery();
        if (latest == null) return true;
        String ship = latest.getDeliveryAddress();
        if (!isNonBlank(ship)) return true;

        if (sale.getCustomer() == null) return false;

        String bill = buildFullCustomerAddress(sale.getCustomer());
        return normalizeAddress(bill).equals(normalizeAddress(ship));
    }

    private String buildFullCustomerAddress(Customer c) {
        StringJoiner sj = new StringJoiner("\n");
        if (isNonBlank(c.getAddressLine1())) sj.add(c.getAddressLine1().trim());
        if (isNonBlank(c.getAddressLine2())) sj.add(c.getAddressLine2().trim());
        String cityState = formatCityState(c.getCity(), c.getState());
        String postal = isNonBlank(c.getPostalCode()) ? c.getPostalCode().trim() : "";
        String line3 = (cityState + " " + postal).trim();
        if (!line3.isEmpty()) sj.add(line3);
        if (isNonBlank(c.getCountry())) sj.add(c.getCountry().trim());
        return sj.toString();
    }

    private String normalizeAddress(String s) {
        if (s == null) return "";
        return s.toLowerCase()
                .replaceAll("[\\p{Punct}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String formatCityState(String city, String state) {
        String c = city != null ? city.trim() : "";
        String s = state != null ? state.trim() : "";
        if (c.isEmpty() && s.isEmpty()) return "";
        if (c.isEmpty()) return s;
        if (s.isEmpty()) return c;
        return c + ", " + s;
    }

    // ============================================================
    // 4. ITEM TABLE
    // ============================================================
    private void addItemTable(Document document, Sale sale, Fonts f, Color brandColor) throws DocumentException {
        boolean isComposition = !saleHasGst(sale);
        boolean showBatchExpiry = hasBatchOrExpiry(sale);

        List<String> headers = new ArrayList<>();
        List<Float> widths = new ArrayList<>();
        headers.add("#");            widths.add(4f);
        headers.add("Item Description"); widths.add(isComposition ? 34f : (showBatchExpiry ? 20f : 24f));
        headers.add("HSN");          widths.add(9f);
        if (showBatchExpiry) {
            headers.add("Batch");    widths.add(10f);
            headers.add("Expiry");   widths.add(9f);
        }
        headers.add("Qty");          widths.add(6f);
        headers.add("Unit");         widths.add(6f);
        headers.add("Rate");         widths.add(10f);
        if (!isComposition) {
            headers.add("GST %");    widths.add(7f);
            headers.add("Disc");     widths.add(8f);
            headers.add("Taxable");  widths.add(10f);
        }
        headers.add("Total");        widths.add(12f);

        int columns = headers.size();
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        table.setSpacingBefore(14f);

        float[] wArr = new float[columns];
        for (int i = 0; i < columns; i++) wArr[i] = widths.get(i);
        table.setWidths(wArr);
        table.setHeaderRows(1);

        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, f.tableHeader));
            cell.setBackgroundColor(brandColor);
            cell.setBorder(Rectangle.BOX);
            cell.setBorderColor(brandColor);
            cell.setBorderWidth(0.5f);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPaddingTop(6);
            cell.setPaddingBottom(6);
            table.addCell(cell);
        }

        int rowNum = 1;
        for (SaleItem item : sale.getSaleItems()) {
            BigDecimal qty      = item.getQty() != null ? item.getQty() : ZERO;
            BigDecimal retQty   = item.getReturnedQty() != null ? item.getReturnedQty() : ZERO;
            BigDecimal rate     = item.getUnitPrice() != null ? item.getUnitPrice() : ZERO;
            BigDecimal discount = item.getDiscount() != null ? item.getDiscount() : ZERO;
            BigDecimal taxable  = item.getTaxableValue() != null ? item.getTaxableValue() : ZERO;
            BigDecimal cgst     = item.getCgstAmt() != null ? item.getCgstAmt() : ZERO;
            BigDecimal sgst     = item.getSgstAmt() != null ? item.getSgstAmt() : ZERO;
            BigDecimal igst     = item.getIgstAmt() != null ? item.getIgstAmt() : ZERO;

            BigDecimal lineTotal = taxable.add(cgst).add(sgst).add(igst);
            boolean zebra = (rowNum % 2 == 0);
            Color rowBg = zebra ? ROW_ALT_BG : Color.WHITE;

            ItemDescriptionParts desc = buildItemDescriptionParts(item);
            if (retQty.compareTo(ZERO) > 0) {
                desc = new ItemDescriptionParts(desc.name, appendComma(desc.attrs, "Returned: " + retQty));
            }

            table.addCell(bodyCell(String.valueOf(rowNum), f.body, Element.ALIGN_CENTER, rowBg));
            table.addCell(itemDescCell(desc.name, desc.attrs, f.bodyBold, f.smallMuted, rowBg));
            String hsn = item.getItemVariant() != null
                    ? nvl(item.getItemVariant().getHsn())
                    : nvl(item.getCustomHsnSac());
            table.addCell(bodyCell(hsn, f.body, Element.ALIGN_CENTER, rowBg));

            if (showBatchExpiry) {
                table.addCell(bodyCell(nvl(item.getBatchNumber()), f.body, Element.ALIGN_CENTER, rowBg));
                String exp = item.getExpiryDate() != null ? item.getExpiryDate().format(EXPIRY_FMT) : "-";
                table.addCell(bodyCell(exp, f.body, Element.ALIGN_CENTER, rowBg));
            }

            table.addCell(bodyCell(qty.toString(), f.body, Element.ALIGN_CENTER, rowBg));
            String unit = item.getItemVariant() != null
                    ? nvl(item.getItemVariant().getUnit())
                    : nvl(item.getCustomUnit());
            table.addCell(bodyCell(unit, f.body, Element.ALIGN_CENTER, rowBg));
            table.addCell(bodyCell(currency.format(rate), f.body, Element.ALIGN_RIGHT, rowBg));

            if (!isComposition) {
                table.addCell(bodyCell(item.getGstType().getRate() + "%", f.body, Element.ALIGN_CENTER, rowBg));
                table.addCell(bodyCell(currency.format(discount), f.body, Element.ALIGN_RIGHT, rowBg));
                table.addCell(bodyCell(currency.format(taxable), f.body, Element.ALIGN_RIGHT, rowBg));
            }

            table.addCell(bodyCell(currency.format(lineTotal), f.bodyBold, Element.ALIGN_RIGHT, rowBg));
            rowNum++;
        }

        document.add(table);
    }

    private PdfPCell bodyCell(String text, Font font, int align, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBackgroundColor(bg);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER_LIGHT);
        cell.setBorderWidth(0.5f);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPaddingTop(5);
        cell.setPaddingBottom(5);
        cell.setPaddingLeft(4);
        cell.setPaddingRight(4);
        return cell;
    }

    private boolean hasBatchOrExpiry(Sale sale) {
        for (SaleItem it : sale.getSaleItems()) {
            if (isNonBlank(it.getBatchNumber())) return true;
            if (it.getExpiryDate() != null) return true;
        }
        return false;
    }

    /** Split representation of an item description: main line vs. attribute line. */
    private static final class ItemDescriptionParts {
        final String name;
        final String attrs;
        ItemDescriptionParts(String name, String attrs) {
            this.name = name;
            this.attrs = attrs;
        }
    }

    /**
     * Builds a two-part item descriptor:
     * <ul>
     *   <li><b>name</b> — the primary line, e.g. {@code "Shirts — Louis Philippe"}. Rendered bold.</li>
     *   <li><b>attrs</b> — a dot-separated attribute list without labels, e.g.
     *       {@code "Beige · 38 · Checked · Loose Fit"}. Rendered small and muted.
     *       {@code null}/empty when the item has no variant attributes.</li>
     * </ul>
     *
     * <p>SKUs are intentionally excluded — they are internal identifiers for
     * packing/inventory, not customer-facing. Labels ("Color:", "Size:", …)
     * are dropped because context makes them obvious and shorter values print
     * cleaner in narrow table cells.
     */
    private ItemDescriptionParts buildItemDescriptionParts(SaleItem item) {
        // Custom / free-text line: no variant, use captured display fields.
        if (item.getItemVariant() == null) {
            String name = item.getCustomItemName() != null ? item.getCustomItemName() : "Custom Item";
            String attrs = isNonBlank(item.getCustomDescription()) ? item.getCustomDescription() : null;
            return new ItemDescriptionParts(name, attrs);
        }

        com.desitech.vyaparsathi.inventory.entity.ItemVariant variant = item.getItemVariant();
        com.desitech.vyaparsathi.inventory.entity.Item parentItem = variant.getItem();

        StringBuilder name = new StringBuilder(parentItem.getName() != null ? parentItem.getName() : "Item");
        if (isNonBlank(parentItem.getBrandName())) {
            name.append(" — ").append(parentItem.getBrandName());
        }

        StringJoiner attrs = new StringJoiner(" · ");
        if (isNonBlank(variant.getColor()))  attrs.add(variant.getColor());
        if (isNonBlank(variant.getSize()))   attrs.add(variant.getSize());
        if (isNonBlank(variant.getDesign())) attrs.add(variant.getDesign());
        if (isNonBlank(variant.getFit()))    attrs.add(variant.getFit());
        // SKU intentionally omitted — customer-facing document

        String attrsStr = attrs.length() == 0 ? null : attrs.toString();
        return new ItemDescriptionParts(name.toString(), attrsStr);
    }

    private static String appendComma(String existing, String addition) {
        if (addition == null || addition.isBlank()) return existing;
        if (existing == null || existing.isBlank()) return addition;
        return existing + " · " + addition;
    }

    /**
     * Two-line item description cell: bold name on line 1, small-muted
     * attribute list on line 2 (when attrs is non-empty). Matches the visual
     * hierarchy customers expect from Zoho / QuickBooks-style invoices.
     */
    private PdfPCell itemDescCell(String name, String attrs, Font nameFont, Font attrsFont, Color bg) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER_LIGHT);
        cell.setBorderWidth(0.5f);
        cell.setPaddingTop(5);
        cell.setPaddingBottom(5);
        cell.setPaddingLeft(4);
        cell.setPaddingRight(4);

        Paragraph p1 = new Paragraph(name != null ? name : "", nameFont);
        p1.setLeading(11f);
        cell.addElement(p1);

        if (attrs != null && !attrs.isBlank()) {
            Paragraph p2 = new Paragraph(attrs, attrsFont);
            p2.setLeading(10f);
            cell.addElement(p2);
        }
        return cell;
    }

    // ============================================================
    // 5. CALCULATIONS
    // ============================================================
    private void addCalculationSection(Document document, Sale sale, Fonts f, BigDecimal paid, Color brandColor)
            throws DocumentException {
        boolean isComposition = !saleHasGst(sale);

        PdfPTable main = new PdfPTable(2);
        main.setWidthPercentage(100);
        main.setWidths(new float[]{60, 40});
        main.setSpacingBefore(14f);

        // -------- LEFT: GST Summary + Amount in Words --------
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(0);

        BigDecimal totalCgst = ZERO;
        BigDecimal totalSgst = ZERO;
        BigDecimal totalIgst = ZERO;
        BigDecimal totalUtgst = ZERO;

        // Union-territory shops split intra-state GST as CGST + UTGST (not CGST + SGST).
        // Decide which column set this specific invoice needs so we don't waste a column
        // on a field that will always be zero.
        String shopStateCodeForGst = gstJurisdictionService.resolveStateCode(sale.getShop()).orElse(null);
        boolean shopIsUt = gstJurisdictionService.isUnionTerritory(shopStateCodeForGst);

        if (!isComposition) {
            Map<BigDecimal, GstSummary> gstMap = new LinkedHashMap<>();
            for (SaleItem item : sale.getSaleItems()) {
                BigDecimal rate = BigDecimal.valueOf(item.getGstType().getRate());
                GstSummary summary = gstMap.computeIfAbsent(rate, GstSummary::new);
                summary.addCgst(item.getCgstAmt());
                summary.addSgst(item.getSgstAmt());
                summary.addIgst(item.getIgstAmt());
                summary.addUtgst(item.getUtgstAmt());
            }

            PdfPTable gstWrap = new PdfPTable(1);
            gstWrap.setWidthPercentage(100);
            gstWrap.addCell(sectionLabelStrip("GST SUMMARY", f.sectionLabel));

            PdfPTable gstTable = new PdfPTable(4);
            gstTable.setWidthPercentage(100);
            String intraLabel = shopIsUt ? "UTGST" : "SGST";
            String[] gstHeaders = {"GST Rate", "CGST", intraLabel, "IGST"};
            for (String h : gstHeaders) {
                PdfPCell c = new PdfPCell(new Phrase(h, f.bodyBold));
                c.setBackgroundColor(SECTION_LABEL_BG);
                c.setBorder(Rectangle.BOX);
                c.setBorderColor(BORDER_LIGHT);
                c.setBorderWidth(0.5f);
                c.setPadding(5);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                gstTable.addCell(c);
            }
            for (GstSummary s : gstMap.values()) {
                gstTable.addCell(bodyCell(s.getRate() + "%", f.body, Element.ALIGN_CENTER, Color.WHITE));
                gstTable.addCell(bodyCell(currency.format(s.getCgst()), f.body, Element.ALIGN_RIGHT, Color.WHITE));
                BigDecimal intraValue = shopIsUt ? s.getUtgst() : s.getSgst();
                gstTable.addCell(bodyCell(currency.format(intraValue), f.body, Element.ALIGN_RIGHT, Color.WHITE));
                gstTable.addCell(bodyCell(currency.format(s.getIgst()), f.body, Element.ALIGN_RIGHT, Color.WHITE));
                totalCgst = totalCgst.add(s.getCgst());
                totalSgst = totalSgst.add(s.getSgst());
                totalUtgst = totalUtgst.add(s.getUtgst());
                totalIgst = totalIgst.add(s.getIgst());
            }
            PdfPCell gstBox = new PdfPCell(gstTable);
            gstBox.setBorder(Rectangle.NO_BORDER);
            gstBox.setPadding(0);
            gstWrap.addCell(gstBox);
            left.addElement(gstWrap);
        }

        // Reverse-charge notice — mandatory legal disclosure when tax burden
        // shifts to the recipient. Placed prominently between the tax summary
        // and the amount-in-words box so buyers cannot miss it.
        if (sale.isReverseCharge()) {
            PdfPTable rcWrap = new PdfPTable(1);
            rcWrap.setWidthPercentage(100);
            rcWrap.setSpacingBefore(8f);
            PdfPCell rcCell = new PdfPCell(new Phrase(
                    "TAX PAYABLE UNDER REVERSE CHARGE — Recipient is liable to pay GST to the government (CGST Act §9(3)/§9(4)).",
                    f.bodyBold));
            rcCell.setBackgroundColor(new Color(254, 243, 199)); // soft amber
            rcCell.setBorder(Rectangle.BOX);
            rcCell.setBorderColor(new Color(217, 119, 6));
            rcCell.setBorderWidth(0.8f);
            rcCell.setPadding(8);
            rcCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            rcWrap.addCell(rcCell);
            left.addElement(rcWrap);
        }

        // Amount in Words box
        PdfPTable wordsWrap = new PdfPTable(1);
        wordsWrap.setWidthPercentage(100);
        wordsWrap.setSpacingBefore(8f);
        wordsWrap.addCell(sectionLabelStrip("AMOUNT IN WORDS", f.sectionLabel));

        PdfPCell wordsBody = new PdfPCell(new Phrase(
                InvoiceUtil.numberToWords(sale.getTotalAmount()) + " Only", f.body));
        wordsBody.setBorder(Rectangle.BOX);
        wordsBody.setBorderColor(BORDER_LIGHT);
        wordsBody.setBorderWidth(0.5f);
        wordsBody.setPadding(8);
        wordsWrap.addCell(wordsBody);
        left.addElement(wordsWrap);

        main.addCell(left);

        // -------- RIGHT: Totals stack --------
        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(100);
        totals.setWidths(new float[]{55, 45});

        BigDecimal taxableTotal = sale.getSaleItems().stream()
                .map(SaleItem::getTaxableValue)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal grossSubtotal = taxableTotal.add(totalCgst).add(totalSgst).add(totalUtgst).add(totalIgst);

        if (!isComposition) {
            styledTotalRow(totals, "Taxable Amount", currency.format(taxableTotal), f.body, false);
            styledTotalRow(totals, "Total CGST",     currency.format(totalCgst),   f.body, false);
            // Render whichever intra-state component is non-zero. UT shops accumulate UTGST;
            // regular states accumulate SGST. Both are never non-zero on the same invoice.
            if (shopIsUt) {
                styledTotalRow(totals, "Total UTGST", currency.format(totalUtgst), f.body, false);
            } else {
                styledTotalRow(totals, "Total SGST",  currency.format(totalSgst),  f.body, false);
            }
            styledTotalRow(totals, "Total IGST",     currency.format(totalIgst),   f.body, false);
        }
        if (sale.getInvoiceDiscount() != null && sale.getInvoiceDiscount().compareTo(ZERO) > 0) {
            styledTotalRow(totals, "Subtotal (Gross)", currency.format(grossSubtotal), f.body, false);
            styledTotalRow(totals, "Discount", "- " + currency.format(sale.getInvoiceDiscount()), f.body, false);
        }
        if (sale.getShippingCharges() != null && sale.getShippingCharges().compareTo(ZERO) > 0) {
            styledTotalRow(totals, "Shipping Charges", currency.format(sale.getShippingCharges()), f.body, false);
        }
        if (sale.getOtherCharges() != null && sale.getOtherCharges().compareTo(ZERO) > 0) {
            styledTotalRow(totals, "Other Charges", currency.format(sale.getOtherCharges()), f.body, false);
        }

        // Grand Total — highlighted brand-color bar
        addGrandTotalRow(totals, "GRAND TOTAL", currency.format(sale.getTotalAmount()), f.grandTotalWhite, brandColor);

        styledTotalRow(totals, "Amount Paid", currency.format(paid), f.body, false);

        BigDecimal due = sale.getTotalAmount().subtract(paid).max(ZERO);
        Font dueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.NORMAL,
                due.compareTo(ZERO) > 0 ? DANGER_RED : SUCCESS_GREEN);
        styledTotalRow(totals, "Balance Due", currency.format(due), dueFont, false);

        PdfPCell right = new PdfPCell(totals);
        right.setBorder(Rectangle.BOX);
        right.setBorderColor(BORDER_LIGHT);
        right.setBorderWidth(0.5f);
        right.setPadding(6);
        main.addCell(right);

        document.add(main);
    }

    private void styledTotalRow(PdfPTable table, String label, String value, Font font, boolean highlight) {
        PdfPCell l = new PdfPCell(new Phrase(label, font));
        l.setBorder(Rectangle.NO_BORDER);
        l.setPaddingTop(4);
        l.setPaddingBottom(4);
        l.setPaddingLeft(6);
        if (highlight) l.setBackgroundColor(SECTION_LABEL_BG);
        table.addCell(l);

        PdfPCell v = new PdfPCell(new Phrase(value, font));
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPaddingTop(4);
        v.setPaddingBottom(4);
        v.setPaddingRight(6);
        if (highlight) v.setBackgroundColor(SECTION_LABEL_BG);
        table.addCell(v);
    }

    private void addGrandTotalRow(PdfPTable table, String label, String value, Font whiteFont, Color brandColor) {
        PdfPCell l = new PdfPCell(new Phrase(label, whiteFont));
        l.setBackgroundColor(brandColor);
        l.setBorder(Rectangle.NO_BORDER);
        l.setPaddingTop(7);
        l.setPaddingBottom(7);
        l.setPaddingLeft(8);
        table.addCell(l);

        PdfPCell v = new PdfPCell(new Phrase(value, whiteFont));
        v.setBackgroundColor(brandColor);
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPaddingTop(7);
        v.setPaddingBottom(7);
        v.setPaddingRight(8);
        table.addCell(v);
    }

    // ============================================================
    // 6. FOOTER
    // ============================================================
    private void addFinalFooter(Document document, Sale sale, Fonts f, BigDecimal paid) throws DocumentException {
        BigDecimal due = sale.getTotalAmount().subtract(paid).max(ZERO);

        String upiId = sale.getShop().getUpiId();
        byte[] qrBytes = null;
        if (isNonBlank(upiId)) {
            qrBytes = generateUPIDynamicQRCode(upiId, sale.getShop().getName(), due);
        }

        PdfPTable footer = new PdfPTable(2);
        footer.setWidthPercentage(100);
        footer.setWidths(new float[]{65, 35});
        footer.setSpacingBefore(16f);

        // ---- LEFT: Banking + QR + Terms ----
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(0);

        String bankDetails = isNonBlank(sale.getShop().getBankDetails())
                ? sale.getShop().getBankDetails()
                : defaultBankingDetails;

        PdfPTable bankWrap = new PdfPTable(1);
        bankWrap.setWidthPercentage(100);
        bankWrap.addCell(sectionLabelStrip("BANKING DETAILS", f.sectionLabel));

        PdfPCell bankBody = new PdfPCell();
        bankBody.setBorder(Rectangle.BOX);
        bankBody.setBorderColor(BORDER_LIGHT);
        bankBody.setBorderWidth(0.5f);
        bankBody.setPadding(8);

        if (qrBytes != null) {
            PdfPTable inner = new PdfPTable(2);
            inner.setWidthPercentage(100);
            inner.setWidths(new float[]{72, 28});

            PdfPCell details = new PdfPCell(new Phrase(formatBankDetails(bankDetails), f.small));
            details.setBorder(Rectangle.NO_BORDER);
            details.setPadding(2);
            inner.addCell(details);

            PdfPCell qrCell = new PdfPCell();
            qrCell.setBorder(Rectangle.NO_BORDER);
            qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            try {
                Image qrImg = Image.getInstance(qrBytes);
                qrImg.scaleToFit(58, 58);
                qrImg.setAlignment(Image.ALIGN_CENTER);
                qrCell.addElement(qrImg);

                Font scanFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 6, Font.NORMAL, TEXT_MUTED);
                Paragraph scanLabel = new Paragraph("SCAN TO PAY", scanFont);
                scanLabel.setAlignment(Element.ALIGN_CENTER);
                qrCell.addElement(scanLabel);
            } catch (Exception e) {
                logger.warn("Failed to render QR Code in PDF", e);
            }
            inner.addCell(qrCell);
            bankBody.addElement(inner);
        } else {
            bankBody.addElement(new Phrase(formatBankDetails(bankDetails), f.small));
        }
        bankWrap.addCell(bankBody);
        left.addElement(bankWrap);

        // Terms
        PdfPTable termsWrap = new PdfPTable(1);
        termsWrap.setWidthPercentage(100);
        termsWrap.setSpacingBefore(8f);
        termsWrap.addCell(sectionLabelStrip("TERMS & CONDITIONS", f.sectionLabel));

        String terms = isNonBlank(sale.getShop().getTermsAndConditions())
                ? sale.getShop().getTermsAndConditions()
                : defaultTermsAndConditions;

        PdfPCell termsBody = new PdfPCell();
        termsBody.setBorder(Rectangle.BOX);
        termsBody.setBorderColor(BORDER_LIGHT);
        termsBody.setBorderWidth(0.5f);
        termsBody.setPadding(8);
        for (String line : terms.split("\n")) {
            Paragraph p = new Paragraph("• " + line.trim(), f.smallMuted);
            p.setLeading(11f);
            termsBody.addElement(p);
        }
        termsWrap.addCell(termsBody);
        left.addElement(termsWrap);

        footer.addCell(left);

        // ---- RIGHT: Signature block ----
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setVerticalAlignment(Element.ALIGN_TOP);
        right.setPaddingLeft(10);

        Paragraph shopName = new Paragraph("For " + sale.getShop().getName().toUpperCase(), f.bodyBold);
        shopName.setAlignment(Element.ALIGN_RIGHT);
        shopName.setSpacingAfter(4f);
        right.addElement(shopName);

        byte[] sigBytes = invoiceUtil.loadImageBytes(sale.getShop().getSignaturePath(), "signature");
        if (sigBytes != null) {
            try {
                Image sigImg = Image.getInstance(sigBytes);
                sigImg.scaleToFit(130, 60);
                sigImg.setAlignment(Image.RIGHT);
                right.addElement(sigImg);
            } catch (Exception e) {
                logger.warn("Failed to render signature", e);
            }
        } else {
            Paragraph spacer = new Paragraph(" ");
            spacer.setSpacingAfter(40f);
            right.addElement(spacer);
        }

        Paragraph label = new Paragraph("Authorized Signatory", f.smallMuted);
        label.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(label);

        footer.addCell(right);
        document.add(footer);

        if (isNonBlank(sale.getShop().getInvoiceFooter())) {
            Paragraph customFooter = new Paragraph(sale.getShop().getInvoiceFooter(), f.smallMuted);
            customFooter.setAlignment(Element.ALIGN_CENTER);
            customFooter.setSpacingBefore(12f);
            document.add(customFooter);
        }
    }

    // ============================================================
    // Utility helpers
    // ============================================================
    private static boolean isNonBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static void appendLine(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append("\n");
        sb.append(s);
    }

    private static String nvl(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    public byte[] generatePdfBySaleIdOrInvoiceNo(Long saleId, String invoiceNo) {
        Sale sale;
        if (saleId != null) {
            sale = saleRepository.findById(saleId)
                    .orElseThrow(() -> new RuntimeException("Sale not found with ID: " + saleId));
        } else if (invoiceNo != null) {
            sale = saleRepository.findByInvoiceNo(invoiceNo);
            if (sale == null) throw new RuntimeException("Sale not found with Invoice No: " + invoiceNo);
        } else {
            throw new IllegalArgumentException("Either saleId or invoiceNo must be provided");
        }
        return generatePdf(sale);
    }
}
