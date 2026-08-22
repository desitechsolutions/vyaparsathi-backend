package com.desitech.vyaparsathi.payroll.mapper;

import com.desitech.vyaparsathi.payroll.dto.PayrollRunDto;
import com.desitech.vyaparsathi.payroll.entity.PayrollRun;
import org.springframework.stereotype.Component;

@Component
public class PayrollRunMapper {
    public PayrollRunDto toDto(PayrollRun entity) {
        if (entity == null) return null;

        PayrollRunDto dto = new PayrollRunDto();
        dto.setId(entity.getId());
        // payrollMonth is now a String (e.g. "01"), payrollYear is Integer
        // PayrollRunDto still has int month/year fields — parse String month safely
        try {
            dto.setMonth(entity.getPayrollMonth() != null ? Integer.parseInt(entity.getPayrollMonth()) : 0);
        } catch (NumberFormatException e) {
            dto.setMonth(0);
        }
        dto.setYear(entity.getPayrollYear() != null ? entity.getPayrollYear() : 0);
        dto.setStatus(entity.getStatus());
        dto.setTotalGrossEarnings(entity.getTotalGrossEarnings());
        dto.setTotalNetPayable(entity.getTotalNetPayable());
        dto.setTotalEmployerContributions(entity.getTotalEmployerContributions());
        // processingStartedAt field removed from PayrollRun — skip
        dto.setProcessingStartedAt(null);
        // approvedAt and disbursedAt are LocalDateTime in entity but LocalDate in DTO — convert
        dto.setApprovedAt(entity.getApprovedAt() != null ? entity.getApprovedAt().toLocalDate() : null);
        dto.setApprovedByUserId(entity.getApprovedByUserId());
        dto.setDisbursedAt(entity.getDisbursedAt() != null ? entity.getDisbursedAt().toLocalDate() : null);
        // disbursedByUserId field removed — skip
        dto.setDisbursedByUserId(null);
        dto.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toLocalDate() : null);
        return dto;
    }

    public PayrollRun toEntity(PayrollRunDto dto) {
        if (dto == null) return null;

        PayrollRun entity = new PayrollRun();
        entity.setPayrollMonth(dto.getMonth() != 0 ? String.format("%02d", dto.getMonth()) : null);
        entity.setPayrollYear(dto.getYear() != 0 ? dto.getYear() : null);
        entity.setStatus(dto.getStatus());
        entity.setTotalGrossEarnings(dto.getTotalGrossEarnings());
        entity.setTotalNetPayable(dto.getTotalNetPayable());
        entity.setTotalEmployerContributions(dto.getTotalEmployerContributions());
        return entity;
    }

    public void updateEntityFromDto(PayrollRunDto dto, PayrollRun entity) {
        if (dto == null) return;
        if (dto.getTotalGrossEarnings() != null) entity.setTotalGrossEarnings(dto.getTotalGrossEarnings());
        if (dto.getTotalNetPayable() != null) entity.setTotalNetPayable(dto.getTotalNetPayable());
        if (dto.getTotalEmployerContributions() != null) entity.setTotalEmployerContributions(dto.getTotalEmployerContributions());
    }
}
