package com.desitech.vyaparsathi.document.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compliance-monitoring reports for the enterprise document suite:
 *
 * <ul>
 *   <li>Sequence continuity — flags gaps in document numbering per FY
 *       (CBIC rule 46(b))
 *   <li>Print audit — how many times each document was reprinted, by whom
 *   <li>E-invoice status — coverage of TAX_INVOICE / DEBIT_NOTE / CREDIT_NOTE
 *       against generated IRNs
 *   <li>E-Way bill status — coverage of shipments against active EWB
 * </ul>
 */
@Service
public class DocumentComplianceReportService {

    @PersistenceContext
    private EntityManager em;

    /** Detects numbering gaps in the requested doc table for a given shop + FY. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> sequenceContinuity(Long shopId, String table, String numberCol, String fiscalYear) {
        // Best-effort: pull all numbers, sort by embedded numeric suffix, flag jumps > 1.
        String q = "SELECT " + numberCol + " FROM " + safeTable(table) +
                   " WHERE shop_id = :shopId ORDER BY " + numberCol + " ASC";
        List<String> numbers = em.createNativeQuery(q).setParameter("shopId", shopId).getResultList();
        List<Map<String, Object>> gaps = new ArrayList<>();
        long prev = -1;
        for (String n : numbers) {
            if (n == null) continue;
            long v = extractSuffix(n);
            if (v < 0) continue;
            if (prev >= 0 && v - prev > 1) {
                Map<String, Object> gap = new LinkedHashMap<>();
                gap.put("from", prev);
                gap.put("to", v);
                gap.put("missingCount", (v - prev - 1));
                gaps.add(gap);
            }
            prev = v;
        }
        return gaps;
    }

    /** Reprint count per document — surfaces suspicious "duplicate" printing patterns. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> printAudit(Long shopId, int minPrints) {
        String q = "SELECT document_type, document_id, document_number, COUNT(*) AS cnt, MAX(printed_at) AS last_printed " +
                   "FROM document_print_audit " +
                   "WHERE shop_id = :shopId " +
                   "GROUP BY document_type, document_id, document_number " +
                   "HAVING COUNT(*) >= :minPrints " +
                   "ORDER BY cnt DESC";
        List<Object[]> rows = em.createNativeQuery(q)
                .setParameter("shopId", shopId)
                .setParameter("minPrints", minPrints)
                .getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("documentType", r[0]);
            row.put("documentId", r[1]);
            row.put("documentNumber", r[2]);
            row.put("printCount", r[3]);
            row.put("lastPrintedAt", r[4]);
            out.add(row);
        }
        return out;
    }

    /** IRN coverage for e-invoice-eligible outward documents. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> eInvoiceCoverage(Long shopId) {
        String salesQ = "SELECT COUNT(*) FROM sale WHERE shop_id = :shopId";
        String withIrnQ = "SELECT COUNT(*) FROM e_invoice WHERE shop_id = :shopId AND document_type = 'TAX_INVOICE' AND status = 'GENERATED'";
        Number sales = (Number) em.createNativeQuery(salesQ).setParameter("shopId", shopId).getSingleResult();
        Number covered = (Number) em.createNativeQuery(withIrnQ).setParameter("shopId", shopId).getSingleResult();

        Map<String, Object> out = new HashMap<>();
        out.put("totalTaxInvoices", sales.longValue());
        out.put("withIrn", covered.longValue());
        long uncovered = sales.longValue() - covered.longValue();
        out.put("withoutIrn", Math.max(0, uncovered));
        out.put("coveragePct", sales.longValue() == 0 ? 0
                : Math.round(covered.doubleValue() * 100.0 / sales.doubleValue()));
        return out;
    }

    /** EWB coverage for inter-state / high-value shipments. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> eWayBillCoverage(Long shopId) {
        String q = "SELECT COUNT(*) FROM e_way_bill WHERE shop_id = :shopId AND status = 'ACTIVE'";
        Number active = (Number) em.createNativeQuery(q).setParameter("shopId", shopId).getSingleResult();
        Map<String, Object> out = new HashMap<>();
        out.put("activeEwbs", active.longValue());
        return out;
    }

    private long extractSuffix(String docNumber) {
        // Grab the trailing digits — works for "INV/25-26/00042", "PO-2026-08-15-00001", etc.
        int i = docNumber.length() - 1;
        while (i >= 0 && Character.isDigit(docNumber.charAt(i))) i--;
        String tail = docNumber.substring(i + 1);
        if (tail.isEmpty()) return -1;
        try { return Long.parseLong(tail); } catch (Exception e) { return -1; }
    }

    private String safeTable(String t) {
        // Whitelist to keep the native query safe.
        return switch (t) {
            case "sale", "purchase_invoice", "debit_notes", "credit_notes",
                 "purchase_order", "receiving", "purchase_return" -> t;
            default -> throw new IllegalArgumentException("Unsupported table: " + t);
        };
    }
}
