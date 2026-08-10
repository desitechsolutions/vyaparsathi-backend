package com.desitech.vyaparsathi.sales.mapper;

import com.desitech.vyaparsathi.sales.dto.SaleItemDto;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface SaleItemMapper {

    @Mapping(target = "itemVariantId",    source = "itemVariant.id")
    @Mapping(target = "itemName",         source = "itemVariant.item.name")
    @Mapping(target = "itemId",           source = "itemVariant.item.id")
    @Mapping(target = "gstRate",          source = "itemVariant.gstRate")
    @Mapping(target = "costPerUnit",      source = "itemVariant.pricePerUnit")
    @Mapping(target = "batchNumber",      source = "batchNumber")
    @Mapping(target = "expiryDate",       source = "expiryDate")
    // Issue 1: Map variant attributes for frontend display and invoice rendering
    @Mapping(target = "variantSku",       source = "itemVariant.sku")
    @Mapping(target = "variantColor",     source = "itemVariant.color")
    @Mapping(target = "variantSize",      source = "itemVariant.size")
    @Mapping(target = "variantDesign",    source = "itemVariant.design")
    @Mapping(target = "variantBrand",     source = "itemVariant.item.brandName")
    SaleItemDto toDto(SaleItem saleItem);

    @Mapping(target = "itemVariant.id",   source = "itemVariantId")
    @Mapping(target = "sale",             ignore = true)   // Prevent recursion
    @Mapping(target = "cgstAmt",          ignore = true)   // Calculated in service
    @Mapping(target = "sgstAmt",          ignore = true)
    @Mapping(target = "igstAmt",          ignore = true)
    @Mapping(target = "taxableValue",     ignore = true)   // Calculated, not from UI
    SaleItem toEntity(SaleItemDto dto);
}
