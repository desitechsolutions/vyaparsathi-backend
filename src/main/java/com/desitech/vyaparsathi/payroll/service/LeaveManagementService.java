package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.payroll.dto.LeaveApplicationDto;
import com.desitech.vyaparsathi.payroll.dto.LeaveBalanceDto;
import com.desitech.vyaparsathi.payroll.dto.LeaveTypeDto;
import com.desitech.vyaparsathi.payroll.entity.*;
import com.desitech.vyaparsathi.payroll.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LeaveManagementService {

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private LeaveApplicationRepository leaveApplicationRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Transactional
    public LeaveTypeDto createLeaveType(LeaveTypeDto dto) {
        Long shopId = TenantContext.getCurrentShopId();

        LeaveType leaveType = new LeaveType();
        leaveType.setName(dto.getName());
        leaveType.setMaxDaysPerYear(dto.getMaxDaysPerYear());
        leaveType.setCarryForwardDays(dto.getCarryForwardDays());
        leaveType.setIsProrated(dto.getIsProrated());
        leaveType.setIsActive(dto.getIsActive());

        // Set shop from context
        Shop shop = new Shop();
        shop.setId(shopId);
        leaveType.setShop(shop);

        leaveTypeRepository.save(leaveType);
        dto.setId(leaveType.getId());
        return dto;
    }

    public List<LeaveTypeDto> listLeaveTypes() {
        Long shopId = TenantContext.getCurrentShopId();
        return leaveTypeRepository.findByShopIdAndIsActiveTrue(shopId).stream()
                .map(lt -> LeaveTypeDto.builder()
                        .id(lt.getId())
                        .name(lt.getName())
                        .maxDaysPerYear(lt.getMaxDaysPerYear())
                        .carryForwardDays(lt.getCarryForwardDays())
                        .isProrated(lt.getIsProrated())
                        .isActive(lt.getIsActive())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void allocateLeaveBalance(Long employeeId, Integer year) {
        Long shopId = TenantContext.getCurrentShopId();
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundAppException("Employee", employeeId));

        List<LeaveType> leaveTypes = leaveTypeRepository.findByShopIdAndIsActiveTrue(shopId);

        for (LeaveType leaveType : leaveTypes) {
            LeaveBalance balance = new LeaveBalance();
            balance.setEmployee(employee);
            balance.setLeaveType(leaveType);
            balance.setYear(year);
            balance.setOpeningBalance(BigDecimal.ZERO);
            balance.setAllocated(BigDecimal.valueOf(leaveType.getMaxDaysPerYear()));
            balance.setUsed(BigDecimal.ZERO);
            balance.setCarriedForward(BigDecimal.ZERO);
            balance.setClosingBalance(BigDecimal.valueOf(leaveType.getMaxDaysPerYear()));
            leaveBalanceRepository.save(balance);
        }
    }

    @Transactional
    public LeaveApplicationDto applyLeave(Long employeeId, LocalDate fromDate, LocalDate toDate,
                                          Long leaveTypeId, String reason) {
        Long shopId = TenantContext.getCurrentShopId();
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundAppException("Employee", employeeId));

        LeaveType leaveType = leaveTypeRepository.findById(leaveTypeId)
                .orElseThrow(() -> new EntityNotFoundAppException("LeaveType", leaveTypeId));

        long days = java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1;

        LeaveApplication application = new LeaveApplication();
        application.setEmployee(employee);
        application.setLeaveType(leaveType);
        application.setFromDate(fromDate);
        application.setToDate(toDate);
        application.setDays(BigDecimal.valueOf(days));
        application.setReason(reason);
        application.setStatus("PENDING");

        leaveApplicationRepository.save(application);

        return LeaveApplicationDto.builder()
                .id(application.getId())
                .employeeId(employeeId)
                .leaveTypeId(leaveTypeId)
                .fromDate(fromDate)
                .toDate(toDate)
                .days(BigDecimal.valueOf(days))
                .reason(reason)
                .status("PENDING")
                .build();
    }

    @Transactional
    public LeaveApplicationDto approveLeaveApplication(Long applicationId, Long approverId) {
        LeaveApplication application = leaveApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new EntityNotFoundAppException("LeaveApplication", applicationId));

        // approverId is userId; for now just set approval without looking up full Employee
        // TODO: Implement Employee.findByUserId mapping if needed
        Long shopId = TenantContext.getCurrentShopId();

        application.setStatus("APPROVED");
        application.setApprovedAt(java.time.LocalDateTime.now());
        leaveApplicationRepository.save(application);

        // Deduct from leave balance
        Integer year = application.getFromDate().getYear();
        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeIdAndYear(application.getEmployee().getId(),
                        application.getLeaveType().getId(), year)
                .orElseThrow(() -> new EntityNotFoundAppException("LeaveBalance", null));

        balance.setUsed(balance.getUsed().add(application.getDays()));
        balance.setClosingBalance(balance.getAllocated().subtract(balance.getUsed()));
        leaveBalanceRepository.save(balance);

        return toDto(application);
    }

    @Transactional
    public LeaveApplicationDto rejectLeaveApplication(Long applicationId, String reason) {
        LeaveApplication application = leaveApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new EntityNotFoundAppException("LeaveApplication", applicationId));

        application.setStatus("REJECTED");
        application.setApprovalComments(reason);
        leaveApplicationRepository.save(application);

        return toDto(application);
    }

    public LeaveBalanceDto getLeaveBalance(Long employeeId, Long leaveTypeId) {
        Integer currentYear = java.time.LocalDate.now().getYear();

        if (leaveTypeId != null) {
            // Get balance for specific leave type
            LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, leaveTypeId, currentYear)
                    .orElse(null);

            if (balance == null) {
                // Return zero balance if not found
                return LeaveBalanceDto.builder()
                        .employeeId(employeeId)
                        .leaveTypeId(leaveTypeId)
                        .year(currentYear)
                        .allocated(BigDecimal.ZERO)
                        .used(BigDecimal.ZERO)
                        .closingBalance(BigDecimal.ZERO)
                        .build();
            }

            return LeaveBalanceDto.builder()
                    .id(balance.getId())
                    .employeeId(employeeId)
                    .leaveTypeId(balance.getLeaveType().getId())
                    .leaveTypeName(balance.getLeaveType().getName())
                    .year(currentYear)
                    .openingBalance(balance.getOpeningBalance())
                    .allocated(balance.getAllocated())
                    .used(balance.getUsed())
                    .carriedForward(balance.getCarriedForward())
                    .closingBalance(balance.getClosingBalance())
                    .build();
        } else {
            // Return aggregated balance for all leave types
            List<LeaveBalance> allBalances = leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, currentYear);
            BigDecimal totalAllocated = allBalances.stream()
                    .map(LeaveBalance::getAllocated)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalUsed = allBalances.stream()
                    .map(LeaveBalance::getUsed)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            return LeaveBalanceDto.builder()
                    .employeeId(employeeId)
                    .year(currentYear)
                    .allocated(totalAllocated)
                    .used(totalUsed)
                    .closingBalance(totalAllocated.subtract(totalUsed))
                    .build();
        }
    }

    public LeaveBalanceDto getEmployeeLeaveBalance(Long employeeId, Integer year) {
        LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, 1L, year)
                .orElseThrow(() -> new EntityNotFoundAppException("LeaveBalance", null));

        return LeaveBalanceDto.builder()
                .id(balance.getId())
                .employeeId(employeeId)
                .leaveTypeId(balance.getLeaveType().getId())
                .leaveTypeName(balance.getLeaveType().getName())
                .year(year)
                .openingBalance(balance.getOpeningBalance())
                .allocated(balance.getAllocated())
                .used(balance.getUsed())
                .carriedForward(balance.getCarriedForward())
                .closingBalance(balance.getClosingBalance())
                .build();
    }

    public List<LeaveApplicationDto> listLeaveApplicationsByStatus(String status) {
        Long shopId = TenantContext.getCurrentShopId();
        return leaveApplicationRepository.findByShopIdAndStatusOrderByIdDesc(shopId, status)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private LeaveApplicationDto toDto(LeaveApplication application) {
        return LeaveApplicationDto.builder()
                .id(application.getId())
                .employeeId(application.getEmployee().getId())
                .leaveTypeId(application.getLeaveType().getId())
                .fromDate(application.getFromDate())
                .toDate(application.getToDate())
                .days(application.getDays())
                .reason(application.getReason())
                .status(application.getStatus())
                .approverId(application.getApprover() != null ? application.getApprover().getId() : null)
                .approvalComments(application.getApprovalComments())
                .build();
    }
}
