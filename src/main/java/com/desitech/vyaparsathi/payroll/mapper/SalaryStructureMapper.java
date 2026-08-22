package com.desitech.vyaparsathi.payroll.mapper;

import com.desitech.vyaparsathi.payroll.dto.SalaryComponentDto;
import com.desitech.vyaparsathi.payroll.dto.SalaryStructureDto;
import com.desitech.vyaparsathi.payroll.entity.SalaryComponent;
import com.desitech.vyaparsathi.payroll.entity.SalaryStructure;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class SalaryStructureMapper {

    private final SalaryComponentMapper componentMapper;

    public SalaryStructureMapper(SalaryComponentMapper componentMapper) {
        this.componentMapper = componentMapper;
    }

    public SalaryStructureDto toDto(SalaryStructure entity) {
        if (entity == null) {
            return null;
        }

        return SalaryStructureDto.builder()
                .id(entity.getId())
                .structureName(entity.getStructureName())
                .structureCode(entity.getStructureCode())
                .description(entity.getDescription())
                .isActive(entity.getIsActive())
                .effectiveFrom(entity.getEffectiveFrom())
                .components(entity.getComponents() != null ?
                        entity.getComponents().stream()
                                .map(componentMapper::toDto)
                                .collect(Collectors.toList())
                        : null)
                .build();
    }

    public SalaryStructure toEntity(SalaryStructureDto dto) {
        if (dto == null) {
            return null;
        }

        SalaryStructure entity = new SalaryStructure();
        entity.setId(dto.getId());
        entity.setStructureName(dto.getStructureName());
        entity.setStructureCode(dto.getStructureCode());
        entity.setDescription(dto.getDescription());
        entity.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        entity.setEffectiveFrom(dto.getEffectiveFrom());

        if (dto.getComponents() != null) {
            entity.setComponents(
                    dto.getComponents().stream()
                            .map(c -> {
                                SalaryComponent comp = componentMapper.toEntity(c);
                                comp.setStructure(entity);
                                return comp;
                            })
                            .collect(Collectors.toList())
            );
        }

        return entity;
    }

    public void updateEntityFromDto(SalaryStructureDto dto, SalaryStructure entity) {
        if (dto == null || entity == null) {
            return;
        }

        entity.setStructureName(dto.getStructureName());
        entity.setStructureCode(dto.getStructureCode());
        entity.setDescription(dto.getDescription());
        entity.setIsActive(dto.getIsActive());
        entity.setEffectiveFrom(dto.getEffectiveFrom());
    }
}
