package com.desitech.vyaparsathi.customer.mapper;

import com.desitech.vyaparsathi.customer.dto.CustomerContactDto;
import com.desitech.vyaparsathi.customer.entity.CustomerContact;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CustomerContactMapper {

    CustomerContact toEntity(CustomerContactDto dto);
    CustomerContactDto toDto(CustomerContact entity);
    List<CustomerContactDto> toDtoList(List<CustomerContact> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customerId", ignore = true)
    @Mapping(target = "shop", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(CustomerContactDto dto, @MappingTarget CustomerContact entity);
}
