package com.desitech.vyaparsathi.accounting.service;

import com.desitech.vyaparsathi.accounting.entity.CreditNote;
import com.desitech.vyaparsathi.accounting.entity.CreditNoteAllocation;
import com.desitech.vyaparsathi.accounting.enums.CreditNoteStatus;
import com.desitech.vyaparsathi.accounting.repository.CreditNoteAllocationRepository;
import com.desitech.vyaparsathi.accounting.repository.CreditNoteRepository;
import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Enterprise settlement engine for Credit Notes. Every apply-to-invoice and
 * cash/bank refund event lands as an audit row in {@code credit_note_allocation};
 * the running total on {@code credit_notes.applied_amount} is derived from the
 * sum of non-reversed rows. Mirrors {@link DebitNoteApplicationService}.
 *
 * <p>Status transitions are enforced atomically inside each transaction:
 * <ul>
 *   <li>Applied &lt; Total → {@code PARTIALLY_APPLIED}</li>
 *   <li>Applied ≥ Total → {@code FULLY_APPLIED}</li>
 *   <li>REFUND allocation additionally flips {@code refunded=true} so further
 *       applications are rejected.</li>
 * </ul>
 */
@Service
@Transactional
public class CreditNoteAllocationService {

    private final CreditNoteRepository creditRepo;
    private final CreditNoteAllocationRepository allocationRepo;

    public CreditNoteAllocationService(CreditNoteRepository creditRepo,
                                       CreditNoteAllocationRepository allocationRepo) {
        this.creditRepo = creditRepo;
        this.allocationRepo = allocationRepo;
    }

    /** Apply {@code amount} of {@code creditNoteId} against sale {@code saleId}. */
    public CreditNoteAllocation applyToInvoice(Long creditNoteId, Long saleId,
                                               BigDecimal amount, String user, String note) {
        if (saleId == null) {
            throw new BusinessValidationException("saleId is required for INVOICE allocation");
        }
        return record(creditNoteId, CreditNoteAllocation.Type.INVOICE, saleId,
                null, null, amount, user, note);
    }

    /** Record a cash/bank refund payout for the remaining credit. */
    public CreditNoteAllocation recordRefund(Long creditNoteId, BigDecimal amount,
                                             String paymentMode, String paymentReference,
                                             String user, String note) {
        return record(creditNoteId, CreditNoteAllocation.Type.REFUND, null,
                paymentMode, paymentReference, amount, user, note);
    }

    /** Reverse a prior allocation. Restores the credit note's outstanding balance. */
    public CreditNoteAllocation reverse(Long allocationId, String user, String note) {
        CreditNoteAllocation a = allocationRepo.findById(allocationId)
                .orElseThrow(() -> new EntityNotFoundAppException("CreditNoteAllocation", allocationId));
        if (Boolean.TRUE.equals(a.getReversed())) {
            throw new BusinessValidationException("Allocation already reversed");
        }
        a.setReversed(Boolean.TRUE);
        a.setReversedAt(LocalDateTime.now());
        a.setReversedBy(user);
        String noteText = a.getNote() == null ? "" : (a.getNote() + " · ");
        a.setNote(noteText + "Reversed: " + (note == null ? "" : note));
        allocationRepo.save(a);

        CreditNote cn = a.getCreditNote();
        BigDecimal applied = cn.getAppliedAmount() == null ? BigDecimal.ZERO : cn.getAppliedAmount();
        cn.setAppliedAmount(applied.subtract(a.getAllocatedAmount()).max(BigDecimal.ZERO));
        if (a.getAllocationType() == CreditNoteAllocation.Type.REFUND) {
            cn.setRefunded(Boolean.FALSE);
        }
        recalculateStatus(cn);
        creditRepo.save(cn);
        return a;
    }

    @Transactional(readOnly = true)
    public List<CreditNoteAllocation> listAllocations(Long creditNoteId) {
        return allocationRepo.findByCreditNoteIdOrderByAllocatedAtDesc(creditNoteId);
    }

    /** Flip status to CANCELLED. Rejects when any allocation exists so the
     *  audit trail stays consistent — reverse first, then cancel. */
    public CreditNote cancel(Long creditNoteId, String note) {
        CreditNote cn = creditRepo.findById(creditNoteId)
                .orElseThrow(() -> new EntityNotFoundAppException("Credit Note", creditNoteId));
        if (cn.getStatus() == CreditNoteStatus.CANCELLED) return cn;
        BigDecimal applied = cn.getAppliedAmount() == null ? BigDecimal.ZERO : cn.getAppliedAmount();
        if (applied.signum() > 0) {
            throw new BusinessValidationException(
                    "Cannot cancel a credit note with active allocations — reverse them first.");
        }
        cn.setStatus(CreditNoteStatus.CANCELLED);
        if (note != null && !note.isBlank()) {
            String existing = cn.getNotes() == null ? "" : (cn.getNotes() + " · ");
            cn.setNotes(existing + "Cancelled: " + note);
        }
        return creditRepo.save(cn);
    }

    // ── shared record path ───────────────────────────────────────────

    private CreditNoteAllocation record(Long creditNoteId, CreditNoteAllocation.Type type,
                                        Long saleId, String mode, String ref,
                                        BigDecimal amount, String user, String note) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessValidationException("Allocation amount must be positive");
        }
        CreditNote cn = creditRepo.findById(creditNoteId)
                .orElseThrow(() -> new EntityNotFoundAppException("Credit Note", creditNoteId));
        if (cn.getStatus() == CreditNoteStatus.CANCELLED) {
            throw new BusinessValidationException("Cannot allocate a cancelled credit note");
        }
        if (Boolean.TRUE.equals(cn.getRefunded())) {
            throw new BusinessValidationException("Credit note already refunded — no remaining balance");
        }
        BigDecimal applied = cn.getAppliedAmount() == null ? BigDecimal.ZERO : cn.getAppliedAmount();
        BigDecimal total   = cn.getTotalAmount()   == null ? BigDecimal.ZERO : cn.getTotalAmount();
        BigDecimal remaining = total.subtract(applied);
        if (amount.compareTo(remaining) > 0) {
            throw new BusinessValidationException(
                    "Allocation amount " + amount + " exceeds remaining balance " + remaining);
        }

        CreditNoteAllocation row = new CreditNoteAllocation();
        row.setCreditNote(cn);
        row.setAllocationType(type);
        row.setSaleId(saleId);
        row.setPaymentMode(mode);
        row.setPaymentReference(ref);
        row.setAllocatedAmount(amount);
        row.setAllocatedBy(user == null ? "system" : user);
        row.setNote(note);
        row.setAllocatedAt(LocalDateTime.now());
        allocationRepo.save(row);

        cn.setAppliedAmount(applied.add(amount));
        if (type == CreditNoteAllocation.Type.REFUND && cn.getAppliedAmount().compareTo(total) >= 0) {
            cn.setRefunded(Boolean.TRUE);
        }
        recalculateStatus(cn);
        creditRepo.save(cn);
        return row;
    }

    private void recalculateStatus(CreditNote cn) {
        BigDecimal applied = cn.getAppliedAmount() == null ? BigDecimal.ZERO : cn.getAppliedAmount();
        BigDecimal total   = cn.getTotalAmount()   == null ? BigDecimal.ZERO : cn.getTotalAmount();
        if (cn.getStatus() == CreditNoteStatus.CANCELLED) return;
        if (applied.signum() <= 0) {
            cn.setStatus(CreditNoteStatus.ISSUED);
        } else if (applied.compareTo(total) < 0) {
            cn.setStatus(CreditNoteStatus.PARTIALLY_APPLIED);
        } else {
            cn.setStatus(CreditNoteStatus.FULLY_APPLIED);
        }
    }
}
