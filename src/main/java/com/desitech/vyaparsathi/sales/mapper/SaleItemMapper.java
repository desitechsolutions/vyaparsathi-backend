package com.desitech.vyaparsathi.sales.mapper;

import com.desitech.vyaparsathi.sales.dto.SaleItemDto;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SaleItemMapper {

    @Mapping(target = "itemVariantId", source = "itemVariant.id")
    @Mapping(target = "itemName", source = "itemVariant.item.name")
    @Mapping(target = "itemId", source = "itemVariant.item.id") // if you track parent item
    @Mapping(target = "gstRate", source = "itemVariant.gstRate") // if present in ItemVariant
    @Mapping(target = "costPerUnit", source = "itemVariant.pricePerUnit") // optional
    SaleItemDto toDto(SaleItem saleItem);

    @Mapping(target = "itemVariant.id", source = "itemVariantId")
    @Mapping(target = "sale", ignore = true)   // VERY IMPORTANT to avoid recursion
    @Mapping(target = "cgstAmt", ignore = true) // usually calculated in service
    @Mapping(target = "sgstAmt", ignore = true)
    @Mapping(target = "igstAmt", ignore = true)
    @Mapping(target = "taxableValue", ignore = true) // calculated, not taken from UI
    SaleItem toEntity(SaleItemDto dto);
}

