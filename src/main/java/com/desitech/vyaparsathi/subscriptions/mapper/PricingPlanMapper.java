package com.desitech.vyaparsathi.subscriptions.mapper;

import com.desitech.vyaparsathi.subscriptions.dto.PricingPlanDTO;
import com.desitech.vyaparsathi.subscriptions.entity.PricingPlanConfig;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PricingPlanMapper {

    PricingPlanDTO toDto(PricingPlanConfig entity);

    List<PricingPlanDTO> toDtoList(List<PricingPlanConfig> entities);

    PricingPlanConfig toEntity(PricingPlanDTO dto);
    void updateEntityFromDto(PricingPlanDTO dto, @MappingTarget PricingPlanConfig entity);
}