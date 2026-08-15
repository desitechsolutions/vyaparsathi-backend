package com.desitech.vyaparsathi.document.mapper;

import com.desitech.vyaparsathi.document.dto.PartyDto;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.entity.ShopBankAccount;
import com.desitech.vyaparsathi.shop.service.ShopBankAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartyMapperTest {

    @Mock private ShopBankAccountService bankAccountService;

    @InjectMocks private PartyMapper mapper;

    private Shop shop;

    @BeforeEach
    void setUp() {
        shop = new Shop();
        shop.setLegalName("Acme Traders Pvt Ltd");
        shop.setTradeName("Acme");
        shop.setGstin("10ABCDE1234F1Z5");
        shop.setState("Bihar");
        shop.setStateCode("10");
        shop.setAddress("12 Main St");
        shop.setUpiId("acme@hdfcbank");
    }

    @Test
    @DisplayName("Structured account wins over legacy blob when both are present")
    void structuredAccountWins() {
        ShopBankAccount account = new ShopBankAccount();
        account.setAccountHolderName("Acme Traders");
        account.setBankName("HDFC Bank");
        account.setAccountNumber("50200012345678");
        account.setIfscCode("HDFC0000123");
        account.setBranch("Sitamarhi");
        account.setUpiId("acme.trad@hdfc");
        account.setCurrencyCode("INR");
        when(bankAccountService.findDefault("INR")).thenReturn(Optional.of(account));

        // Blob would say a DIFFERENT bank + holder — must be ignored.
        shop.setBankDetails("Bank: SBI\nA/C Name: Old Name\nA/C Number: 9999\nIFSC: SBIN0001111");

        PartyDto p = mapper.fromShop(shop);

        assertEquals("Acme Traders", p.getBankHolderName());
        assertEquals("HDFC Bank", p.getBankName());
        assertEquals("50200012345678", p.getBankAccountNumber());
        assertEquals("HDFC0000123", p.getBankIfsc());
        assertEquals("Sitamarhi", p.getBankBranch());
        assertEquals("acme.trad@hdfc", p.getUpiId(), "Account-scoped UPI must win over shop.upiId");
    }

    @Test
    @DisplayName("Structured account with no UPI falls back to shop.upiId")
    void structuredAccountWithoutUpiFallsBackToShop() {
        ShopBankAccount account = new ShopBankAccount();
        account.setAccountHolderName("Acme Traders");
        account.setBankName("HDFC Bank");
        account.setAccountNumber("50200012345678");
        account.setCurrencyCode("INR");
        account.setUpiId(null);
        when(bankAccountService.findDefault("INR")).thenReturn(Optional.of(account));

        PartyDto p = mapper.fromShop(shop);

        assertEquals("acme@hdfcbank", p.getUpiId());
    }

    @Test
    @DisplayName("No structured account → legacy blob parser is used")
    void legacyBlobParsedAsFallback() {
        when(bankAccountService.findDefault("INR")).thenReturn(Optional.empty());
        shop.setBankDetails(
                "Bank Name: HDFC Bank\n" +
                "A/C Name: Acme Traders\n" +
                "A/C Number: 50200012345678 (Current)\n" +
                "IFSC Code: HDFC0001234\n" +
                "Branch: Sitamarhi");

        PartyDto p = mapper.fromShop(shop);

        assertEquals("HDFC Bank", p.getBankName());
        // The critical bug this replaces: "A/C Name" must NOT get picked as the account number.
        assertEquals("50200012345678 (Current)", p.getBankAccountNumber());
        assertEquals("HDFC0001234", p.getBankIfsc());
        assertEquals("Sitamarhi", p.getBankBranch());
        assertEquals("Acme Traders", p.getBankHolderName());
        assertEquals("acme@hdfcbank", p.getUpiId());
    }

    @Test
    @DisplayName("No structured account + no blob → bank fields are null but party still maps")
    void noBankAtAll() {
        when(bankAccountService.findDefault("INR")).thenReturn(Optional.empty());
        shop.setBankDetails(null);

        PartyDto p = mapper.fromShop(shop);

        assertNull(p.getBankName());
        assertNull(p.getBankAccountNumber());
        assertNull(p.getBankIfsc());
        assertNull(p.getBankHolderName());
        assertEquals("Acme Traders Pvt Ltd", p.getLegalName());
        assertEquals("acme@hdfcbank", p.getUpiId());
    }

    @Test
    @DisplayName("Bank service throwing (missing tenant context) falls through to blob without crashing")
    void serviceExceptionFallsBackGracefully() {
        when(bankAccountService.findDefault(any())).thenThrow(new RuntimeException("no tenant"));
        shop.setBankDetails("Bank: SBI\nA/C Number: 111\nIFSC: SBIN0000111");

        PartyDto p = mapper.fromShop(shop);

        assertEquals("SBI", p.getBankName());
        assertEquals("111", p.getBankAccountNumber());
        assertEquals("SBIN0000111", p.getBankIfsc());
    }
}
