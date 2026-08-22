package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.payroll.dto.PayrollCalculationResultDto;
import com.desitech.vyaparsathi.payroll.entity.*;
import com.desitech.vyaparsathi.payroll.enums.CalculationType;
import com.desitech.vyaparsathi.payroll.enums.ComponentType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Component
public class PayrollCalculationEngine {

    /**
     * Main orchestrator for payroll calculation
     */
    public PayrollCalculationResultDto calculatePayrollForEmployee(
            Employee employee,
            PayrollRun payrollRun,
            SalaryStructure salaryStructure,
            List<AttendanceRecord> attendanceRecords) {

        // 1. Determine prorated days & LOP
        AttendanceSummary attendance = calculateAttendanceSummary(
                payrollRun.getStartDate(),
                payrollRun.getEndDate(),
                payrollRun.getCalendarDays(),
                attendanceRecords
        );

        // 2. Calculate earnings
        List<ComponentAmount> earnings = calculateEarnings(
                employee,
                salaryStructure,
                attendance,
                payrollRun
        );

        // 3. Calculate deductions
        List<ComponentAmount> deductions = calculateDeductions(
                employee,
                salaryStructure,
                earnings,
                attendance,
                payrollRun
        );

        // 4. Build result
        BigDecimal totalEarnings = earnings.stream()
                .map(c -> c.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDeductions = deductions.stream()
                .map(c -> c.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netSalary = totalEarnings.subtract(totalDeductions);

        PayrollCalculationResultDto result = new PayrollCalculationResultDto();
        result.setEmployeeId(employee.getId());
        result.setEmployeeName(employee.getFirstName() + " " + (employee.getLastName() != null ? employee.getLastName() : ""));
        result.setTotalDays(payrollRun.getCalendarDays());
        result.setWorkingDays(attendance.workingDays);
        result.setPresentDays(new BigDecimal(attendance.presentDays));
        result.setPaidLeaves(new BigDecimal(attendance.paidLeaves));
        result.setLossOfPayDays(new BigDecimal(attendance.lopDays));
        result.setMonthlyBaseSalary(employee.getMonthlyCTC());
        result.setGrossEarnings(totalEarnings);
        result.setTotalDeductions(totalDeductions);
        result.setNetSalary(netSalary);
        result.setEarnings(earnings);
        result.setDeductions(deductions);

        return result;
    }

    private AttendanceSummary calculateAttendanceSummary(
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            int calendarDays,
            List<AttendanceRecord> records) {

        AttendanceSummary summary = new AttendanceSummary();
        summary.calendarDays = calendarDays;

        // Count by attendance type
        int present = 0, absent = 0, halfDay = 0, paidLeaves = 0, unpaidLeaves = 0;

        for (AttendanceRecord record : records) {
            switch (record.getAttendanceType()) {
                case PRESENT -> present++;
                case ABSENT -> absent++;
                case HALF_DAY -> halfDay += 1;
                case PAID_LEAVE -> paidLeaves++;
                case UNPAID_LEAVE -> unpaidLeaves++;
                case HOLIDAY, WEEKEND -> {} // ignored
            }
        }

        summary.presentDays = present + (halfDay * 0.5);
        summary.paidLeaves = paidLeaves;
        summary.lopDays = unpaidLeaves;
        summary.workingDays = calendarDays - 8; // Approx: excluding weekends
        summary.absentDays = absent;

        return summary;
    }

    private List<ComponentAmount> calculateEarnings(
            Employee employee,
            SalaryStructure structure,
            AttendanceSummary attendance,
            PayrollRun payrollRun) {

        List<ComponentAmount> earnings = new ArrayList<>();
        BigDecimal baseSalary = employee.getMonthlyCTC();

        for (SalaryComponent component : structure.getComponents()) {
            if (component.getComponentType() != ComponentType.EARNING) continue;

            BigDecimal amount = calculateComponentAmount(
                    component,
                    baseSalary,
                    attendance
            );

            earnings.add(new ComponentAmount(
                    component.getComponentName(),
                    component.getComponentCode(),
                    ComponentType.EARNING,
                    amount
            ));
        }

        return earnings;
    }

    private List<ComponentAmount> calculateDeductions(
            Employee employee,
            SalaryStructure structure,
            List<ComponentAmount> earnings,
            AttendanceSummary attendance,
            PayrollRun payrollRun) {

        List<ComponentAmount> deductions = new ArrayList<>();
        BigDecimal baseSalary = employee.getMonthlyCTC();
        BigDecimal totalEarnings = earnings.stream()
                .map(c -> c.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (SalaryComponent component : structure.getComponents()) {
            if (component.getComponentType() != ComponentType.DEDUCTION) continue;

            BigDecimal amount = calculateComponentAmount(
                    component,
                    baseSalary,
                    totalEarnings,
                    attendance
            );

            deductions.add(new ComponentAmount(
                    component.getComponentName(),
                    component.getComponentCode(),
                    ComponentType.DEDUCTION,
                    amount
            ));
        }

        return deductions;
    }

    private BigDecimal calculateComponentAmount(
            SalaryComponent component,
            BigDecimal baseSalary,
            AttendanceSummary attendance) {

        return switch (component.getCalculationType()) {
            case FLAT_AMOUNT -> component.getCalculationValue();
            case PERCENTAGE_OF_BASIC -> baseSalary
                    .multiply(component.getCalculationValue())
                    .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
            case PERCENTAGE_OF_GROSS -> baseSalary
                    .multiply(component.getCalculationValue())
                    .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
            case FORMULA -> BigDecimal.ZERO; // Phase 3: custom formula engine
        };
    }

    private BigDecimal calculateComponentAmount(
            SalaryComponent component,
            BigDecimal baseSalary,
            BigDecimal grossEarnings,
            AttendanceSummary attendance) {

        // Apply LOP if this is a deduction affected by attendance
        BigDecimal value = calculateComponentAmount(component, baseSalary, attendance);

        // Prorate if applicable
        if (attendance.lopDays > 0 && !component.getIsStatutory()) {
            BigDecimal lopFactor = new BigDecimal(attendance.workingDays - attendance.lopDays)
                    .divide(new BigDecimal(attendance.workingDays), 4, RoundingMode.HALF_UP);
            value = value.multiply(lopFactor);
        }

        return value;
    }

    /**
     * Internal class to hold attendance summary
     */
    public static class AttendanceSummary {
        public int calendarDays;
        public int workingDays;
        public double presentDays;
        public double paidLeaves;
        public double lopDays;
        public int absentDays;
    }

    /**
     * Internal class to represent component calculation result
     */
    public static class ComponentAmount {
        public String componentName;
        public String componentCode;
        public ComponentType type;
        public BigDecimal amount;

        public ComponentAmount(String name, String code, ComponentType type, BigDecimal amount) {
            this.componentName = name;
            this.componentCode = code;
            this.type = type;
            this.amount = amount;
        }
    }
}
