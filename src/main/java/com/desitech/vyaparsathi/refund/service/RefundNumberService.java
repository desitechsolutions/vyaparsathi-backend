package com.desitech.vyaparsathi.refund.service;

import com.desitech.vyaparsathi.refund.entity.RefundSequence;
import com.desitech.vyaparsathi.refund.repository.RefundSequenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Atomic per-shop refund number generator. Format: {@code REF/YY-YY/NNNNN}.
 */
@Service
public class RefundNumberService {

    private static final Logger log = LoggerFactory.getLogger(RefundNumberService.class);
    private static final int SEQ_PAD_WIDTH = 5;
    private static final String DEFAULT_PREFIX = "REF";

    private final RefundSequenceRepository repo;

    public RefundNumberService(RefundSequenceRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextRefundNumber(Long shopId, LocalDate date) {
        short fiscalYear = fiscalYearOf(date);
        String prefix = DEFAULT_PREFIX;

        RefundSequence seq = repo.findByShopIdAndPrefixAndFiscalYearForUpdate(shopId, prefix, fiscalYear)
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

    private RefundSequence createNewSequence(Long shopId, String prefix, short fiscalYear) {
        RefundSequence seq = new RefundSequence();
        seq.setShopId(shopId);
        seq.setPrefix(prefix);
        seq.setFiscalYear(fiscalYear);
        seq.setLastSeq(0L);
        log.info("Creating new refund sequence for shopId={}, prefix={}, fiscalYear={}", shopId, prefix, fiscalYear);
        return repo.saveAndFlush(seq);
    }
}
