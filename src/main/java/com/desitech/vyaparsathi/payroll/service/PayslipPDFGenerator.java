package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.payroll.entity.*;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;

@Service
public class PayslipPDFGenerator {

    public byte[] generatePayslipPDF(PayrollSlip slip) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>");
        html.append("body { font-family: Arial; margin: 20px; } ");
        html.append(".header { text-align: center; margin-bottom: 20px; } ");
        html.append(".header h1 { margin: 0; } ");
        html.append(".header p { margin: 5px 0; color: #666; } ");
        html.append("table { width: 100%; border-collapse: collapse; margin: 20px 0; } ");
        html.append("th, td { border: 1px solid #ddd; padding: 10px; text-align: left; } ");
        html.append("th { background-color: #f0f0f0; } ");
        html.append(".amount-right { text-align: right; } ");
        html.append(".footer { margin-top: 20px; font-size: 11px; color: #999; } ");
        html.append("</style></head><body>");

        // Header
        html.append("<div class='header'>");
        html.append("<h1>PAYSLIP</h1>");
        html.append("<p>Month: ").append(slip.getPayrollRun().getPayrollMonth()).append("/").append(slip.getPayrollRun().getPayrollYear()).append("</p>");
        html.append("</div>");

        // Employee Info
        html.append("<table>");
        html.append("<tr><td><b>Employee Name:</b> ").append(slip.getEmployee().getFirstName()).append(" ").append(slip.getEmployee().getLastName() != null ? slip.getEmployee().getLastName() : "").append("</td>");
        html.append("<td><b>Employee ID:</b> ").append(slip.getEmployee().getId()).append("</td></tr>");
        html.append("<tr><td colspan='2'><b>Department:</b> ").append(slip.getEmployee().getDepartment()).append("</td></tr>");
        html.append("</table>");

        // Earnings
        html.append("<h3>Earnings</h3>");
        html.append("<table>");
        html.append("<tr><th>Component</th><th class='amount-right'>Amount (₹)</th></tr>");
        if (slip.getItems() != null) {
            for (PayrollSlipItem item : slip.getItems()) {
                if (!item.getComponentCode().contains("DEDUCTION") && !item.getComponentCode().contains("_ER")) {
                    html.append("<tr><td>").append(item.getComponentName()).append("</td>");
                    html.append("<td class='amount-right'>").append(String.format("%.2f", item.getAmount())).append("</td></tr>");
                }
            }
        }
        html.append("<tr><th>Total Earnings</th><th class='amount-right'>").append(String.format("%.2f", slip.getGrossEarnings())).append("</th></tr>");
        html.append("</table>");

        // Deductions
        html.append("<h3>Deductions</h3>");
        html.append("<table>");
        html.append("<tr><th>Component</th><th class='amount-right'>Amount (₹)</th></tr>");
        if (slip.getItems() != null) {
            for (PayrollSlipItem item : slip.getItems()) {
                if (item.getComponentCode().contains("DEDUCTION") || item.getComponentCode().contains("PT") || 
                    item.getComponentCode().contains("TDS") || item.getComponentCode().contains("_EE")) {
                    html.append("<tr><td>").append(item.getComponentName()).append("</td>");
                    html.append("<td class='amount-right'>").append(String.format("%.2f", item.getAmount())).append("</td></tr>");
                }
            }
        }
        html.append("<tr><th>Total Deductions</th><th class='amount-right'>").append(String.format("%.2f", slip.getTotalDeductions())).append("</th></tr>");
        html.append("</table>");

        // Net Salary
        html.append("<table style='border: 2px solid #333;'>");
        html.append("<tr><th style='background-color: #e0e0e0;'>NET SALARY PAYABLE</th>");
        html.append("<th style='background-color: #e0e0e0; text-align: right;'>₹").append(String.format("%.2f", slip.getNetSalary())).append("</th></tr>");
        html.append("</table>");

        // Attendance
        html.append("<h3>Attendance</h3>");
        String attendanceSummary = "Present: " + slip.getPresentDays() + " | Paid Leaves: " + slip.getPaidLeaves()
                + " | LOP Days: " + slip.getLossOfPayDays();
        html.append("<p>").append(attendanceSummary).append("</p>");

        // Footer
        html.append("<div class='footer'>");
        html.append("<p>Generated on: ").append(LocalDate.now()).append("</p>");
        html.append("<p>This is a computer-generated document and does not require a signature.</p>");
        html.append("</div>");

        html.append("</body></html>");

        // Mock PDF conversion (in real impl, use iText or Apache PDFBox)
        return html.toString().getBytes();
    }

    public byte[] generateForm16PDF(Form16Data form16) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>");
        html.append("body { font-family: Arial; margin: 20px; } ");
        html.append(".header { text-align: center; margin-bottom: 20px; border-bottom: 2px solid #333; padding-bottom: 10px; } ");
        html.append("table { width: 100%; border-collapse: collapse; margin: 15px 0; } ");
        html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; } ");
        html.append(".label { font-weight: bold; width: 40%; } ");
        html.append(".amount-right { text-align: right; } ");
        html.append("</style></head><body>");

        // Header
        html.append("<div class='header'><h1>FORM 16</h1>");
        html.append("<p>Certificate of Tax Deducted at Source (TDS)</p>");
        html.append("<p>Financial Year: ").append(form16.getFinancialYear()).append("</p>");
        html.append("</div>");

        // Employee Details
        html.append("<h3>Part A - Employee Details</h3>");
        html.append("<table>");
        html.append("<tr><td class='label'>Name:</td><td>").append(form16.getName()).append("</td></tr>");
        html.append("<tr><td class='label'>PAN:</td><td>").append(form16.getPanNumber()).append("</td></tr>");
        html.append("<tr><td class='label'>Address:</td><td>").append(form16.getAddress()).append("</td></tr>");
        html.append("</table>");

        // Employer Details
        html.append("<h3>Part B - Employer Details</h3>");
        html.append("<table>");
        html.append("<tr><td class='label'>Employer Name:</td><td>").append(form16.getEmployerName()).append("</td></tr>");
        html.append("<tr><td class='label'>Employer PAN:</td><td>").append(form16.getEmployerPan()).append("</td></tr>");
        html.append("</table>");

        // Salary & Deductions
        html.append("<h3>Income & Deductions</h3>");
        html.append("<table>");
        html.append("<tr><td class='label'>Salary Received:</td><td class='amount-right'>₹").append(String.format("%.2f", form16.getTotalSalary())).append("</td></tr>");
        html.append("<tr><td class='label'>Standard Deduction:</td><td class='amount-right'>₹").append(String.format("%.2f", form16.getStandardDeduction())).append("</td></tr>");
        html.append("<tr><td class='label'>PF Contribution:</td><td class='amount-right'>₹").append(String.format("%.2f", form16.getPfContribution())).append("</td></tr>");
        html.append("<tr><td class='label'>Professional Tax:</td><td class='amount-right'>₹").append(String.format("%.2f", form16.getProfessionalTax())).append("</td></tr>");
        html.append("</table>");

        // Tax Summary
        html.append("<h3>Tax Summary</h3>");
        html.append("<table>");
        html.append("<tr><th>Description</th><th class='amount-right'>Amount</th></tr>");
        html.append("<tr><td class='label'>Gross Total Income:</td><td class='amount-right'>₹").append(String.format("%.2f", form16.getGrossTotalIncome())).append("</td></tr>");
        html.append("<tr><td class='label'>Tax Payable:</td><td class='amount-right'>₹").append(String.format("%.2f", form16.getTdsPayable())).append("</td></tr>");
        html.append("<tr><td class='label'>Tax Already Paid (TDS):</td><td class='amount-right'>₹").append(String.format("%.2f", form16.getTaxPaidThisYear())).append("</td></tr>");
        html.append("</table>");

        html.append("</body></html>");

        return html.toString().getBytes();
    }
}
