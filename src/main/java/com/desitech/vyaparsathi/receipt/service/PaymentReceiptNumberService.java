package com.desitech.vyaparsathi.receipt.service;

import com.desitech.vyaparsathi.receipt.entity.PaymentReceiptSequence;
import com.desitech.vyaparsathi.receipt.repository.PaymentReceiptSequenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Atomic, race-condition-safe payment receipt number generator.
 *
 * <p>Mirrors {@code InvoiceNumberService} exactly — same pessimistic-lock pattern,
 * same fiscal-year semantics, same {@code REQUIRES_NEW} isolation to release the
 * lock as soon as the counter is incremented. Format: {@code PREFIX/YY-YY/NNNNN}
 * (e.g. {@code RCP/25-26/00042}).
 */
@Service
public class PaymentReceiptNumberService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReceiptNumberService.class);
    private static final int SEQ_PAD_WIDTH = 5;
    private static final String DEFAULT_PREFIX = "RCP";

    private final PaymentReceiptSequenceRepository repo;

    public PaymentReceiptNumberService(PaymentReceiptSequenceRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextReceiptNumber(Long shopId, LocalDate date) {
        short fiscalYear = fiscalYearOf(date);
        String prefix = DEFAULT_PREFIX;

        PaymentReceiptSequence seq = repo.findByShopIdAndPrefixAndFiscalYearForUpdate(shopId, prefix, fiscalYear)
                .orElseGet(() -> createNewSequence(shopId, prefix, fiscalYear));

        long nextSeq = seq.getLastSeq() + 1;
        seq.setLastSeq(nextSeq);
        repo.saveAndFlush(seq);

        String receiptNo = format(prefix, fiscalYear, nextSeq);
        log.debug("Generated receipt number {} for shopId={}", receiptNo, shopId);
        return receiptNo;
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

    private PaymentReceiptSequence createNewSequence(Long shopId, String prefix, short fiscalYear) {
        PaymentReceiptSequence seq = new PaymentReceiptSequence();
        seq.setShopId(shopId);
        seq.setPrefix(prefix);
        seq.setFiscalYear(fiscalYear);
        seq.setLastSeq(0L);
        log.info("Creating new receipt sequence for shopId={}, prefix={}, fiscalYear={}", shopId, prefix, fiscalYear);
        return repo.saveAndFlush(seq);
    }
}
