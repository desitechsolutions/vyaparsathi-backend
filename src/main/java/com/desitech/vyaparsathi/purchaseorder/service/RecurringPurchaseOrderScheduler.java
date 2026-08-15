package com.desitech.vyaparsathi.purchaseorder.service;

import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Recurrence engine for blanket / standing POs. Every hour scans POs whose
 * {@code recurringEnabled} is on and {@code recurringNextAt} has passed;
 * duplicates them via {@link PurchaseOrderService#duplicatePurchaseOrder},
 * pins the parent id, and rolls the next-fire timestamp forward by the
 * configured frequency.
 */
@Component
public class RecurringPurchaseOrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringPurchaseOrderScheduler.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderService purchaseOrderService;

    public RecurringPurchaseOrderScheduler(PurchaseOrderRepository purchaseOrderRepository,
                                           PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderService = purchaseOrderService;
    }

    @Scheduled(cron = "0 30 * * * *")
    @Transactional
    public void fireDuePOs() {
        LocalDateTime now = LocalDateTime.now();
        List<PurchaseOrder> due = purchaseOrderRepository.findAll().stream()
                .filter(PurchaseOrder::isRecurringEnabled)
                .filter(p -> p.getRecurringNextAt() != null && !p.getRecurringNextAt().isAfter(now))
                .toList();
        for (PurchaseOrder parent : due) {
            try {
                var duplicate = purchaseOrderService.duplicatePurchaseOrder(parent.getId());
                LocalDateTime next = nextFire(now, parent.getRecurringFrequency());
                parent.setRecurringNextAt(next);
                purchaseOrderRepository.save(parent);
                log.info("Recurring PO {} → new DRAFT {}; next fire at {}",
                        parent.getPoNumber(), duplicate.getPoNumber(), next);
            } catch (Exception e) {
                log.warn("Recurring PO fire failed for {}: {}", parent.getPoNumber(), e.getMessage());
            }
        }
    }

    private LocalDateTime nextFire(LocalDateTime now, String frequency) {
        if (frequency == null) return now.plusMonths(1);
        return switch (frequency.toUpperCase()) {
            case "DAILY"   -> now.plusDays(1);
            case "WEEKLY"  -> now.plusWeeks(1);
            case "BIWEEKLY" -> now.plusWeeks(2);
            case "MONTHLY" -> now.plusMonths(1);
            case "QUARTERLY" -> now.plusMonths(3);
            case "YEARLY"  -> now.plusYears(1);
            default -> now.plusMonths(1);
        };
    }
}
