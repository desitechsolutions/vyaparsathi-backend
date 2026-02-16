package com.desitech.vyaparsathi.payroll.mapper;

import com.desitech.vyaparsathi.payroll.dto.StaffDto;
import com.desitech.vyaparsathi.payroll.dto.StaffResponseDto;
import com.desitech.vyaparsathi.payroll.entity.Staff;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface StaffMapper {

    StaffDto toDto(Staff staff);

    @Mapping(target = "id", ignore = true) // Protect ID during creation
    @Mapping(target = "shop", ignore = true)
    @Mapping(target = "advanceBalance", ignore = true) // Managed by ledger, not forms
    Staff toEntity(StaffDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "shop", ignore = true)
    @Mapping(target = "advanceBalance", ignore = true)
    void updateEntityFromDto(StaffDto dto, @MappingTarget Staff staff);

    StaffResponseDto toResponseDto(Staff staff);
}