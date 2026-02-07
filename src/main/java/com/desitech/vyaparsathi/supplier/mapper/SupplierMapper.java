package com.desitech.vyaparsathi.supplier.mapper;

import com.desitech.vyaparsathi.supplier.dto.SupplierDto;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SupplierMapper {
    SupplierDto toDto(Supplier supplier);
    Supplier toEntity(SupplierDto dto);
    List<SupplierDto> toDtoList(List<Supplier> suppliers);
}
