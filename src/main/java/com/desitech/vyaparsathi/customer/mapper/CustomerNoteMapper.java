package com.desitech.vyaparsathi.customer.mapper;

import com.desitech.vyaparsathi.customer.dto.CustomerNoteDto;
import com.desitech.vyaparsathi.customer.entity.CustomerNote;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CustomerNoteMapper {

    CustomerNote toEntity(CustomerNoteDto dto);
    CustomerNoteDto toDto(CustomerNote entity);
    List<CustomerNoteDto> toDtoList(List<CustomerNote> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customerId", ignore = true)
    @Mapping(target = "authorUserId", ignore = true)
    @Mapping(target = "authorName", ignore = true)
    @Mapping(target = "shop", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(CustomerNoteDto dto, @MappingTarget CustomerNote entity);
}
