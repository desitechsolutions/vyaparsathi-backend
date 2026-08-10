package com.desitech.vyaparsathi.supplier.service;

import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.supplier.dto.SupplierLedgerDto;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import com.desitech.vyaparsathi.supplier.entity.SupplierLedger;
import com.desitech.vyaparsathi.supplier.repository.SupplierLedgerRepository;
import com.desitech.vyaparsathi.supplier.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class SupplierLedgerService {

    private final SupplierLedgerRepository ledgerRepo;
    private final SupplierRepository supplierRepo;

    public SupplierLedgerService(SupplierLedgerRepository ledgerRepo, SupplierRepository supplierRepo) {
        this.ledgerRepo = ledgerRepo;
        this.supplierRepo = supplierRepo;
    }

    @Transactional
    public SupplierLedger recordEntry(Supplier supplier, String transactionType, String referenceNo,
                                      BigDecimal debitAmount, BigDecimal creditAmount, String notes) {
        Long shopId = TenantUtils.getCurrentShopId();
        BigDecimal lastBalance = ledgerRepo.findLatestEntry(shopId, supplier.getId())
                .map(SupplierLedger::getRunningBalance)
                .orElse(BigDecimal.ZERO);

        // Credit increases balance owed to supplier; Debit reduces balance owed
        BigDecimal debit = debitAmount != null ? debitAmount : BigDecimal.ZERO;
        BigDecimal credit = creditAmount != null ? creditAmount : BigDecimal.ZERO;
        BigDecimal newBalance = lastBalance.add(credit).subtract(debit);

        SupplierLedger entry = new SupplierLedger();
        entry.setSupplier(supplier);
        entry.setTransactionDate(LocalDateTime.now());
        entry.setTransactionType(transactionType);
        entry.setReferenceNo(referenceNo);
        entry.setDebitAmount(debit);
        entry.setCreditAmount(credit);
        entry.setRunningBalance(newBalance);
        entry.setNotes(notes);

        return ledgerRepo.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<SupplierLedgerDto> getSupplierLedger(Long supplierId, Pageable pageable) {
        Long shopId = TenantUtils.getCurrentShopId();
        return ledgerRepo.findByShopIdAndSupplierIdOrderByTransactionDateDesc(shopId, supplierId, pageable)
                .map(this::toDto);
    }

    private SupplierLedgerDto toDto(SupplierLedger entity) {
        SupplierLedgerDto dto = new SupplierLedgerDto();
        dto.setId(entity.getId());
        dto.setSupplierId(entity.getSupplier().getId());
        dto.setSupplierName(entity.getSupplier().getSupplierName());
        dto.setTransactionDate(entity.getTransactionDate());
        dto.setTransactionType(entity.getTransactionType());
        dto.setReferenceNo(entity.getReferenceNo());
        dto.setDebitAmount(entity.getDebitAmount());
        dto.setCreditAmount(entity.getCreditAmount());
        dto.setRunningBalance(entity.getRunningBalance());
        dto.setNotes(entity.getNotes());
        return dto;
    }
}
