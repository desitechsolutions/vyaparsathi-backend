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
    @Mapping(target = "items",              source = "saleItems")
    @Mapping(target = "customer",           source = "customer")
    // Issue 2: Bill-level discount/charges fields
    @Mapping(target = "invoiceDiscount",    source = "invoiceDiscount")
    @Mapping(target = "shippingCharges",    source = "shippingCharges")
    @Mapping(target = "otherCharges",       source = "otherCharges")
    // Issue 5: E-Invoice & E-Way Bill fields
    @Mapping(target = "irn",                source = "irn")
    @Mapping(target = "ackNo",              source = "ackNo")
    @Mapping(target = "ackDate",            source = "ackDate")
    @Mapping(target = "qrCodePath",         source = "qrCodePath")
    @Mapping(target = "einvoiceStatus",     source = "einvoiceStatus")
    @Mapping(target = "ewayBillNo",         source = "ewayBillNo")
    @Mapping(target = "ewayBillDate",       source = "ewayBillDate")
    @Mapping(target = "ewayBillValidUntil", source = "ewayBillValidUntil")
    @Mapping(target = "vehicleNumber",      source = "vehicleNumber")
    @Mapping(target = "transporterId",      source = "transporterId")
    @Mapping(target = "transporterName",    source = "transporterName")
    SaleDto toDto(Sale sale);

    // -------- DTO → ENTITY --------
    @Mapping(target = "saleItems", source = "items")
    @Mapping(target = "customer", ignore = true)
    Sale toEntity(SaleDto saleDto);

    List<SaleDto> toDtoList(List<Sale> sales);
}