package com.desitech.vyaparsathi.customer.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.customer.dto.CustomerDto;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.mapper.CustomerMapper;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CustomerService {
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private CustomerMapper mapper;
    @Autowired
    private ShopRepository shopRepository;

    @Transactional
    public CustomerDto addCustomer(CustomerDto dto) {
        Customer customer = mapper.toEntity(dto);
        if (customer.getCreditBalance() == null) {
            customer.setCreditBalance(BigDecimal.ZERO);
        }
        customer.setShop(getCurrentShop());

        customer = customerRepository.save(customer);
        return mapper.toDto(customer);
    }

    @Transactional
    public CustomerDto updateCustomer(Long id, CustomerDto dto) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
        mapper.updateEntityFromDto(dto, customer);
        customer = customerRepository.save(customer);
        return mapper.toDto(customer);
    }

    /**
     * FIX: Added @Transactional(readOnly = true) to ensure this read operation
     * runs in the same Hibernate Session where the 'shopFilter' was enabled.
     */
    @Transactional(readOnly = true)
    public List<CustomerDto> listCustomers() {
        return customerRepository.findAll().stream()
                .map(mapper::toDto)
                .toList();
    }

    /**
     * FIX: Added @Transactional(readOnly = true).
     */
    @Transactional(readOnly = true)
    public CustomerDto getCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
        return mapper.toDto(customer);
    }

    @Transactional
    public void deleteCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
        customerRepository.delete(customer);
    }

    /**
     * FIX: Added @Transactional(readOnly = true).
     */
    @Transactional(readOnly = true)
    public List<CustomerDto> searchCustomers(String name) {
        // The findAll() call here will now correctly be filtered by the shopId.
        return customerRepository.findAll().stream()
                .filter(c -> c.getName() != null && c.getName().toLowerCase().contains(name.toLowerCase()))
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * FIX: Added @Transactional(readOnly = true).
     */
    @Transactional(readOnly = true)
    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
    }

    /**
     * FIX: Added @Transactional(readOnly = true).
     */
    @Transactional(readOnly = true)
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    /**
     * FIX: Added @Transactional as this is a write operation (saving).
     */
    @Transactional
    public Customer createCustomer(Customer customer) {
        customer.setShop(getCurrentShop());
        if (customer.getCreditBalance() == null) {
            customer.setCreditBalance(BigDecimal.ZERO);
        }
        return customerRepository.save(customer);
    }

    private Shop getCurrentShop() {
        Long shopId = TenantContext.getCurrentShopId();
        return shopRepository.findById(shopId)
                .orElseThrow(() -> new EntityNotFoundException("Shop not found"));
    }
}
