package com.desitech.vyaparsathi.customer.service;

import com.desitech.vyaparsathi.common.exception.CustomerNotFoundException;
import com.desitech.vyaparsathi.common.exception.LedgerNotFoundException;
import com.desitech.vyaparsathi.customer.dto.CustomerLedgerDto;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.entity.CustomerLedger;
import com.desitech.vyaparsathi.customer.entity.CustomerLedgerType;
import com.desitech.vyaparsathi.customer.mapper.CustomerLedgerMapper;
import com.desitech.vyaparsathi.customer.repository.CustomerLedgerRepository;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomerLedgerService {
    @Autowired
    private CustomerLedgerRepository ledgerRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private CustomerLedgerMapper mapper;

    @Transactional
    public CustomerLedgerDto addEntry(Long customerId, CustomerLedgerDto dto) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));

        if (dto.getType() == CustomerLedgerType.CREDIT) {
            customer.setCreditBalance(customer.getCreditBalance().add(dto.getAmount()));
        } else { // DEBIT
            customer.setCreditBalance(customer.getCreditBalance().subtract(dto.getAmount()));
        }
        customerRepository.save(customer);

        CustomerLedger ledger = mapper.toEntity(dto);
        ledger.setCustomer(customer);

        return mapper.toDto(ledgerRepository.save(ledger));
    }

    @Transactional
    public CustomerLedgerDto updateEntry(Long ledgerId, CustomerLedgerDto dto) {
        CustomerLedger existingEntry = ledgerRepository.findById(ledgerId)
                .orElseThrow(() -> new LedgerNotFoundException("Ledger entry not found"));

        Customer customer = existingEntry.getCustomer();

        // Reverse the effect of the old entry
        BigDecimal oldAmount = existingEntry.getAmount();
        if (existingEntry.getType() == CustomerLedgerType.CREDIT) {
            customer.setCreditBalance(customer.getCreditBalance().subtract(oldAmount));
        } else {
            customer.setCreditBalance(customer.getCreditBalance().add(oldAmount));
        }

        // Apply the effect of the new entry
        BigDecimal newAmount = dto.getAmount();
        CustomerLedgerType newType = dto.getType();
        if (newType == CustomerLedgerType.CREDIT) {
            customer.setCreditBalance(customer.getCreditBalance().add(newAmount));
        } else {
            customer.setCreditBalance(customer.getCreditBalance().subtract(newAmount));
        }

        // Update ledger entry and save customer
        existingEntry.setAmount(newAmount);
        existingEntry.setType(newType);
        existingEntry.setDescription(dto.getDescription());

        customerRepository.save(customer);
        return mapper.toDto(ledgerRepository.save(existingEntry));
    }

    @Transactional
    public void deleteEntry(Long ledgerId) {
        CustomerLedger existingEntry = ledgerRepository.findById(ledgerId)
                .orElseThrow(() -> new LedgerNotFoundException("Ledger entry not found"));

        Customer customer = existingEntry.getCustomer();
        BigDecimal oldAmount = existingEntry.getAmount();

        // Reverse the effect of the entry
        if (existingEntry.getType() == CustomerLedgerType.CREDIT) {
            customer.setCreditBalance(customer.getCreditBalance().subtract(oldAmount));
        } else {
            customer.setCreditBalance(customer.getCreditBalance().add(oldAmount));
        }
        customerRepository.save(customer);
        ledgerRepository.deleteById(ledgerId);
    }


    public List<CustomerLedgerDto> getLedger(Long customerId, LocalDateTime startDate, LocalDateTime endDate) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));

        List<CustomerLedger> ledgerEntries = ledgerRepository.findByCustomerAndDateRange(customer, startDate, endDate);
        return ledgerEntries.stream().map(mapper::toDto).collect(Collectors.toList());
    }
}