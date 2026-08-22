package com.desitech.vyaparsathi.payroll.mapper;

import com.desitech.vyaparsathi.payroll.dto.PayrollSlipDto;
import com.desitech.vyaparsathi.payroll.dto.PayrollSlipItemDto;
import com.desitech.vyaparsathi.payroll.entity.PayrollSlip;
import com.desitech.vyaparsathi.payroll.entity.PayrollSlipItem;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class PayrollSlipMapper {
    public PayrollSlipDto toDto(PayrollSlip entity) {
        if (entity == null) return null;

        PayrollSlipDto dto = new PayrollSlipDto();
        dto.setId(entity.getId());
        dto.setPayrollRunId(entity.getPayrollRun() != null ? entity.getPayrollRun().getId() : null);
        dto.setEmployeeId(entity.getEmployee() != null ? entity.getEmployee().getId() : null);
        // getEmployeeName() removed — construct from firstName + lastName
        if (entity.getEmployee() != null) {
            String name = entity.getEmployee().getFirstName();
            if (entity.getEmployee().getLastName() != null) name += " " + entity.getEmployee().getLastName();
            dto.setEmployeeName(name);
        }
        // getAttendanceSummary() removed from PayrollSlip — build from individual fields
        String attendanceSummary = "Present: " + entity.getPresentDays()
                + " | Paid Leaves: " + entity.getPaidLeaves()
                + " | LOP: " + entity.getLossOfPayDays();
        dto.setAttendanceSummary(attendanceSummary);
        dto.setGrossEarnings(entity.getGrossEarnings());
        dto.setTotalDeductions(entity.getTotalDeductions());
        dto.setNetSalary(entity.getNetSalary());
        // getStatus() removed — use payoutStatus field
        dto.setStatus(entity.getPayoutStatus());
        dto.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toLocalDate() : null);
        // getPaidAt() removed — use disbursedOn field
        dto.setPaidAt(entity.getDisbursedOn());

        if (entity.getItems() != null) {
            dto.setItems(entity.getItems().stream()
                    .map(this::itemToDto)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    public PayrollSlipItemDto itemToDto(PayrollSlipItem entity) {
        if (entity == null) return null;

        PayrollSlipItemDto dto = new PayrollSlipItemDto();
        dto.setId(entity.getId());
        dto.setPayrollSlipId(entity.getPayrollSlip() != null ? entity.getPayrollSlip().getId() : null);
        dto.setComponentCode(entity.getComponentCode());
        dto.setComponentName(entity.getComponentName());
        dto.setAmount(entity.getAmount());
        // getSequence() removed from PayrollSlipItem — set null/default
        dto.setSequence(null);
        return dto;
    }

    public PayrollSlip toEntity(PayrollSlipDto dto) {
        if (dto == null) return null;

        PayrollSlip entity = new PayrollSlip();
        // setAttendanceSummary() removed — no-op
        entity.setGrossEarnings(dto.getGrossEarnings());
        entity.setTotalDeductions(dto.getTotalDeductions());
        entity.setNetSalary(dto.getNetSalary());
        // setStatus() removed — use payoutStatus
        entity.setPayoutStatus(dto.getStatus());
        return entity;
    }

    public void updateEntityFromDto(PayrollSlipDto dto, PayrollSlip entity) {
        if (dto == null) return;
        if (dto.getGrossEarnings() != null) entity.setGrossEarnings(dto.getGrossEarnings());
        if (dto.getTotalDeductions() != null) entity.setTotalDeductions(dto.getTotalDeductions());
        if (dto.getNetSalary() != null) entity.setNetSalary(dto.getNetSalary());
    }
}
