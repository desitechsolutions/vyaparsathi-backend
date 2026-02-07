package com.desitech.vyaparsathi.delivery.mapper;

import com.desitech.vyaparsathi.delivery.dto.DeliveryDTO;
import com.desitech.vyaparsathi.delivery.entity.Delivery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring", uses = {DeliveryPersonMapper.class, DeliveryStatusHistoryMapper.class})
public interface DeliveryMapper {
    @Mapping(source = "deliveryPerson", target = "deliveryPerson")
    @Mapping(source = "statusHistory", target = "statusHistory")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    DeliveryDTO toDto(Delivery entity);

    @Mapping(source = "deliveryPerson", target = "deliveryPerson")
    @Mapping(source = "statusHistory", target = "statusHistory")
    Delivery toEntity(DeliveryDTO dto);
}