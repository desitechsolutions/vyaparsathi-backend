package com.desitech.vyaparsathi.accounting.service;

import com.desitech.vyaparsathi.accounting.entity.DebitNote;
import com.desitech.vyaparsathi.accounting.entity.DebitNoteApplication;
import com.desitech.vyaparsathi.accounting.enums.DebitNoteStatus;
import com.desitech.vyaparsathi.accounting.repository.DebitNoteApplicationRepository;
import com.desitech.vyaparsathi.accounting.repository.DebitNoteRepository;
import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Apply / reverse / cancel operations for debit notes. Each apply writes an
 * audit row in {@link DebitNoteApplication} and refreshes the parent DN's
 * {@code appliedAmount} + {@code status} atomically. Reversal marks the audit
 * row instead of deleting so history stays intact.
 */
@Service
public class DebitNoteApplicationService {

    private static final Logger log = LoggerFactory.getLogger(DebitNoteApplicationService.class);

    private final DebitNoteRepository debitNoteRepository;
    private final DebitNoteApplicationRepository applicationRepository;
    private final ShopRepository shopRepository;

    public DebitNoteApplicationService(DebitNoteRepository debitNoteRepository,
                                       DebitNoteApplicationRepository applicationRepository,
                                       ShopRepository shopRepository) {
        this.debitNoteRepository = debitNoteRepository;
        this.applicationRepository = applicationRepository;
        this.shopRepository = shopRepository;
    }

    @Transactional
    public DebitNoteApplication apply(Long debitNoteId, Long purchaseInvoiceId,
                                      Long supplierPaymentId, BigDecimal amount,
                                      String appliedBy, String note) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessValidationException("Apply amount must be positive.");
        }
        DebitNote dn = debitNoteRepository.findById(debitNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Debit note not found: " + debitNoteId));
        if (dn.getStatus() == DebitNoteStatus.CANCELLED) {
            throw new BusinessValidationException("Cannot apply against a cancelled debit note.");
        }
        BigDecimal outstanding = dn.getTotalAmount().subtract(dn.getAppliedAmount());
        if (amount.compareTo(outstanding) > 0) {
            throw new BusinessValidationException(
                    "Apply amount ₹" + amount + " exceeds outstanding ₹" + outstanding + " on this debit note.");
        }

        DebitNoteApplication app = new DebitNoteApplication();
        app.setDebitNoteId(debitNoteId);
        app.setPurchaseInvoiceId(purchaseInvoiceId);
        app.setSupplierPaymentId(supplierPaymentId);
        app.setAppliedAmount(amount);
        app.setAppliedAt(LocalDateTime.now());
        app.setAppliedBy(appliedBy);
        app.setNote(note);
        Long shopId = TenantUtils.getCurrentShopId();
        if (shopId != null) shopRepository.findById(shopId).ifPresent(app::setShop);
        applicationRepository.save(app);

        refreshDebitNoteRunningTotal(dn);
        log.info("Applied ₹{} of debit note {} (running total ₹{})",
                amount, dn.getDebitNoteNo(), dn.getAppliedAmount());
        return app;
    }

    @Transactional
    public DebitNoteApplication reverse(Long applicationId, String reversedBy, String note) {
        DebitNoteApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        if (app.isReversed()) {
            throw new BusinessValidationException("Application is already reversed.");
        }
        app.setReversed(true);
        app.setReversedAt(LocalDateTime.now());
        app.setReversedBy(reversedBy);
        if (note != null) {
            app.setNote((app.getNote() != null ? app.getNote() + " · " : "") + "Reversed: " + note);
        }
        applicationRepository.save(app);

        DebitNote dn = debitNoteRepository.findById(app.getDebitNoteId())
                .orElseThrow(() -> new ResourceNotFoundException("Debit note not found: " + app.getDebitNoteId()));
        refreshDebitNoteRunningTotal(dn);
        return app;
    }

    @Transactional
    public DebitNote cancelDebitNote(Long debitNoteId, String note) {
        DebitNote dn = debitNoteRepository.findById(debitNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Debit note not found: " + debitNoteId));
        if (dn.getStatus() == DebitNoteStatus.CANCELLED) return dn;
        if (dn.getAppliedAmount() != null && dn.getAppliedAmount().signum() > 0) {
            throw new BusinessValidationException(
                    "Debit note has ₹" + dn.getAppliedAmount() + " applied — reverse all applications before cancelling.");
        }
        dn.setStatus(DebitNoteStatus.CANCELLED);
        if (note != null) {
            dn.setNotes((dn.getNotes() != null ? dn.getNotes() + " · " : "") + "Cancelled: " + note);
        }
        return debitNoteRepository.save(dn);
    }

    @Transactional(readOnly = true)
    public List<DebitNoteApplication> listApplications(Long debitNoteId) {
        return applicationRepository.findByDebitNoteIdOrderByAppliedAtDesc(debitNoteId);
    }

    private void refreshDebitNoteRunningTotal(DebitNote dn) {
        BigDecimal running = applicationRepository.sumAppliedForDebitNote(dn.getId());
        if (running == null) running = BigDecimal.ZERO;
        dn.setAppliedAmount(running);
        if (dn.getStatus() != DebitNoteStatus.CANCELLED) {
            int cmp = running.compareTo(dn.getTotalAmount());
            if (running.signum() == 0)          dn.setStatus(DebitNoteStatus.ISSUED);
            else if (cmp < 0)                   dn.setStatus(DebitNoteStatus.PARTIALLY_APPLIED);
            else                                dn.setStatus(DebitNoteStatus.FULLY_APPLIED);
        }
        debitNoteRepository.save(dn);
    }
}
