package com.desitech.vyaparsathi.shop.mapper;

import com.desitech.vyaparsathi.shop.dto.ShopDto;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.enums.IndustryType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ShopMapper {

    // API contract keeps industryType as a String on the DTO — a lenient
    // `fromString` on the enum absorbs case + unknown-value quirks so a
    // legacy row (or a mistyped payload) does not fail the whole mapping.
    @Mapping(target = "industryType", source = "industryType", qualifiedByName = "toIndustryType")
    Shop toEntity(ShopDto dto);

    @Mapping(target = "industryType", source = "industryType", qualifiedByName = "toIndustryString")
    ShopDto toDto(Shop entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "code", ignore = true) // Shop code usually shouldn't change after onboarding
    @Mapping(target = "logoPath", ignore = true) // Handled by File Service
    @Mapping(target = "signaturePath", ignore = true) // Handled by File Service
    @Mapping(target = "industryType", source = "industryType", qualifiedByName = "toIndustryType")
    void updateShopFromDto(ShopDto dto, @MappingTarget Shop shop);

    @Named("toIndustryType")
    default IndustryType toIndustryType(String value) {
        return IndustryType.fromString(value);
    }

    @Named("toIndustryString")
    default String toIndustryString(IndustryType value) {
        return value == null ? null : value.name();
    }
}
