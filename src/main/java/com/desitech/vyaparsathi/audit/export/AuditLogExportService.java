package com.desitech.vyaparsathi.audit.export;

import com.desitech.vyaparsathi.audit.AuditLogDto;
import com.desitech.vyaparsathi.common.exception.ExportAppException;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

@Service
public class AuditLogExportService {
    private static final Logger logger = LoggerFactory.getLogger(AuditLogExportService.class);

    // Unified column headers
    private static final String[] COLUMNS = {
            "ID", "Username", "Action", "Entity", "Entity ID", "Details", "Timestamp", "IP Address", "User Agent"
    };

    public byte[] exportAuditLogs(List<AuditLogDto> data, String format) {
        return switch (format.toLowerCase()) {
            case "csv" -> exportCsv(data);
            case "excel" -> exportExcel(data);
            case "pdf" -> exportPdf(data);
            default -> throw new IllegalArgumentException("Unsupported export format: " + format);
        };
    }

    private byte[] exportCsv(List<AuditLogDto> data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(COLUMNS))) {

            for (AuditLogDto dto : data) {
                printer.printRecord(
                        dto.getId(), dto.getUsername(), dto.getAction(), dto.getEntity(),
                        dto.getEntityId(), dto.getDetails(), dto.getTimestamp(),
                        dto.getIpAddress(), dto.getUserAgent()
                );
            }
            printer.flush();
            return out.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to export CSV", e);
            throw new ExportAppException("Failed to export audit logs as CSV", e);
        }
    }

    private byte[] exportExcel(List<AuditLogDto> data) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Audit Logs");
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);

            for (int i = 0; i < COLUMNS.length; i++) {
                header.createCell(i).setCellValue(COLUMNS[i]);
            }

            int rowIdx = 1;
            for (AuditLogDto dto : data) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(dto.getId());
                row.createCell(1).setCellValue(dto.getUsername());
                row.createCell(2).setCellValue(dto.getAction());
                row.createCell(3).setCellValue(dto.getEntity());
                row.createCell(4).setCellValue(dto.getEntityId());
                row.createCell(5).setCellValue(dto.getDetails());
                row.createCell(6).setCellValue(dto.getTimestamp() != null ? dto.getTimestamp().toString() : "");
                row.createCell(7).setCellValue(dto.getIpAddress());
                row.createCell(8).setCellValue(dto.getUserAgent());
            }

            // Auto-size columns for better readability
            for (int i = 0; i < COLUMNS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to export Excel", e);
            throw new ExportAppException("Failed to export audit logs as Excel", e);
        }
    }

    private byte[] exportPdf(List<AuditLogDto> data) {
        // Use A4 Landscape to fit the extra columns (IP and User Agent)
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, out);
            document.open();

            // Add a Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph title = new Paragraph("Security Audit Logs", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            PdfPTable table = new PdfPTable(COLUMNS.length);
            table.setWidthPercentage(100);

            // Table Headers
            for (String col : COLUMNS) {
                PdfPCell cell = new PdfPCell(new Phrase(col, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
                cell.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            // Table Data
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            for (AuditLogDto dto : data) {
                table.addCell(new Phrase(String.valueOf(dto.getId()), cellFont));
                table.addCell(new Phrase(dto.getUsername(), cellFont));
                table.addCell(new Phrase(dto.getAction(), cellFont));
                table.addCell(new Phrase(dto.getEntity(), cellFont));
                table.addCell(new Phrase(dto.getEntityId(), cellFont));
                table.addCell(new Phrase(dto.getDetails(), cellFont));
                table.addCell(new Phrase(dto.getTimestamp() != null ? dto.getTimestamp().toString() : "", cellFont));
                table.addCell(new Phrase(dto.getIpAddress(), cellFont));
                table.addCell(new Phrase(dto.getUserAgent(), cellFont));
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to export PDF", e);
            throw new ExportAppException("Failed to export audit logs as PDF", e);
        }
    }
}