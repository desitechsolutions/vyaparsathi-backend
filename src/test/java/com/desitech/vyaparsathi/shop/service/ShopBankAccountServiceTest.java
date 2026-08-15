package com.desitech.vyaparsathi.shop.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.shop.dto.ShopBankAccountDto;
import com.desitech.vyaparsathi.shop.entity.ShopBankAccount;
import com.desitech.vyaparsathi.shop.repository.ShopBankAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopBankAccountServiceTest {

    @Mock private ShopBankAccountRepository repository;

    @InjectMocks private ShopBankAccountService service;

    private ShopBankAccountDto validDto;

    @BeforeEach
    void setUp() {
        validDto = new ShopBankAccountDto();
        validDto.setLabel("Main current");
        validDto.setAccountHolderName("Acme Traders");
        validDto.setAccountNumber("50200012345678");
        validDto.setBankName("HDFC Bank");
        validDto.setIfscCode("HDFC0000123");
        validDto.setBranch("Sitamarhi");
        validDto.setAccountType("CURRENT");
        validDto.setCurrencyCode("INR");
    }

    private ShopBankAccount entity(Long id, String currency, boolean isDefault) {
        ShopBankAccount a = new ShopBankAccount();
        a.setId(id);
        a.setAccountHolderName("Acme Traders");
        a.setAccountNumber("50200012345678");
        a.setBankName("HDFC Bank");
        a.setIfscCode("HDFC0000123");
        a.setCurrencyCode(currency);
        a.setIsDefault(isDefault);
        a.setIsActive(true);
        return a;
    }

    @Test
    @DisplayName("create: first account for a currency is auto-marked default")
    void createFirstAccountIsDefault() {
        when(repository.findFirstByIsActiveTrueAndIsDefaultTrueAndCurrencyCode("INR"))
                .thenReturn(Optional.empty());
        when(repository.save(any(ShopBankAccount.class))).thenAnswer(i -> {
            ShopBankAccount a = i.getArgument(0);
            a.setId(1L);
            return a;
        });

        ShopBankAccountDto created = service.create(validDto);

        assertTrue(created.getIsDefault(), "First account should be default");
        verify(repository).clearDefaultForCurrency("INR");
    }

    @Test
    @DisplayName("create: setting isDefault=true clears peer defaults on the same currency")
    void createSetDefaultClearsPeers() {
        validDto.setIsDefault(true);
        when(repository.save(any(ShopBankAccount.class))).thenAnswer(i -> {
            ShopBankAccount a = i.getArgument(0);
            a.setId(2L);
            return a;
        });

        ShopBankAccountDto created = service.create(validDto);

        assertTrue(created.getIsDefault());
        verify(repository).clearDefaultForCurrency("INR");
    }

    @Test
    @DisplayName("validate: blank account holder rejected")
    void createRejectsBlankHolder() {
        validDto.setAccountHolderName("  ");
        assertThrows(BusinessValidationException.class, () -> service.create(validDto));
    }

    @Test
    @DisplayName("validate: bad IFSC rejected")
    void createRejectsBadIfsc() {
        validDto.setIfscCode("hdfc123");  // wrong length + wrong pattern
        assertThrows(BusinessValidationException.class, () -> service.create(validDto));
    }

    @Test
    @DisplayName("validate: missing IFSC allowed (foreign accounts)")
    void createAllowsMissingIfsc() {
        validDto.setIfscCode(null);
        validDto.setCurrencyCode("USD");
        when(repository.findFirstByIsActiveTrueAndIsDefaultTrueAndCurrencyCode("USD"))
                .thenReturn(Optional.empty());
        when(repository.save(any(ShopBankAccount.class))).thenAnswer(i -> i.getArgument(0));

        ShopBankAccountDto created = service.create(validDto);
        assertNotNull(created);
    }

    @Test
    @DisplayName("setDefault: clears peers, flips is_default true")
    void setDefaultPromotes() {
        ShopBankAccount existing = entity(5L, "INR", false);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.save(any(ShopBankAccount.class))).thenAnswer(i -> i.getArgument(0));

        ShopBankAccountDto out = service.setDefault(5L);

        assertTrue(out.getIsDefault());
        verify(repository).clearDefaultForCurrency("INR");
    }

    @Test
    @DisplayName("setDefault: inactive account is rejected")
    void setDefaultRejectsInactive() {
        ShopBankAccount inactive = entity(6L, "INR", false);
        inactive.setIsActive(false);
        when(repository.findById(6L)).thenReturn(Optional.of(inactive));

        assertThrows(BusinessValidationException.class, () -> service.setDefault(6L));
    }

    @Test
    @DisplayName("delete: soft-deletes default and promotes next active in same currency")
    void deletePromotesNextDefault() {
        ShopBankAccount current = entity(1L, "INR", true);
        ShopBankAccount peer    = entity(2L, "INR", false);
        when(repository.findById(1L)).thenReturn(Optional.of(current));
        when(repository.findByIsActiveTrueOrderByIsDefaultDescLabelAsc())
                .thenReturn(List.of(peer));
        when(repository.save(any(ShopBankAccount.class))).thenAnswer(i -> i.getArgument(0));

        service.delete(1L);

        assertFalse(current.getIsActive(), "Original should be soft-deleted");
        assertFalse(current.getIsDefault(), "Original should no longer be default");
        ArgumentCaptor<ShopBankAccount> captor = ArgumentCaptor.forClass(ShopBankAccount.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        boolean promoted = captor.getAllValues().stream()
                .anyMatch(a -> a.getId() != null && a.getId() == 2L && Boolean.TRUE.equals(a.getIsDefault()));
        assertTrue(promoted, "Peer account should have been promoted to default");
    }

    @Test
    @DisplayName("delete: soft-deleting non-default leaves defaults alone")
    void deleteNonDefaultKeepsPeers() {
        ShopBankAccount nonDefault = entity(3L, "INR", false);
        when(repository.findById(3L)).thenReturn(Optional.of(nonDefault));
        when(repository.save(any(ShopBankAccount.class))).thenAnswer(i -> i.getArgument(0));

        service.delete(3L);

        assertFalse(nonDefault.getIsActive());
        verify(repository, never()).findByIsActiveTrueOrderByIsDefaultDescLabelAsc();
    }

    @Test
    @DisplayName("update: nonexistent id → ResourceNotFoundException")
    void updateMissingThrows() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.update(99L, validDto));
    }

    @Test
    @DisplayName("findDefault: returns repository result verbatim")
    void findDefaultDelegates() {
        ShopBankAccount def = entity(7L, "INR", true);
        when(repository.findFirstByIsActiveTrueAndIsDefaultTrueAndCurrencyCode("INR"))
                .thenReturn(Optional.of(def));

        assertSame(def, service.findDefault("INR").orElseThrow());
    }

    @Test
    @DisplayName("findDefault: null currency defaults to INR")
    void findDefaultNullDefaultsInr() {
        service.findDefault(null);
        verify(repository).findFirstByIsActiveTrueAndIsDefaultTrueAndCurrencyCode("INR");
    }
}
