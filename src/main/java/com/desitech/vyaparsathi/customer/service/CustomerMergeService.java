package com.desitech.vyaparsathi.customer.service;

import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Merge two customers into one. Every transactional record linked to
 * the source (sales, quotations, sales orders, credit notes, payments,
 * refunds, receipts, ledger, audit) is re-attached to the target;
 * source-side profile children (contacts / addresses / notes /
 * attachments / custom fields / segment memberships) are removed via
 * the ON DELETE CASCADE constraints when the source row is dropped.
 *
 * <p>Merges are shop-scoped: attempting to merge across shops is
 * refused because it would violate the multi-tenant invariant.</p>
 */
@Service
public class CustomerMergeService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerMergeService.class);

    private final CustomerRepository customerRepo;
    private final CustomerAuditService auditService;

    @PersistenceContext
    private EntityManager em;

    public CustomerMergeService(CustomerRepository customerRepo, CustomerAuditService auditService) {
        this.customerRepo = customerRepo;
        this.auditService = auditService;
    }

    /**
     * Merge {@code sourceId} into {@code targetId}. Returns a summary
     * map with per-table row counts so the FE can show "merged 12 sales,
     * 4 quotes, 3 payments" — a merge failure would otherwise be a
     * silent transfer with no confirmation.
     */
    @Transactional
    public Map<String, Object> merge(Long sourceId, Long targetId) {
        if (sourceId == null || targetId == null) {
            throw new ApplicationException("Both source and target customer ids are required.");
        }
        if (sourceId.equals(targetId)) {
            throw new ApplicationException("Source and target must be different customers.");
        }
        Customer source = customerRepo.findById(sourceId)
                .orElseThrow(() -> new EntityNotFoundException("Source customer not found: " + sourceId));
        Customer target = customerRepo.findById(targetId)
                .orElseThrow(() -> new EntityNotFoundException("Target customer not found: " + targetId));
        // Shop-scope guard — both records must belong to the same shop.
        // ShopFilterAspect already enforces this on the findById calls
        // above, but a belt-and-braces check keeps the intent explicit.
        Long sShop = source.getShop() != null ? source.getShop().getId() : null;
        Long tShop = target.getShop() != null ? target.getShop().getId() : null;
        if (sShop == null || tShop == null || !sShop.equals(tShop)) {
            throw new ApplicationException("Cannot merge customers across shops.");
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("sourceId", sourceId);
        summary.put("targetId", targetId);
        summary.put("sourceName", source.getName());
        summary.put("targetName", target.getName());

        // ─── Re-link transactional records ─────────────────────────────
        summary.put("sales", updateFk("sale", "customer_id", sourceId, targetId));
        summary.put("quotations", updateFk("quotation", "customer_id", sourceId, targetId));
        summary.put("salesOrders", updateFk("sales_order", "customer_id", sourceId, targetId));
        summary.put("creditNotes", updateFk("credit_note", "customer_id", sourceId, targetId));
        summary.put("payments", updateFk("payment", "customer_id", sourceId, targetId));
        summary.put("paymentReceipts", updateFk("payment_receipt", "customer_id", sourceId, targetId));
        summary.put("refunds", updateFk("refund", "customer_id", sourceId, targetId));
        summary.put("ledgerEntries", updateFk("customer_ledger", "customer_id", sourceId, targetId));
        // Delivery challans link via Sale, not directly to customer,
        // so re-linking sales above transfers them implicitly.

        // ─── Merge credit balance ──────────────────────────────────────
        BigDecimal sBal = source.getCreditBalance() == null ? BigDecimal.ZERO : source.getCreditBalance();
        BigDecimal tBal = target.getCreditBalance() == null ? BigDecimal.ZERO : target.getCreditBalance();
        target.setCreditBalance(tBal.add(sBal));
        summary.put("mergedCreditBalance", target.getCreditBalance());

        // ─── Merge segment memberships (union) ─────────────────────────
        int segmentsUnioned = ((Number) em.createNativeQuery(
                "INSERT IGNORE INTO customer_segment_member (customer_id, segment_id, added_at) " +
                "SELECT :target, segment_id, added_at FROM customer_segment_member WHERE customer_id = :source")
                .setParameter("target", targetId)
                .setParameter("source", sourceId)
                .executeUpdate()).intValue();
        summary.put("segmentsUnioned", segmentsUnioned);

        // ─── Audit both sides so the merge is discoverable later ──────
        auditService.recordAction(targetId, "MERGED_IN",
                "Merged customer '" + source.getName() + "' (id=" + sourceId + ") into this record.", null);
        auditService.recordAction(sourceId, "MERGED_INTO",
                "Merged into '" + target.getName() + "' (id=" + targetId + ").", null);

        // ─── Persist target updates, then drop source ─────────────────
        customerRepo.save(target);
        // Source profile children cascade-delete via FK constraints on
        // customer_contact / customer_address / customer_attachment /
        // customer_note / customer_custom_field_value / customer_segment_member.
        customerRepo.delete(source);

        logger.info("Merged customer {} into {}: {}", sourceId, targetId, summary);
        return summary;
    }

    /**
     * Bulk UPDATE of a foreign key column, returning the row count.
     * Native SQL because the tables involved don't all live in the
     * customer package (Sale, CreditNote, Payment, etc.), and the
     * cross-package JPQL would drag half the schema into the imports.
     */
    private int updateFk(String tableName, String fkColumn, Long from, Long to) {
        return em.createNativeQuery(
                "UPDATE `" + tableName + "` SET `" + fkColumn + "` = :to WHERE `" + fkColumn + "` = :from")
                .setParameter("from", from)
                .setParameter("to", to)
                .executeUpdate();
    }
}
