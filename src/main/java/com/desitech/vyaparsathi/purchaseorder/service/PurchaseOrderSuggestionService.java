package com.desitech.vyaparsathi.purchaseorder.service;

import com.desitech.vyaparsathi.inventory.dto.LowStockAlertDto;
import com.desitech.vyaparsathi.inventory.service.StockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Demand-driven PO suggestion. Groups current low-stock alerts by preferred
 * supplier and emits one suggested PO per supplier with the item list already
 * seeded. The FE consumes each group into a pre-filled PO create wizard so
 * the buyer only reviews + submits.
 */
@Service
public class PurchaseOrderSuggestionService {

    private final StockService stockService;

    public PurchaseOrderSuggestionService(StockService stockService) {
        this.stockService = stockService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> suggestFromLowStock() {
        List<LowStockAlertDto> alerts = stockService.getLowStockAlerts();

        Map<Long, List<LowStockAlertDto>> bySupplier = alerts.stream()
                .filter(a -> a.getSupplierId() != null)
                .collect(Collectors.groupingBy(LowStockAlertDto::getSupplierId));

        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (Map.Entry<Long, List<LowStockAlertDto>> entry : bySupplier.entrySet()) {
            List<LowStockAlertDto> lines = entry.getValue();
            Map<String, Object> po = new HashMap<>();
            po.put("supplierId", entry.getKey());
            po.put("supplierName", lines.get(0).getSupplierName());
            BigDecimal total = BigDecimal.ZERO;
            List<Map<String, Object>> items = new ArrayList<>();
            for (LowStockAlertDto a : lines) {
                Map<String, Object> line = new HashMap<>();
                line.put("itemVariantId", a.getItemVariantId());
                line.put("sku", a.getSku());
                line.put("itemName", a.getItemName());
                line.put("currentStock", a.getCurrentStock());
                line.put("threshold", a.getThreshold());
                line.put("suggestedQty", a.getSuggestedOrderQty());
                line.put("lastPurchasePrice", a.getLastPurchasePrice());
                items.add(line);
                BigDecimal qty = a.getSuggestedOrderQty() == null ? BigDecimal.ZERO : a.getSuggestedOrderQty();
                BigDecimal price = a.getLastPurchasePrice() == null ? BigDecimal.ZERO : a.getLastPurchasePrice();
                total = total.add(qty.multiply(price));
            }
            po.put("estimatedValue", total);
            po.put("lineCount", items.size());
            po.put("items", items);
            suggestions.add(po);
        }

        // Ungrouped (no preferred supplier) — surface separately so the buyer
        // can pick a supplier manually.
        List<LowStockAlertDto> orphans = alerts.stream()
                .filter(a -> a.getSupplierId() == null)
                .collect(Collectors.toList());
        if (!orphans.isEmpty()) {
            Map<String, Object> orphanGroup = new HashMap<>();
            orphanGroup.put("supplierId", null);
            orphanGroup.put("supplierName", "(no preferred supplier)");
            orphanGroup.put("lineCount", orphans.size());
            List<Map<String, Object>> items = orphans.stream().map(a -> {
                Map<String, Object> m = new HashMap<>();
                m.put("itemVariantId", a.getItemVariantId());
                m.put("sku", a.getSku());
                m.put("itemName", a.getItemName());
                m.put("currentStock", a.getCurrentStock());
                m.put("threshold", a.getThreshold());
                m.put("suggestedQty", a.getSuggestedOrderQty());
                return m;
            }).collect(Collectors.toList());
            orphanGroup.put("items", items);
            suggestions.add(orphanGroup);
        }
        return suggestions;
    }
}
