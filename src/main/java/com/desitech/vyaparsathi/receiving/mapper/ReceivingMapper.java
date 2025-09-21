package com.desitech.vyaparsathi.receiving.mapper;

import com.desitech.vyaparsathi.receiving.dto.ReceivingDto;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.dto.ReceivingItemDto;
import com.desitech.vyaparsathi.receiving.entity.ReceivingItem;
import com.desitech.vyaparsathi.inventory.dto.SupplierDto;
import com.desitech.vyaparsathi.inventory.entity.Supplier;
import com.desitech.vyaparsathi.inventory.mapper.SupplierMapper;
import org.mapstruct.*;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {SupplierMapper.class})
public interface ReceivingMapper {

    // Receiving -> ReceivingDto
    @Mapping(target = "supplier", source = "purchaseOrder.supplier")
    @Mapping(target = "receivingItems", source = "items", qualifiedByName = "receivingItemsToDtos")  // Key fix: Qualify to use element mapper
    @Mapping(target = "purchaseOrderId", source = "purchaseOrder.id")
    @Mapping(target = "poNumber", source = "purchaseOrder.poNumber")
    @Mapping(target = "shopId", source = "shop.id")
    ReceivingDto toDto(Receiving entity);

    List<ReceivingDto> toDtoList(List<Receiving> entities);

    // ReceivingDto -> Receiving (reverse mapping)
    @Mapping(target = "items", source = "receivingItems", qualifiedByName = "dtosToReceivingItems")  // Symmetric for reverse
    @Mapping(target = "purchaseOrder.id", source = "purchaseOrderId")
    @Mapping(target = "shop.id", source = "shopId")
    Receiving toEntity(ReceivingDto dto);

    // Renamed for clarity; maps single ReceivingItem to DTO
    @Named("toItemDto")  // Qualifier name for element mapping
    @Mapping(target = "purchaseOrderItemId", source = "purchaseOrderItem.id")
    @Mapping(target = "expectedQty", source = "purchaseOrderItem.quantity")  // Nested mapping from PO item
    ReceivingItemDto toItemDto(ReceivingItem entity);  // Renamed from toDto

    // Explicit collection mapper for ReceivingItem -> ReceivingItemDto (uses toItemDto)
    @Named("receivingItemsToDtos")
    default List<ReceivingItemDto> receivingItemsToDtos(List<ReceivingItem> items) {
        if (items == null) {
            return null;
        }
        return items.stream()
                .map(this::toItemDto)
                .collect(Collectors.toList());
    }

    // Reverse: Single ReceivingItemDto -> ReceivingItem
    @Named("toItemEntity")
    @Mapping(target = "purchaseOrderItem.id", source = "purchaseOrderItemId")
    // Note: expectedQty maps back to purchaseOrderItem.quantity if needed; add more if required
    ReceivingItem toItemEntity(ReceivingItemDto dto);

    // Explicit collection mapper for reverse (uses toItemEntity)
    @Named("dtosToReceivingItems")
    default List<ReceivingItem> dtosToReceivingItems(List<ReceivingItemDto> dtos) {
        if (dtos == null) {
            return null;
        }
        return dtos.stream()
                .map(this::toItemEntity)
                .collect(Collectors.toList());
    }
}