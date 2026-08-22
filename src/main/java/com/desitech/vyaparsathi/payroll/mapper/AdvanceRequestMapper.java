package com.desitech.vyaparsathi.payroll.mapper;

import com.desitech.vyaparsathi.payroll.dto.AdvanceRequestDto;
import com.desitech.vyaparsathi.payroll.entity.AdvanceRequest;
import org.springframework.stereotype.Component;

@Component
public class AdvanceRequestMapper {
    public AdvanceRequestDto toDto(AdvanceRequest entity) {
        if (entity == null) return null;
        AdvanceRequestDto dto = new AdvanceRequestDto();
        dto.setId(entity.getId());
        dto.setAmount(entity.getAmount());
        dto.setReason(entity.getReason());
        dto.setStatus(entity.getStatus());
        dto.setRequestedAt(entity.getRequestedAt());
        dto.setApprovedAt(entity.getApprovedAt());
        dto.setDisbursedAt(entity.getDisbursedAt());
        return dto;
    }

    public AdvanceRequest toEntity(AdvanceRequestDto dto) {
        if (dto == null) return null;
        AdvanceRequest entity = new AdvanceRequest();
        entity.setAmount(dto.getAmount());
        entity.setReason(dto.getReason());
        entity.setStatus(dto.getStatus());
        return entity;
    }
}
