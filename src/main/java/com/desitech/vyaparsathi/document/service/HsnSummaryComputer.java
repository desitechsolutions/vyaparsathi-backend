package com.desitech.vyaparsathi.document.service;

import com.desitech.vyaparsathi.document.dto.EnterpriseDocumentDto;
import com.desitech.vyaparsathi.document.dto.HsnSummaryRowDto;
import com.desitech.vyaparsathi.document.dto.LineItemDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the "HSN summary" compliance table required on Indian
 * tax invoices. Groups line items by HSN/SAC and sums taxable + tax
 * amounts per bucket.
 */
@Service
public class HsnSummaryComputer {

    public void populate(EnterpriseDocumentDto doc) {
        if (doc == null || doc.getItems() == null || doc.getItems().isEmpty()) return;
        Map<String, HsnSummaryRowDto> bucket = new LinkedHashMap<>();
        for (LineItemDto ln : doc.getItems()) {
            String key = ln.getHsnSac() == null ? "—" : ln.getHsnSac();
            HsnSummaryRowDto row = bucket.computeIfAbsent(key, k -> {
                HsnSummaryRowDto r = new HsnSummaryRowDto();
                r.setHsnSac(k);
                return r;
            });
            row.setTaxableValue(row.getTaxableValue().add(nz(ln.getTaxableValue())));
            row.setCgstAmount(row.getCgstAmount().add(nz(ln.getCgstAmount())));
            row.setSgstAmount(row.getSgstAmount().add(nz(ln.getSgstAmount())));
            row.setIgstAmount(row.getIgstAmount().add(nz(ln.getIgstAmount())));
            row.setCessAmount(row.getCessAmount().add(nz(ln.getCessAmount())));
        }
        for (HsnSummaryRowDto r : bucket.values()) {
            BigDecimal tax = r.getCgstAmount().add(r.getSgstAmount()).add(r.getIgstAmount()).add(r.getCessAmount());
            r.setTotalTax(tax);
            r.setGrandTotal(r.getTaxableValue().add(tax));
        }
        doc.setHsnSummary(new java.util.ArrayList<>(bucket.values()));
    }

    private BigDecimal nz(BigDecimal b) { return b == null ? BigDecimal.ZERO : b; }
}
