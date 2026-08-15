package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.receiving.entity.GrnSequence;
import com.desitech.vyaparsathi.receiving.repository.GrnSequenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Atomic per-shop GRN number generator. Format: {@code GRN/YY-YY/NNNNN}.
 * Mirrors {@link com.desitech.vyaparsathi.purchaseorder.service.PurchaseOrderNumberService}
 * — same pessimistic-lock pattern per (shop, prefix, fiscal-year), same
 * REQUIRES_NEW transaction so the sequence commit is independent of the
 * caller. Replaces the {@code "GR-" + timestamp} scheme that was
 * collision-prone and not human-readable.
 */
@Service
public class GrnNumberService {

    private static final Logger log = LoggerFactory.getLogger(GrnNumberService.class);
    private static final int SEQ_PAD_WIDTH = 5;
    private static final String DEFAULT_PREFIX = "GRN";

    private final GrnSequenceRepository repo;

    public GrnNumberService(GrnSequenceRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextGrnNumber(Long shopId, LocalDate date) {
        short fiscalYear = fiscalYearOf(date);
        String prefix = DEFAULT_PREFIX;

        GrnSequence seq = repo.findByShopIdAndPrefixAndFiscalYearForUpdate(shopId, prefix, fiscalYear)
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

    private GrnSequence createNewSequence(Long shopId, String prefix, short fiscalYear) {
        GrnSequence seq = new GrnSequence();
        seq.setShopId(shopId);
        seq.setPrefix(prefix);
        seq.setFiscalYear(fiscalYear);
        seq.setLastSeq(0L);
        log.info("Creating new GRN sequence for shopId={}, prefix={}, fiscalYear={}", shopId, prefix, fiscalYear);
        return repo.saveAndFlush(seq);
    }
}