package com.desitech.vyaparsathi.purchaseorder.mapper;

import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderDto;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderItemDto;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PurchaseOrderMapper {
    @Mapping(source = "supplier.id", target = "supplierId")
    PurchaseOrderDto toDto(PurchaseOrder purchaseOrder);
    @Mapping(source = "itemVariant.id", target = "itemVariantId")
    @Mapping(source = "itemVariant.sku", target = "sku")
    @Mapping(source = "itemVariant.item.name", target = "name")
    PurchaseOrderItemDto toDto(PurchaseOrderItem purchaseOrderItem);
    @Mapping(target = "supplier", ignore = true)
    PurchaseOrder toEntity(PurchaseOrderDto dto);
    @Mapping(target = "itemVariant", ignore = true)
    @Mapping(target = "purchaseOrder", ignore = true)
    PurchaseOrderItem toEntity(PurchaseOrderItemDto dto);
    List<PurchaseOrderDto> toDtoList(List<PurchaseOrder> purchaseOrders);
}
