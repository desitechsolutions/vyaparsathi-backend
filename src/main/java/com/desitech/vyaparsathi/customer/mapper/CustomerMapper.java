package com.desitech.vyaparsathi.customer.mapper;

import com.desitech.vyaparsathi.customer.dto.CustomerDto;
import com.desitech.vyaparsathi.customer.entity.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CustomerMapper {
    Customer toEntity(CustomerDto dto);
    CustomerDto toDto(Customer entity);
    List<CustomerDto> toDtoList(List<Customer> entities);

    @Mapping(target = "creditBalance", ignore = true)
    void updateEntityFromDto(CustomerDto dto, @org.mapstruct.MappingTarget Customer customer);
}
