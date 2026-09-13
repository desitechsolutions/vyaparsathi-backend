package com.desitech.vyaparsathi.supplier.service;

import com.desitech.vyaparsathi.supplier.dto.SupplierDto;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import com.desitech.vyaparsathi.supplier.mapper.SupplierMapper;
import com.desitech.vyaparsathi.supplier.repository.SupplierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SupplierService {

    private static final Pattern GSTIN_PATTERN =
            Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$");

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private SupplierMapper mapper;

    private void validateGstin(String gstin) {
        if (gstin != null && !gstin.isBlank()
                && !GSTIN_PATTERN.matcher(gstin.trim().toUpperCase()).matches()) {
            throw new IllegalArgumentException("Invalid GSTIN format: " + gstin);
        }
    }

    public SupplierDto createSupplier(SupplierDto dto) {
        validateGstin(dto.getGstin());
        Supplier supplier = mapper.toEntity(dto);
        supplierRepository.save(supplier);
        return mapper.toDto(supplier);
    }

    public List<SupplierDto> findAllSuppliers() {
        // Avoid N+1 by fetching all suppliers in one query (no relations to fetch here)
        return supplierRepository.findAll().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    public SupplierDto findSupplierById(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found"));
        return mapper.toDto(supplier);
    }

    public SupplierDto updateSupplier(Long id, SupplierDto dto) {
        validateGstin(dto.getGstin());
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found"));
        // Basic contact + identity
        supplier.setName(dto.getName());
        supplier.setContactPerson(dto.getContactPerson());
        supplier.setPhone(dto.getPhone());
        supplier.setEmail(dto.getEmail());
        supplier.setAddress(dto.getAddress());
        supplier.setGstin(dto.getGstin());
        if (dto.getStateCode() != null) supplier.setStateCode(dto.getStateCode());
        // V99 statutory identity — nullable, apply only when provided
        if (dto.getLegalName() != null) supplier.setLegalName(dto.getLegalName());
        if (dto.getTradeName() != null) supplier.setTradeName(dto.getTradeName());
        if (dto.getPan()       != null) supplier.setPan(dto.getPan());
        if (dto.getState()     != null) supplier.setState(dto.getState());
        if (dto.getCity()      != null) supplier.setCity(dto.getCity());
        if (dto.getPincode()   != null) supplier.setPincode(dto.getPincode());
        if (dto.getCountry()   != null) supplier.setCountry(dto.getCountry());
        // V103 lifecycle + terms
        if (dto.getActive()        != null) supplier.setActive(dto.getActive());
        if (dto.getCreditDays()    != null) supplier.setCreditDays(dto.getCreditDays());
        if (dto.getCreditLimit()   != null) supplier.setCreditLimit(dto.getCreditLimit());
        if (dto.getPaymentTerms()  != null) supplier.setPaymentTerms(dto.getPaymentTerms());
        if (dto.getNotes()         != null) supplier.setNotes(dto.getNotes());
        supplierRepository.save(supplier);
        return mapper.toDto(supplier);
    }

    /**
     * Soft-toggle a supplier's {@code active} flag. Suppliers with any posted
     * transaction should never be hard-deleted — this toggle is what removes
     * them from the "new PO" / "new PR" pickers while preserving history.
     */
    public SupplierDto toggleActive(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found"));
        supplier.setActive(!Boolean.TRUE.equals(supplier.getActive()));
        supplierRepository.save(supplier);
        return mapper.toDto(supplier);
    }

    public void deleteSupplier(Long id) {
        if (!supplierRepository.existsById(id)) {
            throw new RuntimeException("Supplier not found");
        }
        supplierRepository.deleteById(id);
    }
}