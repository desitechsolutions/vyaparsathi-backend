package com.desitech.vyaparsathi.delivery.mapper;

import com.desitech.vyaparsathi.delivery.dto.DeliveryStatusHistoryDTO;
import com.desitech.vyaparsathi.delivery.entity.Delivery;
import com.desitech.vyaparsathi.delivery.entity.DeliveryStatusHistory;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface DeliveryStatusHistoryMapper {

    @Mapping(source = "delivery.id", target = "deliveryId")
    DeliveryStatusHistoryDTO toDto(DeliveryStatusHistory entity);

    @Mapping(target = "delivery", ignore = true) // ignore mapping here
    DeliveryStatusHistory toEntity(DeliveryStatusHistoryDTO dto);

    @AfterMapping
    default void setDelivery(@MappingTarget DeliveryStatusHistory entity, DeliveryStatusHistoryDTO dto) {
        if (dto.getDeliveryId() != null) {
            Delivery delivery = new Delivery();
            delivery.setId(dto.getDeliveryId());
            entity.setDelivery(delivery);
        }
    }
}
