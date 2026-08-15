package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.entity.ReceivingApproval;
import com.desitech.vyaparsathi.receiving.entity.ReceivingItem;
import com.desitech.vyaparsathi.receiving.repository.ReceivingApprovalRepository;
import com.desitech.vyaparsathi.receiving.repository.ReceivingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Multi-level GRN approval workflow.
 *
 * <p>Threshold model:
 * <ul>
 *   <li>Total ≤ {@code app.receiving.approval.threshold-l1}: L1 only (any manager)</li>
 *   <li>Total > threshold-l1: L1 required, then L2 required</li>
 * </ul>
 * The L2 approver role defaults to OWNER; overridable via config. Approval
 * requests are seeded lazily on the first fetch so historical GRNs don't need
 * a backfill migration.
 */
@Service
public class ReceivingApprovalService {

    @Value("${app.receiving.approval.threshold-l1:50000}")
    private BigDecimal thresholdL1;

    @Value("${app.receiving.approval.l1-role:ADMIN}")
    private String l1Role;

    @Value("${app.receiving.approval.l2-role:OWNER}")
    private String l2Role;

    private final ReceivingApprovalRepository approvalRepository;
    private final ReceivingRepository receivingRepository;

    public ReceivingApprovalService(ReceivingApprovalRepository approvalRepository,
                                    ReceivingRepository receivingRepository) {
        this.approvalRepository = approvalRepository;
        this.receivingRepository = receivingRepository;
    }

    private BigDecimal receivingTotal(Receiving r) {
        BigDecimal total = BigDecimal.ZERO;
        if (r.getItems() == null) return total;
        for (ReceivingItem it : r.getItems()) {
            int qty = it.getAcceptedQty();
            BigDecimal cost = Optional.ofNullable(it.getUnitCost())
                    .orElse(it.getPurchaseOrderItem() != null
                            ? Optional.ofNullable(it.getPurchaseOrderItem().getUnitCost()).orElse(BigDecimal.ZERO)
                            : BigDecimal.ZERO);
            total = total.add(cost.multiply(BigDecimal.valueOf(qty)));
        }
        return total;
    }

    @Transactional
    public List<ReceivingApproval> getOrSeedApprovals(Long receivingId) {
        Receiving r = receivingRepository.findById(receivingId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiving not found: " + receivingId));
        List<ReceivingApproval> existing = approvalRepository.findByReceivingIdOrderByLevelAsc(receivingId);
        if (!existing.isEmpty()) return existing;

        BigDecimal total = receivingTotal(r);

        ReceivingApproval l1 = new ReceivingApproval();
        l1.setReceivingId(r.getId());
        l1.setShop(r.getShop());
        l1.setLevel((short) 1);
        l1.setApproverRole(l1Role);
        l1.setStatus("PENDING");
        l1.setThresholdMin(BigDecimal.ZERO);
        approvalRepository.save(l1);

        if (total.compareTo(thresholdL1) > 0) {
            ReceivingApproval l2 = new ReceivingApproval();
            l2.setReceivingId(r.getId());
            l2.setShop(r.getShop());
            l2.setLevel((short) 2);
            l2.setApproverRole(l2Role);
            l2.setStatus("PENDING");
            l2.setThresholdMin(thresholdL1);
            approvalRepository.save(l2);
        }
        return approvalRepository.findByReceivingIdOrderByLevelAsc(receivingId);
    }

    @Transactional
    public ReceivingApproval approve(Long approvalId, Long userId, String note) {
        ReceivingApproval a = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval step not found: " + approvalId));
        if (!"PENDING".equals(a.getStatus())) {
            throw new BusinessValidationException("Approval step is already " + a.getStatus());
        }
        // Enforce sequential approval — L2 waits until L1 completes.
        List<ReceivingApproval> steps = approvalRepository.findByReceivingIdOrderByLevelAsc(a.getReceivingId());
        for (ReceivingApproval prior : steps) {
            if (prior.getLevel() < a.getLevel() && !"APPROVED".equals(prior.getStatus())) {
                throw new BusinessValidationException("Prior approval step L" + prior.getLevel() + " is not complete.");
            }
        }
        a.setStatus("APPROVED");
        a.setApproverId(userId);
        a.setApprovedAt(LocalDateTime.now());
        a.setNote(note);
        return approvalRepository.save(a);
    }
}
