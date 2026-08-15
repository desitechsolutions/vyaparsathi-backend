package com.desitech.vyaparsathi.shop.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.shop.dto.ShopBankAccountDto;
import com.desitech.vyaparsathi.shop.entity.ShopBankAccount;
import com.desitech.vyaparsathi.shop.repository.ShopBankAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Bank-account CRUD with the one-default-per-currency invariant enforced
 * atomically inside a single transaction. All reads are shop-scoped via
 * ShopFilterAspect, so this service doesn't touch tenant context.
 */
@Service
@Transactional
public class ShopBankAccountService {

    private final ShopBankAccountRepository repository;

    public ShopBankAccountService(ShopBankAccountRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ShopBankAccountDto> list() {
        return repository.findByIsActiveTrueOrderByIsDefaultDescLabelAsc()
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<ShopBankAccount> findDefault(String currencyCode) {
        return repository.findFirstByIsActiveTrueAndIsDefaultTrueAndCurrencyCode(
                currencyCode != null ? currencyCode : "INR");
    }

    public ShopBankAccountDto create(ShopBankAccountDto dto) {
        validate(dto);
        ShopBankAccount a = new ShopBankAccount();
        applyDto(a, dto);

        // First account for this shop + currency? Auto-mark as default.
        boolean shouldDefault = Boolean.TRUE.equals(dto.getIsDefault())
                || repository.findFirstByIsActiveTrueAndIsDefaultTrueAndCurrencyCode(a.getCurrencyCode()).isEmpty();
        if (shouldDefault) {
            repository.clearDefaultForCurrency(a.getCurrencyCode());
            a.setIsDefault(Boolean.TRUE);
        }
        return toDto(repository.save(a));
    }

    public ShopBankAccountDto update(Long id, ShopBankAccountDto dto) {
        validate(dto);
        ShopBankAccount a = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account not found: " + id));
        String oldCurrency = a.getCurrencyCode();
        applyDto(a, dto);
        // If the caller set it as default, clear any peer default first.
        // Currency may also have changed — clear on the NEW currency.
        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            repository.clearDefaultForCurrency(a.getCurrencyCode());
            a.setIsDefault(Boolean.TRUE);
        } else if (!a.getCurrencyCode().equals(oldCurrency) && Boolean.TRUE.equals(a.getIsDefault())) {
            // Currency changed while remaining default → conflict on the new
            // currency partition. Clear peers there too.
            repository.clearDefaultForCurrency(a.getCurrencyCode());
        }
        return toDto(repository.save(a));
    }

    public void delete(Long id) {
        ShopBankAccount a = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account not found: " + id));
        boolean wasDefault = Boolean.TRUE.equals(a.getIsDefault());
        String currency = a.getCurrencyCode();
        // Soft-delete: keep the row for audit, flip is_active. Historical
        // invoices that referenced the account can still resolve it.
        a.setIsActive(Boolean.FALSE);
        a.setIsDefault(Boolean.FALSE);
        repository.save(a);

        if (wasDefault) {
            // Promote the first remaining active account in the same currency
            // to default, so an invoice can still print a bank block.
            repository.findByIsActiveTrueOrderByIsDefaultDescLabelAsc().stream()
                    .filter(x -> currency.equals(x.getCurrencyCode()))
                    .findFirst()
                    .ifPresent(x -> { x.setIsDefault(Boolean.TRUE); repository.save(x); });
        }
    }

    public ShopBankAccountDto setDefault(Long id) {
        ShopBankAccount a = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account not found: " + id));
        if (!Boolean.TRUE.equals(a.getIsActive())) {
            throw new BusinessValidationException("Cannot mark an inactive account as default");
        }
        repository.clearDefaultForCurrency(a.getCurrencyCode());
        a.setIsDefault(Boolean.TRUE);
        return toDto(repository.save(a));
    }

    // ── helpers ────────────────────────────────────────────────────────

    private void validate(ShopBankAccountDto dto) {
        if (isBlank(dto.getAccountHolderName())) throw new BusinessValidationException("Account holder name is required");
        if (isBlank(dto.getAccountNumber()))     throw new BusinessValidationException("Account number is required");
        if (isBlank(dto.getBankName()))          throw new BusinessValidationException("Bank name is required");
        if (!isBlank(dto.getIfscCode()) && !dto.getIfscCode().matches("^[A-Z]{4}0[A-Z0-9]{6}$"))
            throw new BusinessValidationException("Invalid IFSC — expected 11 characters (e.g. HDFC0000123)");
    }

    private void applyDto(ShopBankAccount a, ShopBankAccountDto d) {
        a.setLabel(d.getLabel());
        a.setAccountHolderName(d.getAccountHolderName());
        a.setAccountNumber(d.getAccountNumber());
        a.setBankName(d.getBankName());
        a.setIfscCode(d.getIfscCode());
        a.setBranch(d.getBranch());
        a.setAccountType(d.getAccountType());
        a.setCurrencyCode(d.getCurrencyCode() == null || d.getCurrencyCode().isBlank() ? "INR" : d.getCurrencyCode().toUpperCase());
        a.setSwiftCode(d.getSwiftCode());
        a.setIban(d.getIban());
        a.setUpiId(d.getUpiId());
        a.setPurpose(d.getPurpose());
        a.setDisplayOnInvoice(d.getDisplayOnInvoice() == null ? Boolean.TRUE : d.getDisplayOnInvoice());
        a.setIsActive(d.getIsActive() == null ? Boolean.TRUE : d.getIsActive());
        a.setNotes(d.getNotes());
    }

    public ShopBankAccountDto toDto(ShopBankAccount a) {
        ShopBankAccountDto d = new ShopBankAccountDto();
        d.setId(a.getId());
        d.setLabel(a.getLabel());
        d.setAccountHolderName(a.getAccountHolderName());
        d.setAccountNumber(a.getAccountNumber());
        d.setBankName(a.getBankName());
        d.setIfscCode(a.getIfscCode());
        d.setBranch(a.getBranch());
        d.setAccountType(a.getAccountType());
        d.setCurrencyCode(a.getCurrencyCode());
        d.setSwiftCode(a.getSwiftCode());
        d.setIban(a.getIban());
        d.setUpiId(a.getUpiId());
        d.setPurpose(a.getPurpose());
        d.setIsDefault(a.getIsDefault());
        d.setIsActive(a.getIsActive());
        d.setDisplayOnInvoice(a.getDisplayOnInvoice());
        d.setNotes(a.getNotes());
        return d;
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}
