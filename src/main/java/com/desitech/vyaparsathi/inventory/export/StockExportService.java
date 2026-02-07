package com.desitech.vyaparsathi.inventory.export;

import com.desitech.vyaparsathi.common.exception.ExportAppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.desitech.vyaparsathi.inventory.dto.StockMovementDto;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVFormat;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

@Service
public class StockExportService {

    private static final Logger logger = LoggerFactory.getLogger(StockExportService.class);
    public byte[] exportStockMovements(List<StockMovementDto> data, String format) {
        if ("csv".equalsIgnoreCase(format)) {
            return exportCsv(data);
        } else if ("excel".equalsIgnoreCase(format)) {
            return exportExcel(data);
        } else if ("pdf".equalsIgnoreCase(format)) {
            return exportPdf(data);
        }
        throw new IllegalArgumentException("Unsupported export format: " + format);
    }

    private byte[] exportCsv(List<StockMovementDto> data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT
                     .withHeader("ID", "Item Name", "SKU", "Movement Type", "Quantity", "Cost Per Unit", "Batch", "Reason", "Reference", "Timestamp"))) {
            for (StockMovementDto dto : data) {
                printer.printRecord(
                        dto.getId(),
                        dto.getItemName(),
                        dto.getSku(),
                        dto.getMovementType(),
                        dto.getQuantity(),
                        dto.getCostPerUnit(),
                        dto.getBatch(),
                        dto.getReason(),
                        dto.getReference(),
                        dto.getTimestamp()
                );
            }
            printer.flush();
            return out.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to export stock movements as CSV", e);
            throw new ExportAppException("Failed to export stock movements as CSV", e);
        }
    }

    private byte[] exportExcel(List<StockMovementDto> data) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Stock Movements");
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            String[] columns = {"ID", "Item Name", "SKU", "Movement Type", "Quantity", "Cost Per Unit", "Batch", "Reason", "Reference", "Timestamp"};
            for (int i = 0; i < columns.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
            }
            int rowIdx = 1;
            for (StockMovementDto dto : data) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(dto.getId());
                row.createCell(1).setCellValue(dto.getItemName());
                row.createCell(2).setCellValue(dto.getSku());
                row.createCell(3).setCellValue(dto.getMovementType().name());
                row.createCell(4).setCellValue(dto.getQuantity().doubleValue());
                row.createCell(5).setCellValue(dto.getCostPerUnit() != null ? dto.getCostPerUnit().doubleValue() : 0);
                row.createCell(6).setCellValue(dto.getBatch());
                row.createCell(7).setCellValue(dto.getReason());
                row.createCell(8).setCellValue(dto.getReference());
                row.createCell(9).setCellValue(dto.getTimestamp() != null ? dto.getTimestamp().toString() : "");
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to export stock movements as Excel", e);
            throw new ExportAppException("Failed to export stock movements as Excel", e);
        }
    }

    private byte[] exportPdf(List<StockMovementDto> data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();
            PdfPTable table = new PdfPTable(10);
            String[] columns = {"ID", "Item Name", "SKU", "Movement Type", "Quantity", "Cost Per Unit", "Batch", "Reason", "Reference", "Timestamp"};
            for (String col : columns) {
                PdfPCell cell = new PdfPCell(new Phrase(col));
                cell.setBackgroundColor(com.lowagie.text.pdf.GrayColor.LIGHT_GRAY);
                table.addCell(cell);
            }
            for (StockMovementDto dto : data) {
                table.addCell(String.valueOf(dto.getId()));
                table.addCell(dto.getItemName());
                table.addCell(dto.getSku());
                table.addCell(dto.getMovementType().name());
                table.addCell(dto.getQuantity().toString());
                table.addCell(dto.getCostPerUnit() != null ? dto.getCostPerUnit().toString() : "");
                table.addCell(dto.getBatch());
                table.addCell(dto.getReason());
                table.addCell(dto.getReference());
                table.addCell(dto.getTimestamp() != null ? dto.getTimestamp().toString() : "");
            }
            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to export stock movements as PDF", e);
            throw new ExportAppException("Failed to export stock movements as PDF", e);
        }
    }
}
