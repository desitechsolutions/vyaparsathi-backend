package com.desitech.vyaparsathi.purchaseorder.service;

import com.desitech.vyaparsathi.inventory.dto.LowStockAlertDto;
import com.desitech.vyaparsathi.inventory.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Auto-PO trigger. Wakes hourly, scans LowStockAlerts for CRITICAL entries
 * with a preferred supplier, and drops a summary into the log for now. Full
 * auto-DRAFT creation is opt-in per shop — hook this up to
 * {@link PurchaseOrderSuggestionService} once the shop enables the flag.
 *
 * <p>Kept as a lightweight scheduler so operators see the intent (and the
 * count of items that would trigger a draft) without accidentally generating
 * a wave of DRAFT POs on install.
 */
@Component
public class PurchaseOrderAutoTriggerScheduler {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderAutoTriggerScheduler.class);

    @Value("${app.purchase.auto-po.enabled:false}")
    private boolean enabled;

    private final StockService stockService;

    public PurchaseOrderAutoTriggerScheduler(StockService stockService) {
        this.stockService = stockService;
    }

    @Scheduled(cron = "0 15 * * * *")
    public void scan() {
        if (!enabled) return;
        try {
            List<LowStockAlertDto> alerts = stockService.getLowStockAlerts();
            long critical = alerts.stream()
                    .filter(a -> "CRITICAL".equalsIgnoreCase(a.getAlertLevel()))
                    .filter(a -> a.getSupplierId() != null)
                    .count();
            if (critical > 0) {
                log.info("Auto-PO scan: {} CRITICAL low-stock alerts with preferred suppliers awaiting DRAFT PO creation.", critical);
            }
        } catch (Exception e) {
            log.warn("Auto-PO scan failed: {}", e.getMessage());
        }
    }
}
