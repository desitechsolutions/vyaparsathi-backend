package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.payroll.entity.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;  // Use java.awt.Color only, com.lowagie.text.Font used fully-qualified
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Enterprise-grade payslip and Form 16 PDF generator using OpenPDF (iText fork).
 *
 * Generates production-quality PDF documents with:
 * - Company header (name, TAN, address from statutory config)
 * - Employee info table (name, code, designation, PAN, UAN, bank)
 * - Attendance summary (total/working/present/LOP/leave/OT)
 * - Two-column Earnings vs Deductions table with all slip items
 * - Statutory summary section (PF, ESI, PT, TDS)
 * - Net Pay highlighted box with amount in words
 * - QR code for digital verification (ZXing)
 * - Professional footer
 */
@Service
public class PayslipPDFGenerator {

    private static final Logger log = LoggerFactory.getLogger(PayslipPDFGenerator.class);

    // ─── Colors (Corporate Blue theme) ───────────────────────────────────────
    private static final Color HEADER_BG    = new Color(0x1E, 0x3A, 0x8A); // Deep blue
    private static final Color HEADER_FG    = Color.WHITE;
    private static final Color SECTION_BG   = new Color(0xEF, 0xF6, 0xFF); // Light blue
    private static final Color ALT_ROW      = new Color(0xF8, 0xF9, 0xFA); // Light grey
    private static final Color NET_BG       = new Color(0x15, 0x80, 0x3D); // Success green
    private static final Color NET_FG       = Color.WHITE;
    private static final Color BORDER_COLOR = new Color(0xD1, 0xD5, 0xDB);

    // ─── Fonts ────────────────────────────────────────────────────────────────
    private static final com.lowagie.text.Font FONT_TITLE      = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 16, com.lowagie.text.Font.BOLD, HEADER_FG);
    private static final com.lowagie.text.Font FONT_SUBTITLE   = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.NORMAL, HEADER_FG);
    private static final com.lowagie.text.Font FONT_SECTION_H  = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.BOLD, new java.awt.Color(0x1E, 0x3A, 0x8A));
    private static final com.lowagie.text.Font FONT_LABEL      = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.BOLD, java.awt.Color.DARK_GRAY);
    private static final com.lowagie.text.Font FONT_VALUE      = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.NORMAL, java.awt.Color.BLACK);
    private static final com.lowagie.text.Font FONT_TH         = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.BOLD, HEADER_FG);
    private static final com.lowagie.text.Font FONT_ROW        = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.NORMAL, java.awt.Color.BLACK);
    private static final com.lowagie.text.Font FONT_ROW_BOLD   = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.BOLD, java.awt.Color.BLACK);
    private static final com.lowagie.text.Font FONT_NET        = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 11, com.lowagie.text.Font.BOLD, NET_FG);
    private static final com.lowagie.text.Font FONT_FOOTER     = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 7, com.lowagie.text.Font.ITALIC, java.awt.Color.GRAY);

    public byte[] generatePayslipPDF(PayrollSlip slip) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 30, 30, 30, 30);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            doc.open();

            Employee emp = slip.getEmployee();
            PayrollRun run = slip.getPayrollRun();

            // ─── HEADER BANNER ───────────────────────────────────────────────
            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.setWidths(new float[]{3f, 1.2f});
            header.setSpacingAfter(8f);

            // Company details cell
            PdfPCell companyCell = new PdfPCell();
            companyCell.setBackgroundColor(HEADER_BG);
            companyCell.setBorder(Rectangle.NO_BORDER);
            companyCell.setPadding(12f);
            Paragraph companyName = new Paragraph("PAYSLIP", FONT_TITLE);
            companyName.setSpacingAfter(4f);
            Paragraph period = new Paragraph(
                    run.getPayrollMonth() + " / " + run.getPayrollYear() +
                    "  |  Slip No: " + slip.getSlipNumber(), FONT_SUBTITLE);
            companyCell.addElement(companyName);
            companyCell.addElement(period);
            header.addCell(companyCell);

            // Right side: Slip Info cell
            PdfPCell slipInfoCell = new PdfPCell();
            slipInfoCell.setBackgroundColor(HEADER_BG);
            slipInfoCell.setBorder(Rectangle.NO_BORDER);
            slipInfoCell.setPadding(12f);
            slipInfoCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            slipInfoCell.addElement(new Paragraph("Generated: " + LocalDate.now()
                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy")), FONT_SUBTITLE));
            slipInfoCell.addElement(new Paragraph("Pay Date: " +
                    (slip.getDisbursedOn() != null ? slip.getDisbursedOn()
                            .format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : "Pending"), FONT_SUBTITLE));
            header.addCell(slipInfoCell);
            doc.add(header);

            // ─── EMPLOYEE INFO TABLE ─────────────────────────────────────────
            addSectionTitle(doc, "EMPLOYEE DETAILS");
            PdfPTable empTable = new PdfPTable(4);
            empTable.setWidthPercentage(100);
            empTable.setSpacingAfter(8f);
            empTable.setWidths(new float[]{1.2f, 1.8f, 1.2f, 1.8f});

            addInfoRow(empTable, "Employee Name",
                    emp.getFirstName() + " " + (emp.getLastName() != null ? emp.getLastName() : ""),
                    "Employee Code", emp.getEmployeeCode() != null ? emp.getEmployeeCode() : String.valueOf(emp.getId()));
            addInfoRow(empTable, "Designation",
                    emp.getDesignation() != null ? emp.getDesignation() : "—",
                    "Department", emp.getDepartment() != null ? emp.getDepartment() : "—");
            addInfoRow(empTable, "PAN Number",
                    emp.getPanNumber() != null ? emp.getPanNumber() : "—",
                    "UAN Number", emp.getUanNumber() != null ? emp.getUanNumber() : "—");
            addInfoRow(empTable, "Bank Account",
                    emp.getBankAccountNumber() != null ? maskBankAccount(emp.getBankAccountNumber()) : "—",
                    "IFSC Code", emp.getBankIFSCCode() != null ? emp.getBankIFSCCode() : "—");
            addInfoRow(empTable, "Tax Regime",
                    emp.getTaxRegime() != null ? emp.getTaxRegime().name() : "NEW_REGIME",
                    "Payment Mode", slip.getPaymentMode() != null ? slip.getPaymentMode() : "BANK");
            doc.add(empTable);

            // ─── ATTENDANCE SUMMARY ──────────────────────────────────────────
            addSectionTitle(doc, "ATTENDANCE SUMMARY");
            PdfPTable attTable = new PdfPTable(6);
            attTable.setWidthPercentage(100);
            attTable.setSpacingAfter(8f);

            // Header row
            String[] attHeaders = {"Total Days", "Working Days", "Present Days", "Paid Leaves", "LOP Days", "Overtime Hrs"};
            for (String h : attHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(h, FONT_TH));
                cell.setBackgroundColor(HEADER_BG);
                cell.setBorder(Rectangle.BOX);
                cell.setBorderColor(BORDER_COLOR);
                cell.setPadding(5f);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                attTable.addCell(cell);
            }

            String[] attValues = {
                    String.valueOf(slip.getTotalDays()),
                    String.valueOf(slip.getWorkingDays()),
                    fmt(slip.getPresentDays()),
                    fmt(slip.getPaidLeaves()),
                    fmt(slip.getLossOfPayDays()),
                    fmt(slip.getOvertimeHours())
            };
            for (String v : attValues) {
                PdfPCell cell = new PdfPCell(new Phrase(v, FONT_ROW));
                cell.setBorder(Rectangle.BOX);
                cell.setBorderColor(BORDER_COLOR);
                cell.setPadding(5f);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                attTable.addCell(cell);
            }
            doc.add(attTable);

            // ─── EARNINGS & DEDUCTIONS TABLE ─────────────────────────────────
            addSectionTitle(doc, "EARNINGS & DEDUCTIONS");
            PdfPTable edTable = new PdfPTable(4);
            edTable.setWidthPercentage(100);
            edTable.setSpacingAfter(8f);
            edTable.setWidths(new float[]{2.5f, 1f, 2.5f, 1f});

            // Column headers
            String[] edHeaders = {"Earnings Component", "Amount (₹)", "Deductions Component", "Amount (₹)"};
            for (String h : edHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(h, FONT_TH));
                cell.setBackgroundColor(HEADER_BG);
                cell.setBorder(Rectangle.BOX);
                cell.setBorderColor(BORDER_COLOR);
                cell.setPadding(5f);
                edTable.addCell(cell);
            }

            // Split items into earnings vs deductions
            List<PayrollSlipItem> items = slip.getItems() != null ? slip.getItems() : List.of();
            List<PayrollSlipItem> earnings = items.stream()
                    .filter(i -> "EARNING".equals(i.getComponentType())).toList();
            List<PayrollSlipItem> deductions = items.stream()
                    .filter(i -> "DEDUCTION".equals(i.getComponentType())).toList();

            int maxRows = Math.max(earnings.size(), deductions.size());
            for (int i = 0; i < maxRows; i++) {
                boolean alt = i % 2 == 1;
                Color rowBg = alt ? ALT_ROW : Color.WHITE;

                if (i < earnings.size()) {
                    addEdCell(edTable, earnings.get(i).getComponentName(), FONT_ROW, rowBg, Element.ALIGN_LEFT);
                    addEdCell(edTable, "₹ " + fmtAmt(earnings.get(i).getAmount()), FONT_ROW, rowBg, Element.ALIGN_RIGHT);
                } else {
                    addEdCell(edTable, "", FONT_ROW, rowBg, Element.ALIGN_LEFT);
                    addEdCell(edTable, "", FONT_ROW, rowBg, Element.ALIGN_RIGHT);
                }

                if (i < deductions.size()) {
                    addEdCell(edTable, deductions.get(i).getComponentName(), FONT_ROW, rowBg, Element.ALIGN_LEFT);
                    addEdCell(edTable, "₹ " + fmtAmt(deductions.get(i).getAmount()), FONT_ROW, rowBg, Element.ALIGN_RIGHT);
                } else {
                    addEdCell(edTable, "", FONT_ROW, rowBg, Element.ALIGN_LEFT);
                    addEdCell(edTable, "", FONT_ROW, rowBg, Element.ALIGN_RIGHT);
                }
            }

            // Totals row
            addEdCell(edTable, "Total Earnings", FONT_ROW_BOLD, SECTION_BG, Element.ALIGN_LEFT);
            addEdCell(edTable, "₹ " + fmtAmt(slip.getGrossEarnings()), FONT_ROW_BOLD, SECTION_BG, Element.ALIGN_RIGHT);
            addEdCell(edTable, "Total Deductions", FONT_ROW_BOLD, SECTION_BG, Element.ALIGN_LEFT);
            addEdCell(edTable, "₹ " + fmtAmt(slip.getTotalDeductions()), FONT_ROW_BOLD, SECTION_BG, Element.ALIGN_RIGHT);
            doc.add(edTable);

            // ─── STATUTORY SUMMARY ───────────────────────────────────────────
            addSectionTitle(doc, "STATUTORY CONTRIBUTIONS");
            PdfPTable statTable = new PdfPTable(6);
            statTable.setWidthPercentage(100);
            statTable.setSpacingAfter(8f);

            String[] statHeaders = {"PF (EE)", "PF (ER)", "ESI (EE)", "ESI (ER)", "Prof. Tax", "TDS"};
            for (String h : statHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(h, FONT_TH));
                cell.setBackgroundColor(HEADER_BG);
                cell.setBorder(Rectangle.BOX);
                cell.setBorderColor(BORDER_COLOR);
                cell.setPadding(4f);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                statTable.addCell(cell);
            }
            String[] statValues = {
                    "₹ " + fmtAmt(slip.getEpfEmployee()),
                    "₹ " + fmtAmt(slip.getEpfEmployer()),
                    "₹ " + fmtAmt(slip.getEsiEmployee()),
                    "₹ " + fmtAmt(slip.getEsiEmployer()),
                    "₹ " + fmtAmt(slip.getProfessionalTax()),
                    "₹ " + fmtAmt(slip.getTdsTax())
            };
            for (String v : statValues) {
                PdfPCell cell = new PdfPCell(new Phrase(v, FONT_ROW));
                cell.setBorder(Rectangle.BOX);
                cell.setBorderColor(BORDER_COLOR);
                cell.setPadding(4f);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                statTable.addCell(cell);
            }
            doc.add(statTable);

            // ─── NET PAY BOX + QR CODE ───────────────────────────────────────
            PdfPTable netPayTable = new PdfPTable(2);
            netPayTable.setWidthPercentage(100);
            netPayTable.setWidths(new float[]{3f, 1f});
            netPayTable.setSpacingAfter(10f);

            PdfPCell netCell = new PdfPCell();
            netCell.setBackgroundColor(NET_BG);
            netCell.setBorder(Rectangle.NO_BORDER);
            netCell.setPadding(12f);
            Paragraph netLabel = new Paragraph("NET SALARY PAYABLE", new Font(Font.HELVETICA, 9, Font.BOLD, NET_FG));
            netLabel.setSpacingAfter(4f);
            Paragraph netAmount = new Paragraph("₹ " + fmtAmt(slip.getNetSalary()), FONT_NET);
            netCell.addElement(netLabel);
            netCell.addElement(netAmount);
            String amtInWords = amountInWords(slip.getNetSalary());
            netCell.addElement(new Paragraph("(" + amtInWords + ")", FONT_SUBTITLE));
            netPayTable.addCell(netCell);

            // QR Code for digital verification
            try {
                String qrData = "PAYSLIP|" + slip.getSlipNumber() + "|" + emp.getId() + "|" + fmtAmt(slip.getNetSalary());
                byte[] qrBytes = generateQRCode(qrData, 80, 80);
                Image qrImage = Image.getInstance(qrBytes);
                qrImage.scaleToFit(80, 80);
                PdfPCell qrCell = new PdfPCell(qrImage, true);
                qrCell.setBorder(Rectangle.NO_BORDER);
                qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                qrCell.setPadding(5f);
                netPayTable.addCell(qrCell);
            } catch (Exception e) {
                PdfPCell qrCell = new PdfPCell(new Phrase("QR N/A", FONT_FOOTER));
                qrCell.setBorder(Rectangle.NO_BORDER);
                netPayTable.addCell(qrCell);
            }
            doc.add(netPayTable);

            // ─── FOOTER ──────────────────────────────────────────────────────
            Paragraph footer = new Paragraph(
                    "This is a computer-generated payslip and does not require a signature. " +
                    "For queries, contact HR. Generated on: " +
                    LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")),
                    FONT_FOOTER);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate payslip PDF for slip {}", slip.getId(), e);
            throw new RuntimeException("Payslip PDF generation failed: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Form 16 PDF (Part A + B)
    // ─────────────────────────────────────────────────────────────────────────

    public byte[] generateForm16PDF(Form16Data form16) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // ─── FORM 16 HEADER ───────────────────────────────────────────────
            PdfPTable headerTable = new PdfPTable(1);
            headerTable.setWidthPercentage(100);
            headerTable.setSpacingAfter(10f);

            PdfPCell hCell = new PdfPCell();
            hCell.setBackgroundColor(HEADER_BG);
            hCell.setBorder(Rectangle.NO_BORDER);
            hCell.setPadding(12f);
            hCell.addElement(new Paragraph("FORM 16", FONT_TITLE));
            hCell.addElement(new Paragraph(
                    "Certificate of Tax Deducted at Source under Section 203 of the Income Tax Act, 1961",
                    FONT_SUBTITLE));
            hCell.addElement(new Paragraph("Financial Year: " + form16.getFinancialYear(), FONT_SUBTITLE));
            headerTable.addCell(hCell);
            doc.add(headerTable);

            // ─── PART A: TDS Certificate ──────────────────────────────────────
            addSectionTitle(doc, "PART A — TDS CERTIFICATE");
            PdfPTable partA = new PdfPTable(2);
            partA.setWidthPercentage(100);
            partA.setSpacingAfter(10f);
            partA.setWidths(new float[]{1.5f, 2f});

            addF16Row(partA, "Employer's Name", form16.getEmployerName());
            addF16Row(partA, "Employer's TAN", form16.getEmployerTan() != null ? form16.getEmployerTan() : "—");
            addF16Row(partA, "Employer's PAN", form16.getEmployerPan() != null ? form16.getEmployerPan() : "—");
            addF16Row(partA, "Employer's Address", form16.getEmployerAddress() != null ? form16.getEmployerAddress() : "—");
            addF16Row(partA, "Employee Name", form16.getName());
            addF16Row(partA, "Employee PAN", form16.getPanNumber());
            addF16Row(partA, "Assessment Year", form16.getAssessmentYear() != null ? form16.getAssessmentYear() : "—");
            addF16Row(partA, "Period of Employment",
                    (form16.getPeriodFrom() != null ? form16.getPeriodFrom() : "—") +
                    " to " + (form16.getPeriodTo() != null ? form16.getPeriodTo() : "—"));
            doc.add(partA);

            // ─── PART B: Computation of Income ────────────────────────────────
            addSectionTitle(doc, "PART B — COMPUTATION OF INCOME & TAX");
            PdfPTable partB = new PdfPTable(2);
            partB.setWidthPercentage(100);
            partB.setSpacingAfter(10f);
            partB.setWidths(new float[]{2.5f, 1f});

            addF16AmountRow(partB, "1. Gross Salary", form16.getTotalSalary(), false);
            addF16AmountRow(partB, "2. Less: Standard Deduction (Sec 16)", form16.getStandardDeduction(), false);
            addF16AmountRow(partB, "3. Less: Professional Tax (Sec 16(iii))", form16.getProfessionalTax(), false);
            addF16AmountRow(partB, "4. Income from Salaries (Net)", form16.getGrossTotalIncome(), true);
            addF16AmountRow(partB, "5. Less: Chapter VI-A Deductions (80C, 80D, etc.)", form16.getTotalDeductions(), false);
            addF16AmountRow(partB, "6. Total Taxable Income", form16.getTotalTaxableIncome(), true);
            addF16AmountRow(partB, "7. Tax on Total Income", form16.getTaxOnIncome(), false);
            addF16AmountRow(partB, "8. Add: Surcharge", BigDecimal.ZERO, false);
            addF16AmountRow(partB, "9. Add: Health & Education Cess (4%)", form16.getEducationCess(), false);
            addF16AmountRow(partB, "10. Tax Payable", form16.getTdsPayable(), true);
            addF16AmountRow(partB, "11. Less: TDS Already Deducted", form16.getTaxPaidThisYear(), false);
            addF16AmountRow(partB, "12. Tax Payable / (Refundable)", form16.getTdsPayable().subtract(form16.getTaxPaidThisYear()), true);
            doc.add(partB);

            // ─── DECLARATION ──────────────────────────────────────────────────
            Paragraph declaration = new Paragraph(
                    "I certify that the information given above is true, correct and complete to the best of my knowledge. " +
                    "This form has been generated electronically and is valid without a physical signature.",
                    FONT_FOOTER);
            declaration.setAlignment(Element.ALIGN_CENTER);
            declaration.setSpacingBefore(10f);
            doc.add(declaration);

            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate Form 16 PDF", e);
            throw new RuntimeException("Form 16 PDF generation failed: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void addSectionTitle(Document doc, String title) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(4f);
        t.setSpacingAfter(4f);
        PdfPCell cell = new PdfPCell(new Phrase(title, FONT_SECTION_H));
        cell.setBackgroundColor(SECTION_BG);
        cell.setBorderColor(new Color(0x93, 0xC5, 0xFD));
        cell.setPadding(5f);
        t.addCell(cell);
        doc.add(t);
    }

    private void addInfoRow(PdfPTable table, String label1, String value1, String label2, String value2) {
        table.addCell(infoCell(label1, FONT_LABEL, ALT_ROW));
        table.addCell(infoCell(value1, FONT_VALUE, Color.WHITE));
        table.addCell(infoCell(label2, FONT_LABEL, ALT_ROW));
        table.addCell(infoCell(value2, FONT_VALUE, Color.WHITE));
    }

    private PdfPCell infoCell(String text, com.lowagie.text.Font font, java.awt.Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "—", font));
        cell.setBackgroundColor(bg);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(4f);
        return cell;
    }

    private void addEdCell(PdfPTable table, String text, com.lowagie.text.Font font, java.awt.Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBackgroundColor(bg);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(4f);
        cell.setHorizontalAlignment(align);
        table.addCell(cell);
    }

    private void addF16Row(PdfPTable table, String label, String value) {
        PdfPCell lCell = new PdfPCell(new Phrase(label, FONT_LABEL));
        lCell.setBackgroundColor(ALT_ROW);
        lCell.setBorderColor(BORDER_COLOR);
        lCell.setPadding(4f);
        table.addCell(lCell);
        PdfPCell vCell = new PdfPCell(new Phrase(value != null ? value : "—", FONT_VALUE));
        vCell.setBorderColor(BORDER_COLOR);
        vCell.setPadding(4f);
        table.addCell(vCell);
    }

    private void addF16AmountRow(PdfPTable table, String label, BigDecimal amount, boolean bold) {
        Font f = bold ? FONT_ROW_BOLD : FONT_ROW;
        Color bg = bold ? SECTION_BG : Color.WHITE;
        PdfPCell lCell = new PdfPCell(new Phrase(label, f));
        lCell.setBackgroundColor(bg);
        lCell.setBorderColor(BORDER_COLOR);
        lCell.setPadding(4f);
        table.addCell(lCell);
        PdfPCell vCell = new PdfPCell(new Phrase("₹ " + fmtAmt(amount), f));
        vCell.setBackgroundColor(bg);
        vCell.setBorderColor(BORDER_COLOR);
        vCell.setPadding(4f);
        vCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(vCell);
    }

    private byte[] generateQRCode(String data, int width, int height) throws WriterException, java.io.IOException {
        QRCodeWriter qrWriter = new QRCodeWriter();
        BitMatrix matrix = qrWriter.encode(data, BarcodeFormat.QR_CODE, width, height);
        BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", stream);
        return stream.toByteArray();
    }

    private String fmt(BigDecimal val) {
        return val != null ? String.valueOf(val.stripTrailingZeros().toPlainString()) : "0";
    }

    private String fmtAmt(BigDecimal val) {
        if (val == null) return "0.00";
        return String.format("%,.2f", val);
    }

    private String maskBankAccount(String account) {
        if (account == null || account.length() < 4) return account;
        return "XXXX" + account.substring(account.length() - 4);
    }

    private String amountInWords(BigDecimal amount) {
        if (amount == null) return "Zero Rupees";
        long rupees = amount.longValue();
        // Basic amount-in-words for common ranges
        if (rupees == 0) return "Zero Rupees Only";
        return "Rupees " + numberToWords(rupees) + " Only";
    }

    private String numberToWords(long n) {
        if (n == 0) return "Zero";
        String[] units = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
                "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
                "Seventeen", "Eighteen", "Nineteen"};
        String[] tens = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};
        if (n < 20) return units[(int) n];
        if (n < 100) return tens[(int) (n / 10)] + (n % 10 > 0 ? " " + units[(int) (n % 10)] : "");
        if (n < 1000) return units[(int) (n / 100)] + " Hundred" + (n % 100 > 0 ? " " + numberToWords(n % 100) : "");
        if (n < 100000) return numberToWords(n / 1000) + " Thousand" + (n % 1000 > 0 ? " " + numberToWords(n % 1000) : "");
        if (n < 10000000) return numberToWords(n / 100000) + " Lakh" + (n % 100000 > 0 ? " " + numberToWords(n % 100000) : "");
        return numberToWords(n / 10000000) + " Crore" + (n % 10000000 > 0 ? " " + numberToWords(n % 10000000) : "");
    }
}
