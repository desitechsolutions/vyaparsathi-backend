package com.desitech.vyaparsathi.shop.mapper;

import com.desitech.vyaparsathi.shop.dto.ShopDto;
import com.desitech.vyaparsathi.shop.entity.Shop;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ShopMapper {

    Shop toEntity(ShopDto dto);

    ShopDto toDto(Shop entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "code", ignore = true) // Shop code usually shouldn't change after onboarding
    @Mapping(target = "logoPath", ignore = true) // Handled by File Service
    @Mapping(target = "signaturePath", ignore = true) // Handled by File Service
    void updateShopFromDto(ShopDto dto, @MappingTarget Shop shop);
}