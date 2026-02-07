package com.desitech.vyaparsathi.delivery.mapper;

import com.desitech.vyaparsathi.delivery.dto.DeliveryPersonDTO;
import com.desitech.vyaparsathi.delivery.entity.DeliveryPerson;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DeliveryPersonMapper {
    DeliveryPersonDTO toDto(DeliveryPerson entity);
    DeliveryPerson toEntity(DeliveryPersonDTO dto);
}