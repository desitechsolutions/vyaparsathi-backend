package com.desitech.vyaparsathi.accounting.service;

import com.desitech.vyaparsathi.accounting.entity.CreditNoteSequence;
import com.desitech.vyaparsathi.accounting.repository.CreditNoteSequenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Atomic per-shop credit note number generator.
 * Format: {@code CR/YY-YY/NNNNN}. Mirrors {@code InvoiceNumberService}.
 */
@Service
public class CreditNoteNumberService {

    private static final Logger log = LoggerFactory.getLogger(CreditNoteNumberService.class);
    private static final int SEQ_PAD_WIDTH = 5;
    private static final String DEFAULT_PREFIX = "CR";

    private final CreditNoteSequenceRepository repo;

    public CreditNoteNumberService(CreditNoteSequenceRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextCreditNoteNumber(Long shopId, LocalDate date) {
        short fiscalYear = fiscalYearOf(date);
        String prefix = DEFAULT_PREFIX;

        CreditNoteSequence seq = repo.findByShopIdAndPrefixAndFiscalYearForUpdate(shopId, prefix, fiscalYear)
                .orElseGet(() -> createNewSequence(shopId, prefix, fiscalYear));

        long nextSeq = seq.getLastSeq() + 1;
        seq.setLastSeq(nextSeq);
        repo.saveAndFlush(seq);

        return format(prefix, fiscalYear, nextSeq);
    }

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

    private CreditNoteSequence createNewSequence(Long shopId, String prefix, short fiscalYear) {
        CreditNoteSequence seq = new CreditNoteSequence();
        seq.setShopId(shopId);
        seq.setPrefix(prefix);
        seq.setFiscalYear(fiscalYear);
        seq.setLastSeq(0L);
        log.info("Creating new credit-note sequence for shopId={}, prefix={}, fiscalYear={}", shopId, prefix, fiscalYear);
        return repo.saveAndFlush(seq);
    }
}
