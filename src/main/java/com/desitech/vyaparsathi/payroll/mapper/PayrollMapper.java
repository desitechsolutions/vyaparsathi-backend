package com.desitech.vyaparsathi.payroll.mapper;

import com.desitech.vyaparsathi.payroll.dto.PayrollRequestDto;
import com.desitech.vyaparsathi.payroll.dto.PayrollResponseDto;
import com.desitech.vyaparsathi.payroll.entity.PayrollRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PayrollMapper {

    // Mapping from Entity to Response DTO
    @Mapping(source = "staff.id", target = "staffId")
    @Mapping(source = "staff.name", target = "staffName")
    @Mapping(source = "staff.role", target = "staffRole")
    PayrollResponseDto toDto(PayrollRecord record);

    // Mapping from Request DTO (Input) to Entity
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "staff", ignore = true) // Will be set manually in Service by ID
    @Mapping(target = "status", constant = "PAID") // Default status for direct payment
    @Mapping(target = "baseSalaryAtTime", ignore = true) // Set from Staff entity in Service
    @Mapping(target = "netAmount", ignore = true) // Calculated in Service
    PayrollRecord toEntity(PayrollRequestDto dto);
}