package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.entity.ReceivingItem;
import com.desitech.vyaparsathi.receiving.repository.ReceivingRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * XLSX export of the GRN register. Complements the CSV path in
 * {@link ReceivingReportService} — same rows, richer formatting so a
 * finance user can drop the file straight into their spreadsheet.
 */
@Service
public class ReceivingExcelExporter {

    private final ReceivingRepository receivingRepository;

    public ReceivingExcelExporter(ReceivingRepository receivingRepository) {
        this.receivingRepository = receivingRepository;
    }

    @Transactional(readOnly = true)
    public byte[] exportGrnXlsx(LocalDate from, LocalDate to) {
        Long shopId = TenantUtils.getCurrentShopId();
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("GRN Register");
            CellStyle headerStyle = wb.createCellStyle();
            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            headerStyle.setFont(boldFont);

            String[] headers = {"GRN", "PO", "Supplier", "Received On", "Status",
                    "Received Qty", "Damaged", "Rejected", "Accepted", "Value"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Receiving r : receivingRepository.findAllByShopId(shopId)) {
                if (from != null && r.getReceivedAt() != null && r.getReceivedAt().toLocalDate().isBefore(from)) continue;
                if (to != null && r.getReceivedAt() != null && r.getReceivedAt().toLocalDate().isAfter(to)) continue;
                int recv = 0, dmg = 0, rej = 0, acc = 0;
                BigDecimal value = BigDecimal.ZERO;
                if (r.getItems() != null) {
                    for (ReceivingItem it : r.getItems()) {
                        recv += Optional.ofNullable(it.getReceivedQty()).orElse(0);
                        dmg += Optional.ofNullable(it.getDamagedQty()).orElse(0);
                        rej += Optional.ofNullable(it.getRejectedQty()).orElse(0);
                        acc += it.getAcceptedQty();
                        BigDecimal cost = it.getUnitCost() != null ? it.getUnitCost()
                                : (it.getPurchaseOrderItem() != null
                                    ? Optional.ofNullable(it.getPurchaseOrderItem().getUnitCost()).orElse(BigDecimal.ZERO)
                                    : BigDecimal.ZERO);
                        int qty = (it.getReceivedQty() == null ? 0 : it.getReceivedQty())
                                + (it.getDamagedQty() == null ? 0 : it.getDamagedQty())
                                + (it.getRejectedQty() == null ? 0 : it.getRejectedQty());
                        value = value.add(cost.multiply(BigDecimal.valueOf(qty)));
                    }
                }
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(r.getGrNumber() != null ? r.getGrNumber() : "");
                row.createCell(1).setCellValue(r.getPurchaseOrder() != null && r.getPurchaseOrder().getPoNumber() != null
                        ? r.getPurchaseOrder().getPoNumber() : "");
                row.createCell(2).setCellValue(r.getPurchaseOrder() != null && r.getPurchaseOrder().getSupplier() != null
                        ? r.getPurchaseOrder().getSupplier().getName() : "");
                row.createCell(3).setCellValue(r.getReceivedAt() != null ? r.getReceivedAt().toLocalDate().toString() : "");
                row.createCell(4).setCellValue(r.getStatus() != null ? r.getStatus().name() : "");
                row.createCell(5).setCellValue(recv);
                row.createCell(6).setCellValue(dmg);
                row.createCell(7).setCellValue(rej);
                row.createCell(8).setCellValue(acc);
                row.createCell(9).setCellValue(value.doubleValue());
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            wb.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write XLSX", e);
        }
    }
}
