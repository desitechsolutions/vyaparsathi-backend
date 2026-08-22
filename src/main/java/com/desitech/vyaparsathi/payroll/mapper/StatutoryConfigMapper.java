package com.desitech.vyaparsathi.payroll.mapper;

import com.desitech.vyaparsathi.payroll.dto.StatutoryConfigDto;
import com.desitech.vyaparsathi.payroll.entity.StatutoryConfig;
import org.springframework.stereotype.Component;

@Component
public class StatutoryConfigMapper {
    public StatutoryConfigDto toDto(StatutoryConfig entity) {
        if (entity == null) return null;
        StatutoryConfigDto dto = new StatutoryConfigDto();
        dto.setId(entity.getId());
        dto.setPfUan(entity.getPfUan());
        dto.setEsicCode(entity.getEsicCode());
        dto.setPtState(entity.getPtState());
        dto.setTaxRegime(entity.getTaxRegime());
        dto.setBankName(entity.getBankName());
        dto.setBankAccountNumber(entity.getBankAccountNumber());
        dto.setBankIfsc(entity.getBankIfsc());
        dto.setBankBranch(entity.getBankBranch());
        return dto;
    }

    public StatutoryConfig toEntity(StatutoryConfigDto dto) {
        if (dto == null) return null;
        StatutoryConfig entity = new StatutoryConfig();
        entity.setPfUan(dto.getPfUan());
        entity.setEsicCode(dto.getEsicCode());
        entity.setPtState(dto.getPtState());
        entity.setTaxRegime(dto.getTaxRegime());
        entity.setBankName(dto.getBankName());
        entity.setBankAccountNumber(dto.getBankAccountNumber());
        entity.setBankIfsc(dto.getBankIfsc());
        entity.setBankBranch(dto.getBankBranch());
        return entity;
    }
}
