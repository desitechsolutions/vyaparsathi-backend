package com.desitech.vyaparsathi.document.mapper;

import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.document.dto.PartyDto;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.entity.ShopBankAccount;
import com.desitech.vyaparsathi.shop.service.ShopBankAccountService;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Normalises the three party-bearing entities (Shop, Supplier, Customer)
 * into a common {@link PartyDto}. Keeps the per-document mappers thin.
 *
 * <p>Bank details resolution order for the shop (issuer): the structured
 * {@code shop_bank_account} default row wins; if there is none yet, the
 * legacy free-text {@code shop.bank_details} blob is parsed as a fallback.
 * This lets a shop migrate at its own pace without breaking today's PDFs.
 */
@Component
public class PartyMapper {

    private final ShopBankAccountService bankAccountService;

    public PartyMapper(ShopBankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
    }

    public PartyDto fromShop(Shop s) {
        if (s == null) return null;
        PartyDto p = new PartyDto();
        p.setRole("ISSUER");
        p.setLegalName(s.getLegalName());
        p.setTradeName(s.getTradeName());
        p.setPan(s.getPan());
        p.setCin(s.getCin());
        p.setGstin(s.getGstin());
        p.setAddressLine1(s.getAddress());
        p.setAddressLine2(s.getAddressLine2());
        p.setCity(s.getCity());
        p.setState(s.getState());
        p.setStateCode(s.getStateCode());
        p.setPincode(s.getPincode());
        p.setCountry(s.getCountry());
        p.setPhone(s.getPhone());
        p.setEmail(s.getEmail());
        p.setWebsite(s.getCompanyWebsite());
        // Signatory
        p.setSignatoryName(s.getSignatoryName());
        p.setSignatoryDesignation(s.getSignatoryDesignation());
        // Bank — structured account first, blob fallback.
        applyBank(p, s);
        return p;
    }

    public PartyDto fromSupplier(Supplier s) {
        if (s == null) return null;
        PartyDto p = new PartyDto();
        p.setRole("SUPPLIER");
        p.setLegalName(s.getLegalName());
        p.setTradeName(s.getTradeName());
        p.setPan(s.getPan());
        p.setGstin(s.getGstin());
        p.setAddressLine1(s.getAddress());
        p.setCity(s.getCity());
        p.setState(s.getState());
        p.setStateCode(s.getStateCode());
        p.setPincode(s.getPincode());
        p.setCountry(s.getCountry());
        p.setPhone(s.getPhone());
        p.setEmail(s.getEmail());
        return p;
    }

    public PartyDto fromCustomer(Customer c) {
        if (c == null) return null;
        PartyDto p = new PartyDto();
        p.setRole("CUSTOMER");
        p.setLegalName(c.getLegalName());
        p.setTradeName(c.getName());
        p.setPan(c.getPanNumber());
        p.setGstin(c.getGstNumber());
        p.setAddressLine1(c.getAddressLine1());
        p.setAddressLine2(c.getAddressLine2());
        p.setCity(c.getCity());
        p.setState(c.getState());
        p.setStateCode(c.getStateCode());
        p.setPincode(c.getPostalCode());
        p.setCountry(c.getCountry());
        p.setPhone(c.getPhone());
        p.setEmail(c.getEmail());
        return p;
    }

    /**
     * Populate the bank sub-block from the shop's default structured account
     * when present; otherwise fall back to parsing the legacy blob so shops
     * that haven't migrated yet still print a bank block.
     */
    private void applyBank(PartyDto p, Shop s) {
        Optional<ShopBankAccount> defaultAccount = Optional.empty();
        if (bankAccountService != null) {
            try {
                defaultAccount = bankAccountService.findDefault("INR");
            } catch (RuntimeException ignore) {
                // Repository may not have a tenant context in some code paths
                // (public verification, background jobs). Fall through to the
                // legacy blob rather than blowing up the PDF.
            }
        }
        if (defaultAccount.isPresent()) {
            ShopBankAccount ba = defaultAccount.get();
            p.setBankHolderName(ba.getAccountHolderName());
            p.setBankName(ba.getBankName());
            p.setBankAccountNumber(ba.getAccountNumber());
            p.setBankIfsc(ba.getIfscCode());
            p.setBankBranch(ba.getBranch());
            // Structured account's UPI wins; fall back to shop-level UPI if
            // the account row didn't set one (e.g. USD EEFC account, INR UPI
            // still lives on the shop).
            p.setUpiId(ba.getUpiId() != null && !ba.getUpiId().isBlank()
                    ? ba.getUpiId() : s.getUpiId());
            return;
        }

        // Legacy free-text blob path — kept until every shop has been backfilled.
        java.util.Map<String, String> bank = parseBankBlob(s.getBankDetails());
        p.setBankHolderName(pick(bank, "a/c name", "account name", "acc name", "beneficiary", "holder"));
        p.setBankName(pick(bank, "bank name", "bank"));
        p.setBankAccountNumber(pick(bank, "a/c number", "account number", "ac number",
                "a/c no", "account no", "acc no", "acc number"));
        p.setBankIfsc(pick(bank, "ifsc code", "ifsc"));
        p.setBankBranch(pick(bank, "branch"));
        p.setUpiId(s.getUpiId());
    }

    /**
     * Parses the free-text bankDetails blob into a key→value map, using
     * everything before the first ":" as the key (lower-cased, whitespace
     * normalised). Handles all common Indian formats:
     *   "Bank Name: HDFC Bank"
     *   "A/C Name:  Acme Traders"
     *   "A/C Number: 50200012345678 (Current)"
     *   "IFSC Code: HDFC0001234"
     *   "Branch: Sitamarhi"
     */
    private java.util.Map<String, String> parseBankBlob(String blob) {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        if (blob == null || blob.isBlank()) return out;
        for (String raw : blob.split("\\r?\\n")) {
            String l = raw.trim();
            int idx = l.indexOf(':');
            if (idx <= 0) continue;
            String key = l.substring(0, idx).trim().toLowerCase().replaceAll("\\s+", " ");
            String val = l.substring(idx + 1).trim();
            if (!key.isEmpty() && !val.isEmpty()) out.put(key, val);
        }
        return out;
    }

    /** Return the first present value for any of the given candidate keys. */
    private String pick(java.util.Map<String, String> map, String... keys) {
        for (String k : keys) {
            String v = map.get(k);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
