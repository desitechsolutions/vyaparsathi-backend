package com.desitech.vyaparsathi.customer.mapper;

import com.desitech.vyaparsathi.customer.dto.CustomerSegmentDto;
import com.desitech.vyaparsathi.customer.entity.CustomerSegment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CustomerSegmentMapper {

    CustomerSegment toEntity(CustomerSegmentDto dto);
    CustomerSegmentDto toDto(CustomerSegment entity);
    List<CustomerSegmentDto> toDtoList(List<CustomerSegment> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "shop", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(CustomerSegmentDto dto, @MappingTarget CustomerSegment entity);
}
