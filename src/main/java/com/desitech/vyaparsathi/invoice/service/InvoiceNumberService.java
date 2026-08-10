package com.desitech.vyaparsathi.invoice.service;

import com.desitech.vyaparsathi.invoice.entity.InvoiceSequence;
import com.desitech.vyaparsathi.invoice.repository.InvoiceSequenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Provides atomic, race-condition-safe invoice number generation.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Each row in {@code invoice_sequence} represents a unique (shop, prefix, year) counter.</li>
 *   <li>The row is fetched with {@code SELECT … FOR UPDATE} (pessimistic write lock) before
 *       incrementing, so no two concurrent transactions can ever receive the same sequence number.</li>
 *   <li>The method runs in its own {@code REQUIRES_NEW} transaction so the lock is held only for
 *       the fraction of a second needed to read-and-increment, minimising contention.</li>
 * </ul>
 *
 * <h3>Invoice Format</h3>
 * <pre>{prefix}/{YY-YY}/{padded-sequence}</pre>
 * Example: {@code VS/25-26/00042}
 */
@Service
public class InvoiceNumberService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceNumberService.class);

    /** Width of the zero-padded sequence number in the invoice string. */
    private static final int SEQ_PAD_WIDTH = 5;

    private final InvoiceSequenceRepository repo;

    public InvoiceNumberService(InvoiceSequenceRepository repo) {
        this.repo = repo;
    }

    /**
     * Generates the next unique invoice number for the given shop and prefix.
     *
     * <p>Runs in a fresh {@code REQUIRES_NEW} transaction so the pessimistic lock
     * is released immediately after the counter is incremented, keeping the lock
     * window as small as possible even when the parent transaction is long-running.
     *
     * @param shopId the tenant shop ID
     * @param prefix the invoice prefix (e.g. shop's {@code invoicePrefix} or "INV")
     * @param date   the sale date used to determine the fiscal year
     * @return a formatted invoice number, e.g. {@code INV/25-26/00042}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextInvoiceNumber(Long shopId, String prefix, LocalDate date) {
        short fiscalYear = fiscalYearOf(date);
        String effectivePrefix = (prefix != null && !prefix.isBlank()) ? prefix.trim().toUpperCase() : "INV";

        InvoiceSequence seq = repo.findByShopIdAndPrefixAndFiscalYearForUpdate(shopId, effectivePrefix, fiscalYear)
                .orElseGet(() -> createNewSequence(shopId, effectivePrefix, fiscalYear));

        long nextSeq = seq.getLastSeq() + 1;
        seq.setLastSeq(nextSeq);
        repo.saveAndFlush(seq);

        String invoiceNo = format(effectivePrefix, fiscalYear, nextSeq);
        log.debug("Generated invoice number {} for shopId={}", invoiceNo, shopId);
        return invoiceNo;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Determines the fiscal year start year.
     * India: FY starts April 1. So April 2025 → fiscal year 2025 (i.e. FY 2025–26).
     * Jan–Mar 2026 → fiscal year 2025 (still FY 2025–26).
     */
    private short fiscalYearOf(LocalDate date) {
        int month = date.getMonthValue();
        int year  = date.getYear();
        // If month is Jan, Feb, Mar → belongs to previous fiscal year
        return (short) (month < 4 ? year - 1 : year);
    }

    /**
     * Formats the invoice number as {@code PREFIX/YY-YY/NNNNN}.
     * Example: {@code INV/25-26/00001}
     */
    private String format(String prefix, short fiscalYear, long seq) {
        int startYY = fiscalYear % 100;
        int endYY   = (fiscalYear + 1) % 100;
        String seqStr = String.format("%0" + SEQ_PAD_WIDTH + "d", seq);
        return String.format("%s/%02d-%02d/%s", prefix, startYY, endYY, seqStr);
    }

    /**
     * Creates a new zero-initialized sequence row for a shop/prefix/year that
     * has not yet generated any invoices. The save happens within the current
     * {@code REQUIRES_NEW} transaction, so it is immediately visible to the
     * subsequent {@code saveAndFlush} call.
     */
    private InvoiceSequence createNewSequence(Long shopId, String prefix, short fiscalYear) {
        InvoiceSequence seq = new InvoiceSequence();
        seq.setShopId(shopId);
        seq.setPrefix(prefix);
        seq.setFiscalYear(fiscalYear);
        seq.setLastSeq(0L);
        log.info("Creating new invoice sequence for shopId={}, prefix={}, fiscalYear={}", shopId, prefix, fiscalYear);
        return repo.saveAndFlush(seq);
    }
}
