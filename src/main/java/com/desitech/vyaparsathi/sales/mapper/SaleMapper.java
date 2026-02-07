package com.desitech.vyaparsathi.sales.mapper;

import com.desitech.vyaparsathi.customer.mapper.CustomerMapper;
import com.desitech.vyaparsathi.delivery.mapper.DeliveryMapper;
import com.desitech.vyaparsathi.payment.mapper.PaymentMapper;
import com.desitech.vyaparsathi.sales.dto.SaleDto;
import com.desitech.vyaparsathi.sales.entity.Sale;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(
        componentModel = "spring",
        uses = {
                CustomerMapper.class,
                SaleItemMapper.class,
                PaymentMapper.class,
                DeliveryMapper.class
        }
)
public interface SaleMapper {

    // -------- ENTITY → DTO --------
    @Mapping(target = "items", source = "saleItems")
    @Mapping(target = "customer", source = "customer")
    SaleDto toDto(Sale sale);

    // -------- DTO → ENTITY --------
    @Mapping(target = "saleItems", source = "items")
    @Mapping(target = "customer", ignore = true)
    Sale toEntity(SaleDto saleDto);

    List<SaleDto> toDtoList(List<Sale> sales);
}