package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.inventory.dto.CurrentStockDto;
import com.desitech.vyaparsathi.inventory.entity.CycleCount;
import com.desitech.vyaparsathi.inventory.entity.CycleCountLine;
import com.desitech.vyaparsathi.inventory.entity.StockMovement;
import com.desitech.vyaparsathi.inventory.enums.StockMovementType;
import com.desitech.vyaparsathi.inventory.repository.CycleCountRepository;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
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
 * Cycle count / stocktake workflow. On plan we snapshot every variant's
 * current stock into count lines. On commit we compute variance and emit
 * ADJUST movements for any non-zero delta so the physical count becomes the
 * new system-of-record.
 */
@Service
public class CycleCountService {

    private static final Logger log = LoggerFactory.getLogger(CycleCountService.class);

    private final CycleCountRepository repository;
    private final StockService stockService;
    private final StockMovementRepository stockMovementRepository;
    private final ItemVariantRepository itemVariantRepository;
    private final ShopRepository shopRepository;

    public CycleCountService(CycleCountRepository repository,
                             StockService stockService,
                             StockMovementRepository stockMovementRepository,
                             ItemVariantRepository itemVariantRepository,
                             ShopRepository shopRepository) {
        this.repository = repository;
        this.stockService = stockService;
        this.stockMovementRepository = stockMovementRepository;
        this.itemVariantRepository = itemVariantRepository;
        this.shopRepository = shopRepository;
    }

    @Transactional
    public CycleCount plan(String scope, String initiatedBy, String notes) {
        CycleCount cc = new CycleCount();
        cc.setCountNumber("CC-" + System.currentTimeMillis());
        cc.setScope(scope != null ? scope : "FULL");
        cc.setStatus("PLANNED");
        cc.setInitiatedBy(initiatedBy);
        cc.setNotes(notes);
        Long shopId = TenantUtils.getCurrentShopId();
        if (shopId != null) shopRepository.findById(shopId).ifPresent(cc::setShop);

        // Snapshot current stock as system-of-record for reconciliation.
        for (CurrentStockDto row : stockService.getCurrentStock()) {
            CycleCountLine line = new CycleCountLine();
            line.setCycleCount(cc);
            line.setItemVariantId(row.getItemVariantId());
            line.setBatchNumber(row.getBatchNumber());
            line.setSystemQty(row.getTotalQuantity() != null ? row.getTotalQuantity() : BigDecimal.ZERO);
            cc.getLines().add(line);
        }
        return repository.save(cc);
    }

    @Transactional
    public CycleCount recordCount(Long cycleCountId, Long lineId, BigDecimal countedQty,
                                  String countedBy, String reason) {
        CycleCount cc = repository.findById(cycleCountId)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle count not found: " + cycleCountId));
        if ("COMPLETED".equals(cc.getStatus()) || "CANCELLED".equals(cc.getStatus())) {
            throw new BusinessValidationException("Cycle count is already " + cc.getStatus());
        }
        for (CycleCountLine line : cc.getLines()) {
            if (line.getId().equals(lineId)) {
                line.setCountedQty(countedQty);
                line.setVarianceQty(countedQty.subtract(line.getSystemQty()));
                line.setCountedBy(countedBy);
                line.setCountedAt(LocalDateTime.now());
                line.setReason(reason);
                if ("PLANNED".equals(cc.getStatus())) {
                    cc.setStatus("IN_PROGRESS");
                    cc.setStartedAt(LocalDateTime.now());
                }
                return repository.save(cc);
            }
        }
        throw new ResourceNotFoundException("Line not found: " + lineId);
    }

    @Transactional
    public CycleCount commit(Long cycleCountId) {
        CycleCount cc = repository.findById(cycleCountId)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle count not found: " + cycleCountId));
        if (!"IN_PROGRESS".equals(cc.getStatus()) && !"PLANNED".equals(cc.getStatus())) {
            throw new BusinessValidationException("Cannot commit a " + cc.getStatus() + " count.");
        }
        int variances = 0;
        for (CycleCountLine line : cc.getLines()) {
            if (line.getCountedQty() == null) continue;
            BigDecimal delta = line.getVarianceQty();
            if (delta == null || delta.signum() == 0) continue;
            variances++;
            StockMovement mv = new StockMovement();
            mv.setItemVariant(itemVariantRepository.findById(line.getItemVariantId()).orElse(null));
            mv.setMovementType(StockMovementType.ADJUST);
            mv.setQuantity(delta);
            mv.setReason("Cycle count " + cc.getCountNumber()
                    + (line.getReason() != null ? " · " + line.getReason() : ""));
            mv.setReference("CYCLE_COUNT:" + cc.getId());
            mv.setBatch(line.getBatchNumber());
            stockMovementRepository.save(mv);
        }
        cc.setStatus("COMPLETED");
        cc.setCompletedAt(LocalDateTime.now());
        log.info("Cycle count {} committed — {} variance adjustments applied", cc.getCountNumber(), variances);
        return repository.save(cc);
    }

    @Transactional
    public CycleCount cancel(Long cycleCountId, String reason) {
        CycleCount cc = repository.findById(cycleCountId)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle count not found: " + cycleCountId));
        if ("COMPLETED".equals(cc.getStatus())) {
            throw new BusinessValidationException("Cannot cancel a completed cycle count.");
        }
        cc.setStatus("CANCELLED");
        cc.setNotes((cc.getNotes() != null ? cc.getNotes() + " · " : "") + "Cancelled: " + reason);
        return repository.save(cc);
    }

    @Transactional(readOnly = true)
    public List<CycleCount> listAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public CycleCount get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle count not found: " + id));
    }
}
