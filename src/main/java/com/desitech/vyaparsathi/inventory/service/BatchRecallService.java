package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.inventory.entity.BatchRecall;
import com.desitech.vyaparsathi.inventory.entity.BatchRecallImpact;
import com.desitech.vyaparsathi.inventory.entity.StockMovement;
import com.desitech.vyaparsathi.inventory.enums.StockMovementType;
import com.desitech.vyaparsathi.inventory.repository.BatchRecallRepository;
import com.desitech.vyaparsathi.inventory.repository.StockMovementRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Batch recall / trace-out. Opening a recall scans historical movements for
 * the batch, emits impact rows for every outbound movement (sale/transfer/RTV),
 * and quarantines any remaining stock via an ADJUST(-remaining) so the batch
 * can't be sold accidentally.
 */
@Service
public class BatchRecallService {

    private static final Logger log = LoggerFactory.getLogger(BatchRecallService.class);

    private final BatchRecallRepository repository;
    private final StockMovementRepository stockMovementRepository;
    private final ShopRepository shopRepository;

    public BatchRecallService(BatchRecallRepository repository,
                              StockMovementRepository stockMovementRepository,
                              ShopRepository shopRepository) {
        this.repository = repository;
        this.stockMovementRepository = stockMovementRepository;
        this.shopRepository = shopRepository;
    }

    @Transactional
    public BatchRecall open(String batchNumber, Long itemVariantId, String reason,
                            String initiatedBy, boolean notifySupplier, boolean notifyCustomers) {
        if (batchNumber == null || batchNumber.isBlank()) {
            throw new BusinessValidationException("Batch number is required.");
        }
        BatchRecall r = new BatchRecall();
        r.setBatchNumber(batchNumber);
        r.setItemVariantId(itemVariantId);
        r.setReason(reason);
        r.setStatus("OPEN");
        r.setInitiatedBy(initiatedBy);
        r.setInitiatedAt(LocalDateTime.now());
        r.setSupplierNotified(notifySupplier);
        r.setCustomersNotified(notifyCustomers);
        Long shopId = TenantUtils.getCurrentShopId();
        if (shopId != null) shopRepository.findById(shopId).ifPresent(r::setShop);
        r = repository.save(r);

        // Fan out impacts from all outbound movements on this batch.
        List<StockMovement> movements = stockMovementRepository.findAll().stream()
                .filter(m -> batchNumber.equals(m.getBatch()))
                .toList();
        BigDecimal remaining = BigDecimal.ZERO;
        for (StockMovement m : movements) {
            BigDecimal qty = m.getQuantity() != null ? m.getQuantity() : BigDecimal.ZERO;
            if (m.getMovementType() == StockMovementType.ADD) remaining = remaining.add(qty);
            else remaining = remaining.add(qty);
            if (m.getMovementType() == StockMovementType.DEDUCT) {
                BatchRecallImpact impact = new BatchRecallImpact();
                impact.setBatchRecall(r);
                impact.setReferenceType(m.getReference() != null ? m.getReference().split(":")[0] : "MOVEMENT");
                impact.setReferenceNumber(m.getReference());
                impact.setQuantity(qty.abs());
                impact.setOutcome("NOTIFIED");
                impact.setLoggedAt(LocalDateTime.now());
                r.getImpacts().add(impact);
            }
        }

        // Quarantine any leftover stock — ADJUST with a negative delta = remaining
        // and a QUARANTINE reason so it can't be sold.
        if (remaining.signum() > 0 && movements.size() > 0) {
            StockMovement quarantine = new StockMovement();
            StockMovement any = movements.get(0);
            quarantine.setItemVariant(any.getItemVariant());
            quarantine.setMovementType(StockMovementType.ADJUST);
            quarantine.setQuantity(remaining.negate());
            quarantine.setCostPerUnit(any.getCostPerUnit());
            quarantine.setBatch(batchNumber);
            quarantine.setReason("BATCH_RECALL:" + r.getId() + " · " + reason);
            quarantine.setReference("BATCH_RECALL:" + r.getId());
            stockMovementRepository.save(quarantine);
        }

        BatchRecall saved = repository.save(r);
        log.info("Batch recall {} opened — {} impacts, {} units quarantined",
                saved.getId(), saved.getImpacts().size(), remaining);
        return saved;
    }

    @Transactional
    public BatchRecall close(Long id, String note) {
        BatchRecall r = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recall not found: " + id));
        r.setStatus("CLOSED");
        r.setClosedAt(LocalDateTime.now());
        if (note != null) r.setNotes((r.getNotes() != null ? r.getNotes() + " · " : "") + note);
        return repository.save(r);
    }

    @Transactional
    public BatchRecall updateImpactOutcome(Long recallId, Long impactId, String outcome) {
        BatchRecall r = repository.findById(recallId)
                .orElseThrow(() -> new ResourceNotFoundException("Recall not found: " + recallId));
        for (BatchRecallImpact impact : r.getImpacts()) {
            if (impact.getId().equals(impactId)) {
                impact.setOutcome(outcome);
                return repository.save(r);
            }
        }
        throw new ResourceNotFoundException("Impact not found: " + impactId);
    }

    @Transactional(readOnly = true)
    public List<BatchRecall> listAll() {
        return repository.findAll();
    }
}
