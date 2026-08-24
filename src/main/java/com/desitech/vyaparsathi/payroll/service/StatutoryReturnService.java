package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.payroll.entity.*;
import com.desitech.vyaparsathi.payroll.repository.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Enterprise statutory return file generator.
 *
 * Generates compliance-ready output files:
 * 1. ESIC Monthly Return (Excel XLSX) — for filing with ESIC portal
 * 2. 24Q TDS Quarterly Return (CSV) — TRACES-compatible format for TDS filing
 * 3. LWF State Return (CSV) — Labour Welfare Fund state-wise return stub
 */
@Service
public class StatutoryReturnService {

    private static final Logger log = LoggerFactory.getLogger(StatutoryReturnService.class);

    @Autowired private PayrollRunRepository payrollRunRepository;
    @Autowired private PayrollSlipRepository payrollSlipRepository;
    @Autowired private StatutoryConfigRepository statutoryConfigRepository;
    @Autowired private EmployeeRepository employeeRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // ESIC Monthly Return — Excel (as per ESIC Form 6)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generate ESIC monthly return in Excel format.
     * Compliant with ESIC Employer portal Form 6 upload specification.
     *
     * @param runId  Payroll run ID
     * @return Excel file bytes (.xlsx)
     */
    @Transactional(readOnly = true)
    public byte[] generateESICMonthlyReturn(Long runId) {
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> new RuntimeException("PayrollRun not found: " + runId));
        Long shopId = run.getShop() != null ? run.getShop().getId() : null;
        StatutoryConfig config = statutoryConfigRepository.findByShopId(shopId).orElse(null);
        String esicCode = config != null ? config.getEsicCode() : "ESIC-CODE-NOT-SET";

        List<PayrollSlip> slips = payrollSlipRepository
                .findByPayrollRunId(runId, Pageable.unpaged()).getContent();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("ESIC Monthly Return");

            // ── Styles ───────────────────────────────────────────────────────
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle numStyle = workbook.createCellStyle();
            numStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            // ── Cover info ────────────────────────────────────────────────────
            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("ESIC MONTHLY RETURN");
            titleRow.getCell(0).setCellStyle(headerStyle);

            Row esicRow = sheet.createRow(1);
            esicRow.createCell(0).setCellValue("Establishment Code: " + esicCode);
            esicRow.createCell(3).setCellValue("Period: " + run.getPayrollMonth() + "/" + run.getPayrollYear());

            Row blankRow = sheet.createRow(2);

            // ── Column headers (ESIC Form 6 spec) ────────────────────────────
            String[] columns = {
                    "Sr No", "Employee Name", "ESIC Insurance Number", "IP Number",
                    "Gross Wages", "ESI Employee Contribution (0.75%)", "ESI Employer Contribution (3.25%)",
                    "Total ESI Contribution", "Days Worked", "Reason if Absent"
            };
            Row headerRow = sheet.createRow(3);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            // ── Data rows ─────────────────────────────────────────────────────
            int rowNum = 4;
            int srNo = 1;
            BigDecimal totalGross = BigDecimal.ZERO;
            BigDecimal totalEsiEE = BigDecimal.ZERO;
            BigDecimal totalEsiER = BigDecimal.ZERO;

            for (PayrollSlip slip : slips) {
                Employee emp = slip.getEmployee();
                if (!Boolean.TRUE.equals(emp.getEsicEnrolled())) continue;

                BigDecimal esiEE = nvl(slip.getEsiEmployee());
                // Employer ESI = 3.25% (EE is 0.75%, so ER = EE * 3.25/0.75)
                BigDecimal esiER = esiEE.compareTo(BigDecimal.ZERO) > 0
                        ? esiEE.multiply(new BigDecimal("3.25")).divide(new BigDecimal("0.75"), 2, RoundingMode.HALF_UP)
                        : nvl(slip.getEsiEmployer());

                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(srNo++);
                row.createCell(1).setCellValue(emp.getFirstName() + " " + (emp.getLastName() != null ? emp.getLastName() : ""));
                row.createCell(2).setCellValue(emp.getEsicNumber() != null ? emp.getEsicNumber() : "");
                row.createCell(3).setCellValue(emp.getEsicNumber() != null ? emp.getEsicNumber() : "");

                Cell grossCell = row.createCell(4);
                grossCell.setCellValue(slip.getGrossEarnings().doubleValue());
                grossCell.setCellStyle(numStyle);

                Cell eeCell = row.createCell(5);
                eeCell.setCellValue(esiEE.doubleValue());
                eeCell.setCellStyle(numStyle);

                Cell erCell = row.createCell(6);
                erCell.setCellValue(esiER.doubleValue());
                erCell.setCellStyle(numStyle);

                Cell totalCell = row.createCell(7);
                totalCell.setCellValue(esiEE.add(esiER).doubleValue());
                totalCell.setCellStyle(numStyle);

                row.createCell(8).setCellValue(nvl(slip.getPresentDays()).intValue());
                row.createCell(9).setCellValue("");

                totalGross = totalGross.add(slip.getGrossEarnings());
                totalEsiEE = totalEsiEE.add(esiEE);
                totalEsiER = totalEsiER.add(esiER);
            }

            // ── Summary row ───────────────────────────────────────────────────
            Row summaryRow = sheet.createRow(rowNum + 1);
            summaryRow.createCell(0).setCellValue("TOTAL");
            summaryRow.getCell(0).setCellStyle(headerStyle);
            Cell totGross = summaryRow.createCell(4);
            totGross.setCellValue(totalGross.doubleValue());
            totGross.setCellStyle(numStyle);
            Cell totEE = summaryRow.createCell(5);
            totEE.setCellValue(totalEsiEE.doubleValue());
            totEE.setCellStyle(numStyle);
            Cell totER = summaryRow.createCell(6);
            totER.setCellValue(totalEsiER.doubleValue());
            totER.setCellStyle(numStyle);
            Cell totTotal = summaryRow.createCell(7);
            totTotal.setCellValue(totalEsiEE.add(totalEsiER).doubleValue());
            totTotal.setCellStyle(numStyle);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate ESIC return for run {}", runId, e);
            throw new RuntimeException("ESIC monthly return generation failed", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 24Q TDS Quarterly Return — TRACES CSV Format
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generate 24Q TDS quarterly return in TRACES-compliant CSV format.
     * Used to file TDS return with Income Tax Department via TRACES portal.
     *
     * @param shopId         Shop/Employer ID
     * @param financialYear  Financial year (e.g., "2024-25")
     * @param quarter        Quarter (1 = Apr-Jun, 2 = Jul-Sep, 3 = Oct-Dec, 4 = Jan-Mar)
     * @return CSV bytes
     */
    @Transactional(readOnly = true)
    public byte[] generate24QTDSReturn(Long shopId, String financialYear, int quarter) {
        StatutoryConfig config = statutoryConfigRepository.findByShopId(shopId).orElse(null);

        // Determine months in quarter
        int[] months = getQuarterMonths(quarter);
        int yearPart1 = Integer.parseInt(financialYear.split("-")[0]);

        List<Employee> employees = employeeRepository.findByShopId(shopId);

        StringBuilder csv = new StringBuilder();

        // ── TRACES 24Q Batch Header (Record 1) ─────────────────────────────
        csv.append("Batch Header\n");
        csv.append("\"Transaction Type\",\"24Q\"\n");
        csv.append("\"Deductor TAN\",\"").append(config != null && config.getPfUan() != null ? config.getPfUan() : "TAN-NOT-SET").append("\"\n");
        csv.append("\"Financial Year\",\"").append(financialYear).append("\"\n");
        csv.append("\"Quarter\",\"Q").append(quarter).append("\"\n");
        csv.append("\"Date of Preparation\",\"").append(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append("\"\n");
        csv.append("\n");

        // ── Deductee-wise Records ────────────────────────────────────────────
        csv.append("\"Sr No\",\"Deductee PAN\",\"Deductee Name\",\"Total Salary Paid\",\"TDS Deducted\"," +
                "\"TDS Deposited\",\"Challan No\",\"TDS Certificate Number\",\"Remarks\"\n");

        int srNo = 1;
        BigDecimal totalSalaryPaid = BigDecimal.ZERO;
        BigDecimal totalTdsDeducted = BigDecimal.ZERO;

        for (Employee emp : employees) {
            // Aggregate salary + TDS from all payroll slips for this employee in this quarter
            BigDecimal empSalary = BigDecimal.ZERO;
            BigDecimal empTds = BigDecimal.ZERO;

            for (int m : months) {
                int year = (m < 4) ? yearPart1 + 1 : yearPart1; // Apr-Mar FY
                List<PayrollSlip> slips = payrollSlipRepository
                        .findByEmployeeIdAndPayrollYearAndPayrollMonth(emp.getId(), year, m);
                for (PayrollSlip slip : slips) {
                    empSalary = empSalary.add(nvl(slip.getGrossEarnings()));
                    empTds = empTds.add(nvl(slip.getTdsTax()));
                }
            }

            if (empSalary.compareTo(BigDecimal.ZERO) == 0) continue; // Skip employees with no payroll this quarter

            csv.append("\"").append(srNo++).append("\",");
            csv.append("\"").append(emp.getPanNumber() != null ? emp.getPanNumber() : "NOPAN").append("\",");
            csv.append("\"").append(emp.getFirstName()).append(" ").append(emp.getLastName() != null ? emp.getLastName() : "").append("\",");
            csv.append("\"").append(empSalary.setScale(2, RoundingMode.HALF_UP)).append("\",");
            csv.append("\"").append(empTds.setScale(2, RoundingMode.HALF_UP)).append("\",");
            csv.append("\"").append(empTds.setScale(2, RoundingMode.HALF_UP)).append("\","); // Deposited = Deducted
            csv.append("\"\","); // Challan No (to be filled manually)
            csv.append("\"\","); // TDS Certificate No
            csv.append("\"\"\n");

            totalSalaryPaid = totalSalaryPaid.add(empSalary);
            totalTdsDeducted = totalTdsDeducted.add(empTds);
        }

        // ── Summary ────────────────────────────────────────────────────────
        csv.append("\n\"TOTAL\",\"\",\"\",\"").append(totalSalaryPaid.setScale(2, RoundingMode.HALF_UP))
                .append("\",\"").append(totalTdsDeducted.setScale(2, RoundingMode.HALF_UP))
                .append("\",\"").append(totalTdsDeducted.setScale(2, RoundingMode.HALF_UP)).append("\",\"\",\"\",\"\"\n");

        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LWF State Return — CSV stub
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generate LWF (Labour Welfare Fund) state return.
     * Stub implementation — format varies by state (MH, KA, WB supported).
     *
     * @param shopId  Shop ID
     * @param month   Month number (1-12)
     * @param year    Year
     * @param state   State code (MH/KA/WB)
     * @return CSV bytes
     */
    @Transactional(readOnly = true)
    public byte[] generateLWFReturn(Long shopId, int month, int year, String state) {
        List<Employee> employees = employeeRepository.findByShopId(shopId);

        StringBuilder csv = new StringBuilder();
        csv.append("LWF State Return — ").append(state).append(" — ").append(month).append("/").append(year).append("\n");
        csv.append("\"Sr No\",\"Employee Name\",\"Designation\",\"Gross Salary\",\"LWF Employee\",\"LWF Employer\"\n");

        // LWF rates by state (annual, collected bi-annually or monthly)
        BigDecimal lwfEmployee = getLwfEmployeeContribution(state);
        BigDecimal lwfEmployer = getLwfEmployerContribution(state);

        int srNo = 1;
        for (Employee emp : employees) {
            if (!emp.getPtState().equals(state)) continue;
            csv.append("\"").append(srNo++).append("\",");
            csv.append("\"").append(emp.getFirstName()).append(" ").append(emp.getLastName() != null ? emp.getLastName() : "").append("\",");
            csv.append("\"").append(emp.getDesignation() != null ? emp.getDesignation() : "—").append("\",");
            csv.append("\"").append(emp.getMonthlyCTC() != null ? emp.getMonthlyCTC().setScale(2, RoundingMode.HALF_UP) : "0.00").append("\",");
            csv.append("\"").append(lwfEmployee).append("\",");
            csv.append("\"").append(lwfEmployer).append("\"\n");
        }

        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private int[] getQuarterMonths(int quarter) {
        return switch (quarter) {
            case 1 -> new int[]{4, 5, 6};   // Q1: Apr-Jun
            case 2 -> new int[]{7, 8, 9};   // Q2: Jul-Sep
            case 3 -> new int[]{10, 11, 12}; // Q3: Oct-Dec
            case 4 -> new int[]{1, 2, 3};   // Q4: Jan-Mar
            default -> throw new IllegalArgumentException("Quarter must be 1-4, got: " + quarter);
        };
    }

    private BigDecimal getLwfEmployeeContribution(String state) {
        return switch (state.toUpperCase()) {
            case "MH" -> new BigDecimal("12"); // Maharashtra: ₹12/month
            case "KA" -> new BigDecimal("20"); // Karnataka: ₹20/month
            case "WB" -> new BigDecimal("6");  // West Bengal: ₹6/month
            default -> new BigDecimal("10");   // Default: ₹10
        };
    }

    private BigDecimal getLwfEmployerContribution(String state) {
        return switch (state.toUpperCase()) {
            case "MH" -> new BigDecimal("36"); // Maharashtra: ₹36/month (3x employee)
            case "KA" -> new BigDecimal("40"); // Karnataka: ₹40/month (2x employee)
            case "WB" -> new BigDecimal("18"); // West Bengal: ₹18/month (3x employee)
            default -> new BigDecimal("20");   // Default: ₹20
        };
    }

    private BigDecimal nvl(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
}
