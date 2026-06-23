package com.desitech.vyaparsathi.invoice.service;

import com.desitech.vyaparsathi.common.exception.ExportAppException;
import com.desitech.vyaparsathi.delivery.entity.Delivery;
import com.desitech.vyaparsathi.inventory.entity.Item;
import com.desitech.vyaparsathi.inventory.enums.DrugSchedule;
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
import java.time.LocalDate;
import java.util.*;
import java.util.List;

import static com.desitech.vyaparsathi.invoice.utils.InvoiceUtil.*;
import static java.math.BigDecimal.ZERO;

@Service
public class InvoiceService {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceService.class);

    private static final Color LIGHT_GREY      = new Color(245, 245, 245);
    private static final Color SUCCESS_GREEN   = new Color(39, 174, 96);
    private static final Color WARNING_ORANGE  = new Color(243, 156, 18);
    private static final Color DANGER_RED      = new Color(231, 76, 60);

    private final NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private SaleRepository saleRepository;
    @Autowired
    private InvoiceUtil invoiceUtil;



    @Value("${shop.banking.details:Bank Name: XYZ Bank\nAccount: 123456789\nIFSC: XYZB0001234}")
    private String defaultBankingDetails;

    @Value("${invoice.terms:1. Goods once sold will not be taken back.\n2. Payment due within 30 days.\n3. Subject to local jurisdiction.}")
    private String defaultTermsAndConditions;

    @Value("${invoice.pharmacy.terms:1. Medicines once sold cannot be returned or exchanged.\n2. Please check the medicine name, dosage and expiry before purchase.\n3. Prescription medicines are dispensed only against a valid prescription.\n4. Keep medicines out of reach of children.\n5. Store as directed on the label.}")
    private String defaultPharmacyTerms;

    public byte[] generatePdf(Sale sale) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 75, 45);
            PdfWriter writer = PdfWriter.getInstance(document, baos);

            byte[] logoBytes = invoiceUtil.loadImageBytes(sale.getShop().getLogoPath(), "logo");
            Color brandColor = parseColor(sale.getShop().getBrandColor(), new Color(41, 128, 185));

            writer.setPageEvent(new InvoicePageEvent(logoBytes, brandColor));
            document.open();

            Font titleFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Font.NORMAL, brandColor);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, Color.WHITE);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, Color.BLACK);
            Font boldFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, Color.BLACK);
            Font smallFont  = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, Color.DARK_GRAY);

            boolean isPharmacy = isPharmacyShop(sale);

            addProfessionalHeader(document, sale, titleFont, normalFont, boldFont, isPharmacy);
            if (isPharmacy) {
                addPharmacyAddressSection(document, sale, normalFont, boldFont, brandColor);
                addPharmacyItemTable(document, sale, headerFont, normalFont, boldFont, brandColor);
            } else {
                addAddressSection(document, sale, normalFont, boldFont, brandColor);
                addItemTable(document, sale, headerFont, normalFont, boldFont, brandColor);
            }
            addCalculationSection(document, sale, normalFont, boldFont);
            addFinalFooter(document, sale, normalFont, boldFont, smallFont, isPharmacy);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("PDF generation failed for sale ID: {}", sale.getId(), e);
            throw new ExportAppException("Failed to generate invoice PDF for sale ID: " + sale.getId(), e);
        }
    }

    /**
     * Determines whether this specific sale was billed with GST.
     * Uses the sale's own {@code isGstRequired} flag (captured at sale-creation time)
     * so that invoices remain correct even after a shop later toggles its
     * composition-scheme setting.
     */
    private boolean saleHasGst(Sale sale) {
        return Boolean.TRUE.equals(sale.getIsGstRequired());
    }

    /** Returns true when the sale's shop is a pharmacy. */
    private boolean isPharmacyShop(Sale sale) {
        String industry = sale.getShop().getIndustryType();
        return industry != null && "PHARMACY".equalsIgnoreCase(industry);
    }

    private void addProfessionalHeader(Document document, Sale sale, Font titleFont, Font normalFont, Font boldFont,
                                        boolean isPharmacy)
            throws DocumentException {

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{60, 40});

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.addElement(new Phrase(sale.getShop().getName().toUpperCase(), boldFont));
        left.addElement(new Phrase("\n" + sale.getShop().getAddress(), normalFont));
        if (sale.getShop().getGstin() != null) {
            left.addElement(new Phrase("\nGSTIN: " + sale.getShop().getGstin(), normalFont));
        }
        if (sale.getShop().getCompanyWebsite() != null && !sale.getShop().getCompanyWebsite().isBlank()) {
            left.addElement(new Phrase("\nWebsite: " + sale.getShop().getCompanyWebsite(), normalFont));
        }
        if (sale.getShop().getSupportContact() != null && !sale.getShop().getSupportContact().isBlank()) {
            left.addElement(new Phrase("\nSupport: " + sale.getShop().getSupportContact(), normalFont));
        }
        if (isPharmacy && sale.getShop().getDrugLicenseNumber() != null && !sale.getShop().getDrugLicenseNumber().isBlank()) {
            left.addElement(new Phrase("\nDrug Lic. No: " + sale.getShop().getDrugLicenseNumber(), normalFont));
        }
        table.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setPadding(4);

        boolean isComposition = !saleHasGst(sale);

        String invoiceTitle;
        if (isPharmacy) {
            invoiceTitle = "PHARMACY BILL";
        } else if (isComposition) {
            invoiceTitle = "BILL OF SUPPLY";
        } else {
            invoiceTitle = "TAX INVOICE";
        }
        right.addElement(new Paragraph(invoiceTitle, titleFont));

        if (isComposition && !isPharmacy) {
            Font declFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7, Color.DARK_GRAY);
            Paragraph decl = new Paragraph("Composition taxable person, not eligible to collect tax on supplies", declFont);
            decl.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(decl);
        }

        right.addElement(new Paragraph("Invoice No: " + sale.getInvoiceNo(), boldFont));
        right.addElement(new Paragraph("Date: " + sale.getDate().toLocalDate(), normalFont));
        if (sale.getDueDate() != null) {
            right.addElement(new Paragraph("Due Date: " + sale.getDueDate(), normalFont));
        }

        // Payment status badge
        BigDecimal paid = paymentService.getTotalPaidBySaleIds(Set.of(sale.getId())).getOrDefault(sale.getId(), ZERO);
        String status = paid.compareTo(sale.getTotalAmount()) >= 0 ? "PAID"
                : paid.compareTo(ZERO) > 0 ? "PARTIALLY PAID" : "DUE";

        Color statusColor = "PAID".equals(status) ? SUCCESS_GREEN
                : "PARTIALLY PAID".equals(status) ? WARNING_ORANGE : DANGER_RED;

        PdfPTable badgeTable = new PdfPTable(1);
        badgeTable.setTotalWidth(90);
        badgeTable.setLockedWidth(true);
        badgeTable.setHorizontalAlignment(Element.ALIGN_RIGHT);

        PdfPCell badgeCell = new PdfPCell(new Phrase(status,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE)));
        badgeCell.setBackgroundColor(statusColor);
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badgeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        badgeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        badgeCell.setPadding(4);

        badgeTable.addCell(badgeCell);
        right.addElement(badgeTable);

        table.addCell(right);
        document.add(table);
        document.add(new Paragraph("\n"));
    }
    private void addAddressSection(Document document, Sale sale, Font normalFont, Font boldFont, Color brandColor)
            throws DocumentException {

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{50, 50});

        PdfPCell billHeader = new PdfPCell(new Phrase("BILL TO", boldFont));
        billHeader.setBackgroundColor(brandColor);
        billHeader.setPadding(6);
        table.addCell(billHeader);

        PdfPCell shipHeader = new PdfPCell(new Phrase("SHIP TO", boldFont));
        shipHeader.setBackgroundColor(brandColor);
        shipHeader.setPadding(6);
        table.addCell(shipHeader);

        PdfPCell billCell = new PdfPCell();
        billCell.setPadding(8);
        if (sale.getCustomer() != null) {
            billCell.addElement(new Phrase(sale.getCustomer().getName(), boldFont));
            if (sale.getCustomer().getAddressLine1() != null) {
                billCell.addElement(new Phrase("\n" + sale.getCustomer().getAddressLine1(), normalFont));
            }
            String cityState = formatCityState(sale.getCustomer().getCity(), sale.getCustomer().getState());
            if (!cityState.isEmpty()) {
                billCell.addElement(new Phrase("\n" + cityState, normalFont));
            }
            if (sale.getCustomer().getGstNumber() != null) {
                billCell.addElement(new Phrase("\nGSTIN: " + sale.getCustomer().getGstNumber(), normalFont));
            }
        } else {
            billCell.addElement(new Phrase("Walk-in Customer", normalFont));
        }
        table.addCell(billCell);

        PdfPCell shipCell = new PdfPCell();
        shipCell.setPadding(8);
        Delivery latest = sale.getLatestDelivery();
        if (sale.getCustomer() != null) {
            boolean hasCustomDeliveryAddress = latest != null
                    && latest.getDeliveryAddress() != null
                    && !latest.getDeliveryAddress().trim().isEmpty();
            String shipAddr = hasCustomDeliveryAddress
                    ? latest.getDeliveryAddress()
                    : buildCustomerAddress(sale.getCustomer().getAddressLine1(),
                                          sale.getCustomer().getCity(),
                                          sale.getCustomer().getState());
            shipCell.addElement(new Phrase(sale.getCustomer().getName(), boldFont));
            shipCell.addElement(new Phrase("\n" + shipAddr, normalFont));
        } else if (latest != null && latest.getDeliveryAddress() != null && !latest.getDeliveryAddress().trim().isEmpty()) {
            shipCell.addElement(new Phrase("Walk-in Customer", boldFont));
            shipCell.addElement(new Phrase("\n" + latest.getDeliveryAddress(), normalFont));
        } else {
            shipCell.addElement(new Phrase("Walk-in Customer", normalFont));
        }
        table.addCell(shipCell);

        document.add(table);
        document.add(new Paragraph("\n"));
    }

    /**
     * Pharmacy-specific address section.
     * Uses "PATIENT" label instead of "BILL TO / SHIP TO".
     * Shows phone number prominently (used for prescription tracking).
     */
    private void addPharmacyAddressSection(Document document, Sale sale, Font normalFont, Font boldFont, Color brandColor)
            throws DocumentException {

        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);

        PdfPCell patientHeader = new PdfPCell(new Phrase("PATIENT / CUSTOMER DETAILS", boldFont));
        patientHeader.setBackgroundColor(brandColor);
        patientHeader.setPadding(6);
        table.addCell(patientHeader);

        PdfPCell patientCell = new PdfPCell();
        patientCell.setPadding(8);
        if (sale.getCustomer() != null) {
            // Combine name and phone on a single line
            String nameLine = "Name: " + sale.getCustomer().getName();
            if (sale.getCustomer().getPhone() != null) {
                nameLine += "    |    Phone: " + sale.getCustomer().getPhone();
            }
            patientCell.addElement(new Phrase(nameLine, boldFont));
            if (sale.getCustomer().getAddressLine1() != null) {
                patientCell.addElement(new Phrase("\nAddress: " + sale.getCustomer().getAddressLine1(), normalFont));
            }
        } else {
            patientCell.addElement(new Phrase("Walk-in Customer", normalFont));
        }
        table.addCell(patientCell);

        document.add(table);
        document.add(new Paragraph("\n"));
    }

    /**
     * Pharmacy-specific item table.
     * Columns: #, Medicine Name + Drug Schedule, Batch No, Expiry Date, HSN, MRP, Qty, Unit, Rate, Total.
     * For non-composition shops, also shows GST%.
     */
    private void addPharmacyItemTable(Document document, Sale sale, Font headerFont, Font normalFont, Font boldFont, Color brandColor)
            throws DocumentException {

        boolean isComposition = !saleHasGst(sale);
        // Columns: #, Medicine, Batch No, Expiry, HSN, MRP, Qty, Unit, Rate, [GST%,] Total
        int columns = isComposition ? 10 : 11;

        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);

        if (isComposition) {
            table.setWidths(new float[]{3, 22, 11, 9, 8, 9, 6, 6, 10, 16});
        } else {
            table.setWidths(new float[]{3, 20, 10, 9, 7, 9, 6, 5, 9, 7, 15});
        }

        List<String> headers = new ArrayList<>(List.of(
                "#", "Medicine / Item", "Batch No", "Expiry", "HSN", "MRP", "Qty", "Unit", "Rate"));
        if (!isComposition) {
            headers.add("GST %");
        }
        headers.add("Total");

        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(brandColor);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(5);
            table.addCell(cell);
        }

        int rowNum = 1;
        for (SaleItem item : sale.getSaleItems()) {
            BigDecimal qty      = item.getQty() != null ? item.getQty() : ZERO;
            BigDecimal retQty   = item.getReturnedQty() != null ? item.getReturnedQty() : ZERO;
            BigDecimal rate     = item.getUnitPrice() != null ? item.getUnitPrice() : ZERO;
            BigDecimal taxable  = item.getTaxableValue() != null ? item.getTaxableValue() : ZERO;
            BigDecimal cgst     = item.getCgstAmt() != null ? item.getCgstAmt() : ZERO;
            BigDecimal sgst     = item.getSgstAmt() != null ? item.getSgstAmt() : ZERO;
            BigDecimal igst     = item.getIgstAmt() != null ? item.getIgstAmt() : ZERO;
            BigDecimal lineTotal = taxable.add(cgst).add(sgst).add(igst);

            table.addCell(createCell(String.valueOf(rowNum++), normalFont, Element.ALIGN_CENTER));

            // Medicine name + drug schedule badge
            Item parentItem = item.getItemVariant().getItem();
            String medicineName = parentItem.getName();
            if (retQty.compareTo(ZERO) > 0) medicineName += " (Ret: " + retQty + ")";
            DrugSchedule schedule = parentItem.getDrugSchedule();
            if (schedule != null && schedule != DrugSchedule.OTC) {
                medicineName += " [" + formatDrugSchedule(schedule) + "]";
            }
            table.addCell(createCell(medicineName, normalFont, Element.ALIGN_LEFT));

            // Batch No
            String batch = item.getItemVariant().getBatchNumber();
            table.addCell(createCell(batch != null ? batch : "-", normalFont, Element.ALIGN_CENTER));

            // Expiry Date
            LocalDate expiry = item.getItemVariant().getExpiryDate();
            table.addCell(createCell(expiry != null ? expiry.toString() : "-", normalFont, Element.ALIGN_CENTER));

            // HSN
            table.addCell(createCell(item.getItemVariant().getHsn() != null ? item.getItemVariant().getHsn() : "-", normalFont, Element.ALIGN_CENTER));

            // MRP
            BigDecimal mrp = item.getItemVariant().getMrp();
            table.addCell(createCell(mrp != null ? currency.format(mrp) : "-", normalFont, Element.ALIGN_RIGHT));

            // Qty
            table.addCell(createCell(qty.toString(), normalFont, Element.ALIGN_CENTER));

            // Unit
            table.addCell(createCell(item.getItemVariant().getUnit() != null ? item.getItemVariant().getUnit() : "-", normalFont, Element.ALIGN_CENTER));

            // Rate
            table.addCell(createCell(currency.format(rate), normalFont, Element.ALIGN_RIGHT));

            // GST % (non-composition only)
            if (!isComposition) {
                table.addCell(createCell(item.getGstType().getRate() + "%", normalFont, Element.ALIGN_CENTER));
            }

            // Total
            table.addCell(createCell(currency.format(lineTotal), boldFont, Element.ALIGN_RIGHT));
        }

        document.add(table);
    }

    /** Returns a short human-readable label for the drug schedule. */
    private String formatDrugSchedule(DrugSchedule schedule) {
        return switch (schedule) {
            case SCHEDULE_X    -> "Sch-X";
            case SCHEDULE_H    -> "Sch-H";
            case SCHEDULE_H1   -> "Sch-H1";
            case NON_SCHEDULED -> "Non-Sch";
            default -> "";
        };
    }

    /** Formats city and state into a single line, handling null/empty values. */
    private String formatCityState(String city, String state) {
        String c = city != null ? city.trim() : "";
        String s = state != null ? state.trim() : "";
        if (c.isEmpty() && s.isEmpty()) return "";
        if (c.isEmpty()) return s;
        if (s.isEmpty()) return c;
        return c + ", " + s;
    }

    /** Builds a full address string from individual components, handling nulls. */
    private String buildCustomerAddress(String addressLine1, String city, String state) {
        StringBuilder sb = new StringBuilder();
        if (addressLine1 != null && !addressLine1.isEmpty()) sb.append(addressLine1);
        String cityState = formatCityState(city, state);
        if (!cityState.isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(cityState);
        }
        return sb.toString();
    }

    private void addItemTable(Document document, Sale sale, Font headerFont, Font normalFont, Font boldFont, Color brandColor)
            throws DocumentException {

        boolean isComposition = !saleHasGst(sale);
        int columns = isComposition ? 7 : 10;

        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);

        if (isComposition) {
            table.setWidths(new float[]{4, 38, 12, 10, 8, 12, 16});
        } else {
            table.setWidths(new float[]{4, 24, 10, 6, 6, 10, 8, 8, 10, 14});
        }

        List<String> headers = new ArrayList<>(List.of("#", "Item Description", "HSN", "Qty", "Unit", "Rate"));
        if (!isComposition) {
            headers.addAll(List.of("GST %", "Disc", "Taxable Amt"));
        }
        headers.add("Total");

        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(brandColor);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(5);
            table.addCell(cell);
        }

        int rowNum = 1;
        for (SaleItem item : sale.getSaleItems()) {
            BigDecimal qty        = item.getQty() != null ? item.getQty() : ZERO;
            BigDecimal retQty     = item.getReturnedQty() != null ? item.getReturnedQty() : ZERO;
            BigDecimal rate       = item.getUnitPrice() != null ? item.getUnitPrice() : ZERO;
            BigDecimal discount   = item.getDiscount() != null ? item.getDiscount() : ZERO;
            BigDecimal taxable    = item.getTaxableValue() != null ? item.getTaxableValue() : ZERO;
            BigDecimal cgst       = item.getCgstAmt() != null ? item.getCgstAmt() : ZERO;
            BigDecimal sgst       = item.getSgstAmt() != null ? item.getSgstAmt() : ZERO;
            BigDecimal igst       = item.getIgstAmt() != null ? item.getIgstAmt() : ZERO;

            BigDecimal lineTotal = taxable.add(cgst).add(sgst).add(igst);

            table.addCell(createCell(String.valueOf(rowNum++), normalFont, Element.ALIGN_CENTER));

            String desc = item.getItemVariant().getItem().getName();
            if (retQty.compareTo(ZERO) > 0) desc += " (Returned: " + retQty + ")";
            table.addCell(createCell(desc, normalFont, Element.ALIGN_LEFT));

            table.addCell(createCell(item.getItemVariant().getHsn() != null ? item.getItemVariant().getHsn() : "-", normalFont, Element.ALIGN_CENTER));
            table.addCell(createCell(qty.toString(), normalFont, Element.ALIGN_CENTER));
            table.addCell(createCell(item.getItemVariant().getUnit() != null ? item.getItemVariant().getUnit() : "-", normalFont, Element.ALIGN_CENTER));
            table.addCell(createCell(currency.format(rate), normalFont, Element.ALIGN_RIGHT));

            if (!isComposition) {
                table.addCell(createCell(item.getGstType().getRate() + "%", normalFont, Element.ALIGN_CENTER));
                table.addCell(createCell(currency.format(discount), normalFont, Element.ALIGN_RIGHT));
                table.addCell(createCell(currency.format(taxable), normalFont, Element.ALIGN_RIGHT));
            }

            table.addCell(createCell(currency.format(lineTotal), boldFont, Element.ALIGN_RIGHT));
        }

        document.add(table);
    }
    private void addCalculationSection(Document document, Sale sale, Font normalFont, Font boldFont) throws DocumentException {
        boolean isComposition = !saleHasGst(sale);

        PdfPTable main = new PdfPTable(2);
        main.setWidthPercentage(100);
        main.setWidths(new float[]{60, 40});
        main.setSpacingBefore(15);

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);

        BigDecimal totalCgst = ZERO;
        BigDecimal totalSgst = ZERO;
        BigDecimal totalIgst = ZERO;

        if (!isComposition) {
            left.addElement(new Paragraph("GST SUMMARY", boldFont));

            PdfPTable gstTable = new PdfPTable(4);
            gstTable.setWidthPercentage(100);

            String[] gstHeaders = {"GST Rate", "CGST Amt", "SGST Amt", "IGST Amt"};
            for (String h : gstHeaders) {
                PdfPCell c = new PdfPCell(new Phrase(h, boldFont));
                c.setBackgroundColor(LIGHT_GREY);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                gstTable.addCell(c);
            }

            Map<BigDecimal, GstSummary> gstMap = new LinkedHashMap<>();
            for (SaleItem item : sale.getSaleItems()) {
                BigDecimal rate = BigDecimal.valueOf(item.getGstType().getRate());
                GstSummary summary = gstMap.computeIfAbsent(rate, GstSummary::new);
                summary.addCgst(item.getCgstAmt());
                summary.addSgst(item.getSgstAmt());
                summary.addIgst(item.getIgstAmt());
            }

            for (GstSummary s : gstMap.values()) {
                gstTable.addCell(createCell(s.getRate() + "%", normalFont, Element.ALIGN_CENTER));
                gstTable.addCell(createCell(currency.format(s.getCgst()), normalFont, Element.ALIGN_RIGHT));
                gstTable.addCell(createCell(currency.format(s.getSgst()), normalFont, Element.ALIGN_RIGHT));
                gstTable.addCell(createCell(currency.format(s.getIgst()), normalFont, Element.ALIGN_RIGHT));

                totalCgst = totalCgst.add(s.getCgst());
                totalSgst = totalSgst.add(s.getSgst());
                totalIgst = totalIgst.add(s.getIgst());
            }

            left.addElement(gstTable);
        }

        left.addElement(new Paragraph("\nAmount in Words: " + InvoiceUtil.numberToWords(sale.getTotalAmount()) + " Only", normalFont));

        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(100);

        BigDecimal taxableTotal = sale.getSaleItems().stream()
                .map(SaleItem::getTaxableValue)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);

        if (!isComposition) {
            addTotalRow(totals, "Taxable Amount:", currency.format(taxableTotal), normalFont);
            addTotalRow(totals, "Total CGST:",     currency.format(totalCgst),   normalFont);
            addTotalRow(totals, "Total SGST:",     currency.format(totalSgst),   normalFont);
            addTotalRow(totals, "Total IGST:",     currency.format(totalIgst),   normalFont);
        }

        if (sale.getInvoiceDiscount() != null && sale.getInvoiceDiscount().compareTo(ZERO) > 0) {
            addTotalRow(totals, "Invoice Discount:", "-" + currency.format(sale.getInvoiceDiscount()), normalFont);
        }
        if (sale.getShippingCharges() != null && sale.getShippingCharges().compareTo(ZERO) > 0) {
            addTotalRow(totals, "Shipping Charges:", currency.format(sale.getShippingCharges()), normalFont);
        }
        if (sale.getOtherCharges() != null && sale.getOtherCharges().compareTo(ZERO) > 0) {
            addTotalRow(totals, "Other Charges:", currency.format(sale.getOtherCharges()), normalFont);
        }

        addTotalRow(totals, "Grand Total:", currency.format(sale.getTotalAmount()), boldFont);

        BigDecimal paid = paymentService.getTotalPaidBySaleIds(Set.of(sale.getId()))
                .getOrDefault(sale.getId(), ZERO);

        addTotalRow(totals, "Amount Paid:", currency.format(paid), normalFont);

        BigDecimal due = sale.getTotalAmount().subtract(paid).max(ZERO);
        Font dueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.NORMAL,
                due.compareTo(ZERO) > 0 ? DANGER_RED : SUCCESS_GREEN);
        addTotalRow(totals, "Balance Due:", currency.format(due), dueFont);

        PdfPCell right = new PdfPCell(totals);
        right.setBorder(Rectangle.NO_BORDER);

        main.addCell(left);
        main.addCell(right);
        document.add(main);
    }
    private void addFinalFooter(Document document, Sale sale, Font normalFont, Font boldFont, Font smallFont,
                                boolean isPharmacy)
            throws DocumentException {

        document.add(new Paragraph("\n"));

        BigDecimal paid = paymentService.getTotalPaidBySaleIds(Set.of(sale.getId()))
                .getOrDefault(sale.getId(), ZERO);
        BigDecimal due = sale.getTotalAmount().subtract(paid).max(ZERO);

        String upiId = sale.getShop().getUpiId();
        byte[] qrBytes = null;
        if (upiId != null && !upiId.isBlank()) {
            qrBytes = generateUPIDynamicQRCode(upiId, sale.getShop().getName(), due);
        }

        PdfPTable footer = new PdfPTable(2);
        footer.setWidthPercentage(100);
        footer.setWidths(new float[]{65, 35});
        footer.setSpacingBefore(20);

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);

        if (isPharmacy) {
            // Show Drug License prominently for pharmacy shops
            if (sale.getShop().getDrugLicenseNumber() != null && !sale.getShop().getDrugLicenseNumber().isBlank()) {
                left.addElement(new Phrase("DRUG LICENSE NO: " + sale.getShop().getDrugLicenseNumber(), boldFont));
                left.addElement(new Phrase("\n", smallFont));
            }
        }

        String bankDetails = sale.getShop().getBankDetails() != null && !sale.getShop().getBankDetails().trim().isEmpty()
                ? sale.getShop().getBankDetails()
                : defaultBankingDetails;

        if (qrBytes != null) {
            PdfPTable paymentTable = new PdfPTable(2);
            paymentTable.setWidthPercentage(100);
            paymentTable.setWidths(new float[]{72, 28});

            PdfPCell bankCell = new PdfPCell();
            bankCell.setBorder(Rectangle.NO_BORDER);
            bankCell.addElement(new Phrase("BANKING DETAILS", boldFont));
            bankCell.addElement(new Phrase("\n" + formatBankDetails(bankDetails), smallFont));
            paymentTable.addCell(bankCell);

            PdfPCell qrCell = new PdfPCell();
            qrCell.setBorder(Rectangle.NO_BORDER);
            qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            try {
                Image qrImg = Image.getInstance(qrBytes);
                qrImg.scaleToFit(55, 55);
                qrImg.setAlignment(Image.ALIGN_CENTER);
                qrCell.addElement(qrImg);

                Font scanFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 6, Font.NORMAL, Color.DARK_GRAY);
                Paragraph scanLabel = new Paragraph("SCAN TO PAY", scanFont);
                scanLabel.setAlignment(Element.ALIGN_CENTER);
                qrCell.addElement(scanLabel);
            } catch (Exception e) {
                logger.warn("Failed to render QR Code in PDF", e);
            }
            paymentTable.addCell(qrCell);
            left.addElement(paymentTable);
        } else {
            left.addElement(new Phrase("BANKING DETAILS", boldFont));
            left.addElement(new Phrase("\n" + formatBankDetails(bankDetails), smallFont));
        }

        left.addElement(new Phrase("\n\nTERMS & CONDITIONS", boldFont));

        String terms;
        if (sale.getShop().getTermsAndConditions() != null && !sale.getShop().getTermsAndConditions().isEmpty()) {
            terms = sale.getShop().getTermsAndConditions();
        } else if (isPharmacy) {
            terms = defaultPharmacyTerms;
        } else {
            terms = defaultTermsAndConditions;
        }

        for (String line : terms.split("\n")) {
            left.addElement(new Phrase("\n• " + line.trim(), smallFont));
        }

        footer.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setVerticalAlignment(Element.ALIGN_BOTTOM);

        Paragraph shopName = new Paragraph("For " + sale.getShop().getName().toUpperCase(), boldFont);
        shopName.setAlignment(Element.ALIGN_RIGHT);
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
        }

        Paragraph label = new Paragraph(isPharmacy ? "Licensed Pharmacist" : "Authorized Signatory", normalFont);
        label.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(label);

        footer.addCell(right);

        document.add(footer);

        if (sale.getShop().getInvoiceFooter() != null && !sale.getShop().getInvoiceFooter().isBlank()) {
            Paragraph customFooter = new Paragraph("\n" + sale.getShop().getInvoiceFooter(), smallFont);
            customFooter.setAlignment(Element.ALIGN_CENTER);
            document.add(customFooter);
        }
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