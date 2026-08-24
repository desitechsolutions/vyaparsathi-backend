package com.desitech.vyaparsathi.payroll.scheduler;

import com.desitech.vyaparsathi.payroll.entity.*;
import com.desitech.vyaparsathi.payroll.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Enterprise Leave Carry-Forward Scheduler.
 *
 * Runs automatically on Jan 1 each year (cron: 0 0 1 1 *) to:
 * 1. Carry forward eligible leave balances into the new year (subject to max carry-forward days)
 * 2. Reset non-carryable leave types to their fresh annual entitlement
 * 3. Log all adjustments with audit trail
 *
 * Field mapping (from LeaveBalance entity):
 *   openingBalance = carried forward from previous year
 *   allocated      = fresh annual entitlement
 *   used           = days taken this year
 *   carriedForward = days carried forward from this year to next (computed at year end)
 *   closingBalance = openingBalance + allocated - used
 */
@Component
public class LeaveCarryForwardScheduler {

    private static final Logger log = LoggerFactory.getLogger(LeaveCarryForwardScheduler.class);

    @Autowired private LeaveBalanceRepository leaveBalanceRepository;
    @Autowired private LeaveTypeRepository leaveTypeRepository;
    @Autowired private EmployeeRepository employeeRepository;

    /**
     * Scheduled: runs every January 1st at midnight.
     * Can also be triggered manually from the admin API.
     */
    @Scheduled(cron = "0 0 0 1 1 ?") // Jan 1 at midnight (00:00:00)
    @Transactional
    public void runAnnualCarryForward() {
        int newYear = LocalDate.now().getYear();
        int previousYear = newYear - 1;
        log.info("Starting annual leave carry-forward from {} to {}", previousYear, newYear);

        // Process all previous-year balances
        List<LeaveBalance> previousYearBalances = leaveBalanceRepository.findAll().stream()
                .filter(b -> b.getYear() == previousYear)
                .toList();

        int totalProcessed = 0;
        int totalCarriedForward = 0;
        int totalReset = 0;

        for (LeaveBalance prevBalance : previousYearBalances) {
            LeaveType leaveType = prevBalance.getLeaveType();
            Employee employee = prevBalance.getEmployee();

            if (employee == null || !Boolean.TRUE.equals(employee.getIsActive())) continue;
            if (!Boolean.TRUE.equals(leaveType.getIsActive())) continue;

            // Compute how many days remain unused at year end
            BigDecimal unused = prevBalance.getClosingBalance() != null
                    ? prevBalance.getClosingBalance()
                    : nvl(prevBalance.getAllocated()).add(nvl(prevBalance.getOpeningBalance()))
                            .subtract(nvl(prevBalance.getUsed())).max(BigDecimal.ZERO);

            // Compute carry-forward amount — capped at max carry-forward days for type
            BigDecimal carryForwardAmount = BigDecimal.ZERO;
            if (leaveType.getCarryForwardDays() != null && leaveType.getCarryForwardDays() > 0) {
                BigDecimal maxCf = BigDecimal.valueOf(leaveType.getCarryForwardDays());
                carryForwardAmount = unused.min(maxCf);
            }

            // Update previous year record with final carry-forward amount
            prevBalance.setCarriedForward(carryForwardAmount);
            leaveBalanceRepository.save(prevBalance);

            // Fresh entitlement for new year
            BigDecimal freshEntitlement = leaveType.getMaxDaysPerYear() != null
                    ? BigDecimal.valueOf(leaveType.getMaxDaysPerYear()) : BigDecimal.ZERO;

            // Create or update new-year balance
            LeaveBalance newBalance = leaveBalanceRepository
                    .findByEmployeeIdAndLeaveTypeIdAndYear(employee.getId(), leaveType.getId(), newYear)
                    .orElse(createFreshBalance(employee, leaveType, newYear));

            newBalance.setOpeningBalance(carryForwardAmount);
            newBalance.setAllocated(freshEntitlement);
            newBalance.setUsed(BigDecimal.ZERO);
            newBalance.setCarriedForward(BigDecimal.ZERO);
            newBalance.setClosingBalance(carryForwardAmount.add(freshEntitlement));
            newBalance.setYear(newYear);

            leaveBalanceRepository.save(newBalance);
            totalProcessed++;

            if (carryForwardAmount.compareTo(BigDecimal.ZERO) > 0) {
                totalCarriedForward++;
                log.debug("Carry-forward: employee={} leaveType={} days={}", employee.getId(), leaveType.getName(), carryForwardAmount);
            } else {
                totalReset++;
            }
        }

        log.info("Leave carry-forward complete for {}: {} processed, {} carried forward, {} reset",
                newYear, totalProcessed, totalCarriedForward, totalReset);
    }

    /**
     * Manual trigger for admin — carry forward for a specific shop and year transition.
     *
     * @param shopId   Shop to process
     * @param fromYear Year to carry forward from
     */
    @Transactional
    public void runCarryForwardForShop(Long shopId, int fromYear) {
        int toYear = fromYear + 1;
        log.info("Running manual leave carry-forward for shop {} from {} to {}", shopId, fromYear, toYear);

        List<LeaveBalance> previousBalances = leaveBalanceRepository.findByShopIdAndYear(shopId, fromYear);
        List<LeaveType> leaveTypes = leaveTypeRepository.findAll();

        for (LeaveBalance prevBalance : previousBalances) {
            Employee employee = prevBalance.getEmployee();
            LeaveType leaveType = prevBalance.getLeaveType();

            if (employee == null || !Boolean.TRUE.equals(employee.getIsActive())) continue;
            if (!Boolean.TRUE.equals(leaveType.getIsActive())) continue;

            BigDecimal unused = prevBalance.getClosingBalance() != null
                    ? prevBalance.getClosingBalance()
                    : nvl(prevBalance.getAllocated()).add(nvl(prevBalance.getOpeningBalance()))
                            .subtract(nvl(prevBalance.getUsed())).max(BigDecimal.ZERO);

            BigDecimal carryForwardAmount = leaveType.getCarryForwardDays() != null && leaveType.getCarryForwardDays() > 0
                    ? unused.min(BigDecimal.valueOf(leaveType.getCarryForwardDays()))
                    : BigDecimal.ZERO;

            prevBalance.setCarriedForward(carryForwardAmount);
            leaveBalanceRepository.save(prevBalance);

            BigDecimal freshEntitlement = leaveType.getMaxDaysPerYear() != null
                    ? BigDecimal.valueOf(leaveType.getMaxDaysPerYear()) : BigDecimal.ZERO;

            LeaveBalance newBalance = leaveBalanceRepository
                    .findByEmployeeIdAndLeaveTypeIdAndYear(employee.getId(), leaveType.getId(), toYear)
                    .orElse(createFreshBalance(employee, leaveType, toYear));

            newBalance.setOpeningBalance(carryForwardAmount);
            newBalance.setAllocated(freshEntitlement);
            newBalance.setUsed(BigDecimal.ZERO);
            newBalance.setCarriedForward(BigDecimal.ZERO);
            newBalance.setClosingBalance(carryForwardAmount.add(freshEntitlement));
            leaveBalanceRepository.save(newBalance);
        }
    }

    private LeaveBalance createFreshBalance(Employee employee, LeaveType leaveType, int year) {
        LeaveBalance balance = new LeaveBalance();
        balance.setEmployee(employee);
        balance.setLeaveType(leaveType);
        balance.setYear(year);
        balance.setOpeningBalance(BigDecimal.ZERO);
        balance.setAllocated(BigDecimal.ZERO);
        balance.setUsed(BigDecimal.ZERO);
        balance.setCarriedForward(BigDecimal.ZERO);
        balance.setClosingBalance(BigDecimal.ZERO);
        return balance;
    }

    private BigDecimal nvl(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
}
