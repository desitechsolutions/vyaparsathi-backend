package com.desitech.vyaparsathi.purchasereturn.mapper;

import com.desitech.vyaparsathi.purchasereturn.dto.PurchaseReturnDto;
import com.desitech.vyaparsathi.purchasereturn.dto.PurchaseReturnItemDto;
import com.desitech.vyaparsathi.purchasereturn.entity.PurchaseReturn;
import com.desitech.vyaparsathi.purchasereturn.entity.PurchaseReturnItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

import com.desitech.vyaparsathi.supplier.mapper.SupplierMapper;

@Mapper(componentModel = "spring", uses = {SupplierMapper.class})
public interface PurchaseReturnMapper {

    @Mapping(target = "supplierId", source = "supplier.id")
    @Mapping(target = "supplierName", source = "supplier.name")
    @Mapping(target = "purchaseOrderId", source = "purchaseOrder.id")
    @Mapping(target = "poNumber", source = "purchaseOrder.poNumber")
    @Mapping(target = "receivingId", source = "receiving.id")
    PurchaseReturnDto toDto(PurchaseReturn entity);

    List<PurchaseReturnDto> toDtoList(List<PurchaseReturn> entities);

    @Mapping(target = "itemVariantId", source = "itemVariant.id")
    @Mapping(target = "itemVariantName", source = "itemVariant.item.name")
    @Mapping(target = "sku", source = "itemVariant.sku")
    PurchaseReturnItemDto toItemDto(PurchaseReturnItem entity);

    List<PurchaseReturnItemDto> toItemDtoList(List<PurchaseReturnItem> entities);
}
