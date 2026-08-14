package com.desitech.vyaparsathi.purchaseorder.service;

import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderSequence;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderSequenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Atomic per-shop purchase-order number generator. Format: {@code PO/YY-YY/NNNNN}.
 * Mirrors {@link com.desitech.vyaparsathi.quotation.service.QuotationNumberService}
 * — same pessimistic-lock pattern per (shop, prefix, fiscal-year) so two
 * concurrent creates never collide on the same number.
 *
 * <p>Runs in a REQUIRES_NEW transaction so the sequence commit is independent
 * of the caller — if the outer PO create rolls back after we've reserved a
 * number, we accept the small numbering gap rather than risk two POs receiving
 * the same reserved number.
 */
@Service
public class PurchaseOrderNumberService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderNumberService.class);
    private static final int SEQ_PAD_WIDTH = 5;
    private static final String DEFAULT_PREFIX = "PO";

    private final PurchaseOrderSequenceRepository repo;

    public PurchaseOrderNumberService(PurchaseOrderSequenceRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextPurchaseOrderNumber(Long shopId, LocalDate date) {
        short fiscalYear = fiscalYearOf(date);
        String prefix = DEFAULT_PREFIX;

        PurchaseOrderSequence seq = repo.findByShopIdAndPrefixAndFiscalYearForUpdate(shopId, prefix, fiscalYear)
                .orElseGet(() -> createNewSequence(shopId, prefix, fiscalYear));

        long nextSeq = seq.getLastSeq() + 1;
        seq.setLastSeq(nextSeq);
        repo.saveAndFlush(seq);

        return format(prefix, fiscalYear, nextSeq);
    }

    /** Indian FY: Apr–Mar. Jan–Mar counts as the prior FY. */
    private short fiscalYearOf(LocalDate date) {
        int month = date.getMonthValue();
        int year = date.getYear();
        return (short) (month < 4 ? year - 1 : year);
    }

    private String format(String prefix, short fiscalYear, long seq) {
        int startYY = fiscalYear % 100;
        int endYY = (fiscalYear + 1) % 100;
        String seqStr = String.format("%0" + SEQ_PAD_WIDTH + "d", seq);
        return String.format("%s/%02d-%02d/%s", prefix, startYY, endYY, seqStr);
    }

    private PurchaseOrderSequence createNewSequence(Long shopId, String prefix, short fiscalYear) {
        PurchaseOrderSequence seq = new PurchaseOrderSequence();
        seq.setShopId(shopId);
        seq.setPrefix(prefix);
        seq.setFiscalYear(fiscalYear);
        seq.setLastSeq(0L);
        log.info("Creating new PO sequence for shopId={}, prefix={}, fiscalYear={}", shopId, prefix, fiscalYear);
        return repo.saveAndFlush(seq);
    }
}