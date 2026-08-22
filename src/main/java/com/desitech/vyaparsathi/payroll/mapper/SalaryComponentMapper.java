package com.desitech.vyaparsathi.payroll.mapper;

import com.desitech.vyaparsathi.payroll.dto.SalaryComponentDto;
import com.desitech.vyaparsathi.payroll.entity.SalaryComponent;
import org.springframework.stereotype.Component;

@Component
public class SalaryComponentMapper {

    public SalaryComponentDto toDto(SalaryComponent entity) {
        if (entity == null) {
            return null;
        }

        return SalaryComponentDto.builder()
                .id(entity.getId())
                .componentName(entity.getComponentName())
                .componentCode(entity.getComponentCode())
                .componentType(entity.getComponentType())
                .calculationType(entity.getCalculationType())
                .calculationValue(entity.getCalculationValue())
                .isTaxable(entity.getIsTaxable())
                .affectsPF(entity.getAffectsPF())
                .affectsESI(entity.getAffectsESI())
                .isStatutory(entity.getIsStatutory())
                .isActive(entity.getIsActive())
                .orderSequence(entity.getOrderSequence())
                .build();
    }

    public SalaryComponent toEntity(SalaryComponentDto dto) {
        if (dto == null) {
            return null;
        }

        SalaryComponent entity = new SalaryComponent();
        entity.setId(dto.getId());
        entity.setComponentName(dto.getComponentName());
        entity.setComponentCode(dto.getComponentCode());
        entity.setComponentType(dto.getComponentType());
        entity.setCalculationType(dto.getCalculationType());
        entity.setCalculationValue(dto.getCalculationValue());
        entity.setIsTaxable(dto.getIsTaxable() != null ? dto.getIsTaxable() : true);
        entity.setAffectsPF(dto.getAffectsPF() != null ? dto.getAffectsPF() : true);
        entity.setAffectsESI(dto.getAffectsESI() != null ? dto.getAffectsESI() : true);
        entity.setIsStatutory(dto.getIsStatutory() != null ? dto.getIsStatutory() : false);
        entity.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        entity.setOrderSequence(dto.getOrderSequence() != null ? dto.getOrderSequence() : 0);
        return entity;
    }
}
