package com.desitech.vyaparsathi.document.mapper;

import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.document.dto.PartyDto;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import org.springframework.stereotype.Component;

/**
 * Normalises the three party-bearing entities (Shop, Supplier, Customer)
 * into a common {@link PartyDto}. Keeps the per-document mappers thin.
 */
@Component
public class PartyMapper {

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
        // Bank
        p.setBankName(extractBankLine(s.getBankDetails(), "bank"));
        p.setBankAccountNumber(extractBankLine(s.getBankDetails(), "a/c"));
        p.setBankIfsc(extractBankLine(s.getBankDetails(), "ifsc"));
        p.setUpiId(s.getUpiId());
        // Signatory
        p.setSignatoryName(s.getSignatoryName());
        p.setSignatoryDesignation(s.getSignatoryDesignation());
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

    /** Best-effort parser for the free-text `bankDetails` field. */
    private String extractBankLine(String blob, String needle) {
        if (blob == null) return null;
        for (String raw : blob.split("[\r\n,;]+")) {
            String l = raw.trim();
            if (l.toLowerCase().startsWith(needle.toLowerCase())) {
                int idx = l.indexOf(":");
                return idx >= 0 ? l.substring(idx + 1).trim() : l;
            }
        }
        return null;
    }
}
