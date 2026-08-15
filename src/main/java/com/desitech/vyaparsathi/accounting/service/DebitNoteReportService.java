package com.desitech.vyaparsathi.accounting.service;

import com.desitech.vyaparsathi.accounting.entity.DebitNote;
import com.desitech.vyaparsathi.accounting.enums.DebitNoteStatus;
import com.desitech.vyaparsathi.accounting.repository.DebitNoteRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DN reporting surface: aging bucket, supplier-wise summary, CSV/XLSX
 * exports. Shop scoping comes from {@code ShopFilterAspect} on the repo.
 */
@Service
public class DebitNoteReportService {

    private final DebitNoteRepository debitNoteRepository;

    public DebitNoteReportService(DebitNoteRepository debitNoteRepository) {
        this.debitNoteRepository = debitNoteRepository;
    }

    /** DNs with unapplied balance, bucketed by age of the DN date. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aging() {
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (DebitNote dn : debitNoteRepository.findAll()) {
            if (dn.getStatus() == DebitNoteStatus.CANCELLED) continue;
            BigDecimal outstanding = dn.getTotalAmount().subtract(dn.getAppliedAmount() == null ? BigDecimal.ZERO : dn.getAppliedAmount());
            if (outstanding.signum() <= 0) continue;
            long age = dn.getDebitNoteDate() != null
                    ? ChronoUnit.DAYS.between(dn.getDebitNoteDate(), today) : 0;
            String bucket = age <= 30 ? "0-30" : age <= 60 ? "31-60" : age <= 90 ? "61-90" : "90+";
            Map<String, Object> row = new HashMap<>();
            row.put("id", dn.getId());
            row.put("debitNoteNo", dn.getDebitNoteNo());
            row.put("debitNoteDate", dn.getDebitNoteDate());
            row.put("ageDays", age);
            row.put("ageBucket", bucket);
            row.put("supplierId", dn.getSupplier() != null ? dn.getSupplier().getId() : null);
            row.put("supplierName", dn.getSupplier() != null ? dn.getSupplier().getName() : null);
            row.put("totalAmount", dn.getTotalAmount());
            row.put("appliedAmount", dn.getAppliedAmount());
            row.put("outstanding", outstanding.setScale(2, RoundingMode.HALF_UP));
            row.put("status", dn.getStatus());
            out.add(row);
        }
        out.sort((a, b) -> ((BigDecimal) b.get("outstanding")).compareTo((BigDecimal) a.get("outstanding")));
        return out;
    }

    /** Supplier-wise summary: DN count, issued value, applied, outstanding. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> supplierSummary() {
        Map<Long, Map<String, Object>> agg = new HashMap<>();
        for (DebitNote dn : debitNoteRepository.findAll()) {
            if (dn.getSupplier() == null) continue;
            Long sid = dn.getSupplier().getId();
            Map<String, Object> row = agg.computeIfAbsent(sid, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("supplierId", sid);
                m.put("supplierName", dn.getSupplier().getName());
                m.put("dnCount", 0);
                m.put("issued", BigDecimal.ZERO);
                m.put("applied", BigDecimal.ZERO);
                m.put("outstanding", BigDecimal.ZERO);
                return m;
            });
            row.put("dnCount", ((Integer) row.get("dnCount")) + 1);
            row.put("issued", ((BigDecimal) row.get("issued")).add(dn.getTotalAmount()));
            BigDecimal applied = dn.getAppliedAmount() == null ? BigDecimal.ZERO : dn.getAppliedAmount();
            row.put("applied", ((BigDecimal) row.get("applied")).add(applied));
            if (dn.getStatus() != DebitNoteStatus.CANCELLED) {
                row.put("outstanding", ((BigDecimal) row.get("outstanding"))
                        .add(dn.getTotalAmount().subtract(applied)));
            }
        }
        return new ArrayList<>(agg.values()).stream()
                .sorted((a, b) -> ((BigDecimal) b.get("outstanding")).compareTo((BigDecimal) a.get("outstanding")))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter pw = new PrintWriter(baos, false, StandardCharsets.UTF_8)) {
            pw.println("DebitNoteNo,Date,Supplier,Reason,Total,Applied,Outstanding,Status");
            for (DebitNote dn : debitNoteRepository.findAll()) {
                BigDecimal applied = dn.getAppliedAmount() == null ? BigDecimal.ZERO : dn.getAppliedAmount();
                BigDecimal outstanding = dn.getTotalAmount().subtract(applied);
                pw.println(String.join(",",
                        csv(dn.getDebitNoteNo()),
                        csv(dn.getDebitNoteDate() != null ? dn.getDebitNoteDate().toString() : ""),
                        csv(dn.getSupplier() != null ? dn.getSupplier().getName() : ""),
                        csv(dn.getReason() != null ? dn.getReason() : ""),
                        dn.getTotalAmount().toPlainString(),
                        applied.toPlainString(),
                        outstanding.toPlainString(),
                        csv(dn.getStatus() != null ? dn.getStatus().name() : "")));
            }
        }
        return baos.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] exportXlsx() {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Debit Notes");
            Row h = sheet.createRow(0);
            String[] headers = {"DN No", "Date", "Supplier", "Reason", "Taxable", "CGST", "SGST", "IGST", "Total", "Applied", "Outstanding", "Status"};
            for (int i = 0; i < headers.length; i++) h.createCell(i).setCellValue(headers[i]);
            int rowIdx = 1;
            for (DebitNote dn : debitNoteRepository.findAll()) {
                BigDecimal applied = dn.getAppliedAmount() == null ? BigDecimal.ZERO : dn.getAppliedAmount();
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(dn.getDebitNoteNo() != null ? dn.getDebitNoteNo() : "");
                row.createCell(1).setCellValue(dn.getDebitNoteDate() != null ? dn.getDebitNoteDate().toString() : "");
                row.createCell(2).setCellValue(dn.getSupplier() != null && dn.getSupplier().getName() != null ? dn.getSupplier().getName() : "");
                row.createCell(3).setCellValue(dn.getReason() != null ? dn.getReason() : "");
                row.createCell(4).setCellValue(dn.getTaxableAmount().doubleValue());
                row.createCell(5).setCellValue(dn.getCgstAmount().doubleValue());
                row.createCell(6).setCellValue(dn.getSgstAmount().doubleValue());
                row.createCell(7).setCellValue(dn.getIgstAmount().doubleValue());
                row.createCell(8).setCellValue(dn.getTotalAmount().doubleValue());
                row.createCell(9).setCellValue(applied.doubleValue());
                row.createCell(10).setCellValue(dn.getTotalAmount().subtract(applied).doubleValue());
                row.createCell(11).setCellValue(dn.getStatus() != null ? dn.getStatus().name() : "");
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            wb.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("XLSX export failed", e);
        }
    }

    private String csv(String v) {
        if (v == null) return "";
        String c = v.replace("\"", "\"\"");
        return c.contains(",") || c.contains("\n") ? "\"" + c + "\"" : c;
    }
}
