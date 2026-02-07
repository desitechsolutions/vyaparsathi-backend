package com.desitech.vyaparsathi.analytics.service;

import com.desitech.vyaparsathi.analytics.dto.PurchaseOrderSuggestionDto;
import com.desitech.vyaparsathi.common.exception.ExportAppException;
// PDF (iText)
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.FontFactory;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

// Excel (Apache POI)
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

// CSV
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

// Spring & Utils
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;


@Service
public class AnalyticsExportService {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsExportService.class);

    public byte[] exportPurchaseSuggestions(List<PurchaseOrderSuggestionDto> data, String format) {
        return switch (format.toLowerCase()) {
            case "xlsx", "excel" -> exportSuggestionsToExcel(data);
            case "pdf" -> exportSuggestionsToPdf(data);
            default -> exportSuggestionsToCsv(data);
        };
    }

    private byte[] exportSuggestionsToExcel(List<PurchaseOrderSuggestionDto> data) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Procurement Plan");

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row header = sheet.createRow(0);
            String[] columns = {"Item Name", "Suggested Qty", "Estimated Investment (INR)"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (PurchaseOrderSuggestionDto dto : data) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(dto.getItemName());
                row.createCell(1).setCellValue(dto.getSuggestedQuantity());
                row.createCell(2).setCellValue(dto.getEstimatedCost().doubleValue());
            }

            for (int i = 0; i < columns.length; i++) sheet.autoSizeColumn(i);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ExportAppException("Excel export failed", e);
        }
    }

    private byte[] exportSuggestionsToPdf(List<PurchaseOrderSuggestionDto> data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            com.lowagie.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            document.add(new Paragraph("Procurement & Investment Forecast", titleFont));
            document.add(new Paragraph("Generated on: " + java.time.LocalDate.now()));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(3);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);

            // Table Headers
            Stream.of("Item Name", "Suggested Qty", "Est. Investment").forEach(columnTitle -> {
                PdfPCell header = new PdfPCell();
                header.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
                header.setPhrase(new Phrase(columnTitle, FontFactory.getFont(FontFactory.HELVETICA_BOLD)));
                header.setPadding(5);
                table.addCell(header);
            });

            for (PurchaseOrderSuggestionDto dto : data) {
                table.addCell(dto.getItemName());
                table.addCell(String.valueOf(dto.getSuggestedQuantity()));
                table.addCell("Rs. " + dto.getEstimatedCost().toPlainString());
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ExportAppException("PDF export failed", e);
        }
    }
    private byte[] exportSuggestionsToCsv(List<PurchaseOrderSuggestionDto> data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                     .withHeader("Item ID", "Item Name", "Suggested Quantity", "Estimated Investment (INR)"))) {

            for (PurchaseOrderSuggestionDto dto : data) {
                csvPrinter.printRecord(
                        dto.getItemId(),
                        dto.getItemName(),
                        dto.getSuggestedQuantity(),
                        dto.getEstimatedCost() != null ? dto.getEstimatedCost().toPlainString() : "0.00"
                );
            }

            csvPrinter.flush();
            return out.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to export purchase suggestions as CSV", e);
            throw new ExportAppException("Failed to export analytics as CSV", e);
        }
    }
}