package com.desitech.vyaparsathi.purchaseorder.mapper;

import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderDto;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderItemDto;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
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

    /**
     * The entity keeps state and state-code as separate V99 columns for
     * statutory reporting, but the FE / DTO uses the flat "code-name"
     * string. Combine them here so a GET /purchase-orders/{id} sends the
     * FE-friendly shape without a manual pass in every service caller.
     * Address snapshots are also flattened from *_party_snapshot columns.
     */
    @AfterMapping
    default void flattenStatutory(PurchaseOrder src, @MappingTarget PurchaseOrderDto tgt) {
        if (src.getPlaceOfSupplyStateCode() != null && src.getPlaceOfSupplyState() != null) {
            tgt.setPlaceOfSupply(src.getPlaceOfSupplyStateCode() + "-" + src.getPlaceOfSupplyState());
        } else if (src.getPlaceOfSupplyState() != null) {
            tgt.setPlaceOfSupply(src.getPlaceOfSupplyState());
        }
        tgt.setBillToAddress(src.getBillToPartySnapshot());
        tgt.setShipToAddress(src.getShipToPartySnapshot());
    }
}
