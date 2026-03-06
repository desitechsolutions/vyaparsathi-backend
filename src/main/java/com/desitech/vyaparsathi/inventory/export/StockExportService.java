package com.desitech.vyaparsathi.inventory.export;

import com.desitech.vyaparsathi.common.exception.ExportAppException;
import com.desitech.vyaparsathi.inventory.dto.CurrentStockDto;
import com.desitech.vyaparsathi.inventory.dto.StockMovementDto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    // ==========================================
    // MOVEMENT EXPORT LOGIC
    // ==========================================

    public byte[] exportStockMovements(List<StockMovementDto> data, String format) {
        if ("csv".equalsIgnoreCase(format)) {
            return exportMovementsCsv(data);
        } else if ("excel".equalsIgnoreCase(format)) {
            return exportMovementsExcel(data);
        } else if ("pdf".equalsIgnoreCase(format)) {
            return exportMovementsPdf(data);
        }
        throw new IllegalArgumentException("Unsupported export format: " + format);
    }

    private byte[] exportMovementsCsv(List<StockMovementDto> data) {
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

    private byte[] exportMovementsExcel(List<StockMovementDto> data) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Stock Movements");
            Row header = sheet.createRow(0);
            String[] columns = {"ID", "Item Name", "SKU", "Movement Type", "Quantity", "Cost Per Unit", "Batch", "Reason", "Reference", "Timestamp"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
            }
            int rowIdx = 1;
            for (StockMovementDto dto : data) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(dto.getId());
                row.createCell(1).setCellValue(dto.getItemName());
                row.createCell(2).setCellValue(dto.getSku());
                row.createCell(3).setCellValue(dto.getMovementType() != null ? dto.getMovementType().name() : "");
                row.createCell(4).setCellValue(dto.getQuantity() != null ? dto.getQuantity().doubleValue() : 0);
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

    private byte[] exportMovementsPdf(List<StockMovementDto> data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, out);
            document.open();

            Paragraph title = new Paragraph("Stock Movement Audit Log", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16));
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(10);
            table.setWidthPercentage(100);
            String[] columns = {"ID", "Item Name", "SKU", "Type", "Qty", "Cost", "Batch", "Reason", "Ref", "Time"};
            for (String col : columns) {
                PdfPCell cell = new PdfPCell(new Phrase(col, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
                cell.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
                table.addCell(cell);
            }
            for (StockMovementDto dto : data) {
                table.addCell(new Phrase(String.valueOf(dto.getId()), FontFactory.getFont(FontFactory.HELVETICA, 9)));
                table.addCell(new Phrase(dto.getItemName(), FontFactory.getFont(FontFactory.HELVETICA, 9)));
                table.addCell(new Phrase(dto.getSku(), FontFactory.getFont(FontFactory.HELVETICA, 9)));
                table.addCell(new Phrase(dto.getMovementType() != null ? dto.getMovementType().name() : "", FontFactory.getFont(FontFactory.HELVETICA, 9)));
                table.addCell(new Phrase(dto.getQuantity().toString(), FontFactory.getFont(FontFactory.HELVETICA, 9)));
                table.addCell(new Phrase(dto.getCostPerUnit() != null ? dto.getCostPerUnit().toString() : "0", FontFactory.getFont(FontFactory.HELVETICA, 9)));
                table.addCell(new Phrase(dto.getBatch() != null ? dto.getBatch() : "", FontFactory.getFont(FontFactory.HELVETICA, 9)));
                table.addCell(new Phrase(dto.getReason() != null ? dto.getReason() : "", FontFactory.getFont(FontFactory.HELVETICA, 9)));
                table.addCell(new Phrase(dto.getReference() != null ? dto.getReference() : "", FontFactory.getFont(FontFactory.HELVETICA, 9)));
                table.addCell(new Phrase(dto.getTimestamp() != null ? dto.getTimestamp().toString().substring(0, 16) : "", FontFactory.getFont(FontFactory.HELVETICA, 8)));
            }
            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to export stock movements as PDF", e);
            throw new ExportAppException("Failed to export stock movements as PDF", e);
        }
    }

    // ==========================================
    // CURRENT STOCK EXPORT LOGIC
    // ==========================================

    public byte[] exportCurrentStock(List<CurrentStockDto> data, String format) {
        if ("csv".equalsIgnoreCase(format)) {
            return exportCurrentCsv(data);
        } else if ("excel".equalsIgnoreCase(format)) {
            return exportCurrentExcel(data);
        } else if ("pdf".equalsIgnoreCase(format)) {
            return exportCurrentPdf(data);
        }
        throw new IllegalArgumentException("Unsupported export format: " + format);
    }

    private byte[] exportCurrentCsv(List<CurrentStockDto> data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT
                     .withHeader("Variant ID", "Item Name", "SKU", "Color", "Size", "Current Stock", "Unit", "WAC (Purchase Cost)", "Selling Price"))) {
            for (CurrentStockDto dto : data) {
                printer.printRecord(
                        dto.getItemVariantId(),
                        dto.getItemName(),
                        dto.getSku(),
                        dto.getColor(),
                        dto.getSize(),
                        dto.getTotalQuantity(),
                        dto.getUnit(),
                        dto.getCostPerUnit(),
                        dto.getPricePerUnit()
                );
            }
            printer.flush();
            return out.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to export current stock as CSV", e);
            throw new ExportAppException("Failed to export current stock as CSV", e);
        }
    }

    private byte[] exportCurrentExcel(List<CurrentStockDto> data) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Current Stock Summary");
            Row header = sheet.createRow(0);
            String[] columns = {"Variant ID", "Item Name", "SKU", "Color", "Size", "Current Stock", "Unit", "WAC (Purchase Cost)", "Selling Price"};
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }
            int rowIdx = 1;
            for (CurrentStockDto dto : data) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(dto.getItemVariantId());
                row.createCell(1).setCellValue(dto.getItemName());
                row.createCell(2).setCellValue(dto.getSku());
                row.createCell(3).setCellValue(dto.getColor());
                row.createCell(4).setCellValue(dto.getSize());
                row.createCell(5).setCellValue(dto.getTotalQuantity() != null ? dto.getTotalQuantity().doubleValue() : 0);
                row.createCell(6).setCellValue(dto.getUnit());
                row.createCell(7).setCellValue(dto.getCostPerUnit() != null ? dto.getCostPerUnit().doubleValue() : 0);
                row.createCell(8).setCellValue(dto.getPricePerUnit() != null ? dto.getPricePerUnit().doubleValue() : 0);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to export current stock as Excel", e);
            throw new ExportAppException("Failed to export current stock as Excel", e);
        }
    }

    private byte[] exportCurrentPdf(List<CurrentStockDto> data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, out);
            document.open();

            Paragraph title = new Paragraph("Current Inventory Summary Report", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16));
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(9);
            table.setWidthPercentage(100);
            String[] columns = {"ID", "Item Name", "SKU", "Color", "Size", "Qty", "Unit", "WAC (Purchase Cost)", "Selling Price"};
            for (String col : columns) {
                PdfPCell cell = new PdfPCell(new Phrase(col, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
                cell.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
                table.addCell(cell);
            }
            for (CurrentStockDto dto : data) {
                table.addCell(new Phrase(String.valueOf(dto.getItemVariantId()), FontFactory.getFont(FontFactory.HELVETICA, 9)));
                table.addCell(new Phrase(dto.getItemName(), FontFactory.getFont(FontFactory.HELVETICA, 9)));
                table.addCell(new Phrase(dto.getSku(), FontFactory.getFont(FontFactory.HELVETICA, 9)));
                table.addCell(new Phrase(dto.getColor() != null ? dto.getColor() : "", FontFactory.getFont(FontFactory.HELVETICA, 9)));
                table.addCell(new Phrase(dto.getSize() != null ? dto.getSize() : "", FontFactory.getFont(FontFactory.HELVETICA, 9)));
                table.addCell(new Phrase(dto.getTotalQuantity().toString(), FontFactory.getFont(FontFactory.HELVETICA, 9)));
                table.addCell(new Phrase(dto.getUnit(), FontFactory.getFont(FontFactory.HELVETICA, 9)));
                table.addCell(new Phrase(dto.getCostPerUnit() != null ? dto.getCostPerUnit().toString() : "0", FontFactory.getFont(FontFactory.HELVETICA, 9)));
                table.addCell(new Phrase(dto.getPricePerUnit() != null ? dto.getPricePerUnit().toString() : "0", FontFactory.getFont(FontFactory.HELVETICA, 9)));
            }
            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to export current stock as PDF", e);
            throw new ExportAppException("Failed to export current stock as PDF", e);
        }
    }
}