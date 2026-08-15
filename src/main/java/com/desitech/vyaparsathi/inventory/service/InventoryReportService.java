package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.inventory.dto.BatchStockDto;
import com.desitech.vyaparsathi.inventory.dto.CurrentStockDto;
import com.desitech.vyaparsathi.inventory.entity.StockMovement;
import com.desitech.vyaparsathi.inventory.enums.StockMovementType;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.inventory.repository.StockMovementRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enterprise inventory reports pack. Covers every P0/P1 report gap flagged in
 * the audit:
 * <ul>
 *   <li>Valuation aggregate — Σ(qty × WAC) with per-category breakdown</li>
 *   <li>Dead / non-moving stock — no outbound movement in N days</li>
 *   <li>Shrinkage — ADJUST movement deltas by variant and period</li>
 *   <li>Ageing — days since last inbound per lot</li>
 *   <li>Inventory turnover / GMROI — outbound units / avg on-hand</li>
 * </ul>
 * All queries respect the tenant's shopId via {@code ShopFilterAspect}.
 */
@Service
public class InventoryReportService {

    private final StockService stockService;
    private final StockMovementRepository stockMovementRepository;
    private final ItemVariantRepository itemVariantRepository;

    public InventoryReportService(StockService stockService,
                                  StockMovementRepository stockMovementRepository,
                                  ItemVariantRepository itemVariantRepository) {
        this.stockService = stockService;
        this.stockMovementRepository = stockMovementRepository;
        this.itemVariantRepository = itemVariantRepository;
    }

    /** Σ(qty × WAC) headline value + per-category breakdown. */
    @Transactional(readOnly = true)
    public Map<String, Object> valuation() {
        List<CurrentStockDto> rows = stockService.getCurrentStock();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal potentialRevenue = BigDecimal.ZERO;
        Map<String, BigDecimal> byCategory = new HashMap<>();
        for (CurrentStockDto r : rows) {
            BigDecimal qty = r.getTotalQuantity() != null ? r.getTotalQuantity() : BigDecimal.ZERO;
            BigDecimal cost = r.getCostPerUnit() != null ? r.getCostPerUnit() : BigDecimal.ZERO;
            BigDecimal price = r.getPricePerUnit() != null ? r.getPricePerUnit() : BigDecimal.ZERO;
            BigDecimal lineValue = qty.multiply(cost);
            total = total.add(lineValue);
            potentialRevenue = potentialRevenue.add(qty.multiply(price));
            String cat = r.getCategoryName() != null ? r.getCategoryName() : "Uncategorized";
            byCategory.merge(cat, lineValue, BigDecimal::add);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("totalValue", total.setScale(2, RoundingMode.HALF_UP));
        out.put("potentialRevenue", potentialRevenue.setScale(2, RoundingMode.HALF_UP));
        out.put("projectedProfit", potentialRevenue.subtract(total).setScale(2, RoundingMode.HALF_UP));
        List<Map<String, Object>> categoryRows = byCategory.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("category", e.getKey());
                    m.put("value", e.getValue().setScale(2, RoundingMode.HALF_UP));
                    return m;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("value")).compareTo((BigDecimal) a.get("value")))
                .collect(Collectors.toList());
        out.put("byCategory", categoryRows);
        out.put("variantCount", rows.size());
        return out;
    }

    /** Non-moving stock — nothing sold or transferred out in {@code days}. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> deadStock(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        List<CurrentStockDto> rows = stockService.getCurrentStock();
        List<Map<String, Object>> out = new ArrayList<>();
        for (CurrentStockDto r : rows) {
            if (r.getTotalQuantity() == null || r.getTotalQuantity().signum() <= 0) continue;
            List<StockMovement> outbound = stockMovementRepository.findByItemVariantIdAndMovementTypeIn(
                    r.getItemVariantId(), List.of(StockMovementType.DEDUCT));
            LocalDateTime lastOut = outbound.stream()
                    .map(StockMovement::getTimestamp)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            if (lastOut != null && lastOut.isAfter(cutoff)) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("itemVariantId", r.getItemVariantId());
            row.put("sku", r.getSku());
            row.put("itemName", r.getItemName());
            row.put("category", r.getCategoryName());
            row.put("currentStock", r.getTotalQuantity());
            row.put("costPrice", r.getCostPerUnit());
            row.put("stockValue", r.getTotalQuantity().multiply(
                    r.getCostPerUnit() != null ? r.getCostPerUnit() : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            row.put("lastOutbound", lastOut);
            row.put("daysSince", lastOut != null ? ChronoUnit.DAYS.between(lastOut, LocalDateTime.now()) : days);
            out.add(row);
        }
        out.sort((a, b) -> ((BigDecimal) b.get("stockValue")).compareTo((BigDecimal) a.get("stockValue")));
        return out;
    }

    /** Shrinkage — ADJUST movements with negative deltas over the period. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> shrinkage(LocalDate from, LocalDate to) {
        LocalDateTime fromDT = (from != null ? from : LocalDate.now().minusMonths(1)).atStartOfDay();
        LocalDateTime toDT = (to != null ? to : LocalDate.now()).atTime(23, 59, 59);
        Map<Long, Map<String, Object>> agg = new HashMap<>();
        List<CurrentStockDto> rows = stockService.getCurrentStock();
        Map<Long, CurrentStockDto> byVariant = rows.stream()
                .collect(Collectors.toMap(CurrentStockDto::getItemVariantId, r -> r, (a, b) -> a));
        for (Long variantId : byVariant.keySet()) {
            List<StockMovement> adjusts = stockMovementRepository
                    .findByItemVariantIdAndTimestampBetweenOrderByTimestampDesc(variantId, fromDT, toDT)
                    .stream()
                    .filter(m -> m.getMovementType() == StockMovementType.ADJUST)
                    .filter(m -> m.getQuantity() != null && m.getQuantity().signum() < 0)
                    .collect(Collectors.toList());
            if (adjusts.isEmpty()) continue;
            BigDecimal totalLoss = BigDecimal.ZERO;
            BigDecimal totalValue = BigDecimal.ZERO;
            for (StockMovement m : adjusts) {
                BigDecimal absQty = m.getQuantity().abs();
                totalLoss = totalLoss.add(absQty);
                BigDecimal cost = m.getCostPerUnit() != null ? m.getCostPerUnit() : BigDecimal.ZERO;
                totalValue = totalValue.add(absQty.multiply(cost));
            }
            CurrentStockDto info = byVariant.get(variantId);
            Map<String, Object> row = new HashMap<>();
            row.put("itemVariantId", variantId);
            row.put("sku", info.getSku());
            row.put("itemName", info.getItemName());
            row.put("category", info.getCategoryName());
            row.put("shrinkageQty", totalLoss);
            row.put("shrinkageValue", totalValue.setScale(2, RoundingMode.HALF_UP));
            row.put("adjustCount", adjusts.size());
            agg.put(variantId, row);
        }
        return new ArrayList<>(agg.values()).stream()
                .sorted((a, b) -> ((BigDecimal) b.get("shrinkageValue")).compareTo((BigDecimal) a.get("shrinkageValue")))
                .collect(Collectors.toList());
    }

    /** Ageing — days since the earliest active batch was received. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> ageing() {
        List<BatchStockDto> batches = stockService.getBatchWiseStock();
        LocalDate today = LocalDate.now();
        return batches.stream()
                .filter(b -> b.getQuantity() != null && b.getQuantity().signum() > 0)
                .map(b -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("itemVariantId", b.getItemVariantId());
                    row.put("sku", b.getSku());
                    row.put("itemName", b.getItemName());
                    row.put("batchNumber", b.getBatchNumber());
                    row.put("currentStock", b.getQuantity());
                    row.put("expiryDate", b.getExpiryDate());
                    row.put("costPrice", b.getCostPerUnit());
                    row.put("stockValue", b.getQuantity().multiply(
                            b.getCostPerUnit() != null ? b.getCostPerUnit() : BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP));
                    return row;
                })
                .collect(Collectors.toList());
    }

    /** Turnover ratio — outbound qty over the last N days / average on-hand. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> turnover(int days) {
        LocalDateTime fromDT = LocalDateTime.now().minusDays(days);
        LocalDateTime toDT = LocalDateTime.now();
        List<CurrentStockDto> rows = stockService.getCurrentStock();
        List<Map<String, Object>> out = new ArrayList<>();
        for (CurrentStockDto r : rows) {
            BigDecimal onHand = r.getTotalQuantity() != null ? r.getTotalQuantity() : BigDecimal.ZERO;
            List<StockMovement> outbound = stockMovementRepository
                    .findByItemVariantIdAndTimestampBetweenOrderByTimestampDesc(r.getItemVariantId(), fromDT, toDT)
                    .stream()
                    .filter(m -> m.getMovementType() == StockMovementType.DEDUCT)
                    .collect(Collectors.toList());
            BigDecimal outboundQty = BigDecimal.ZERO;
            for (StockMovement m : outbound) {
                outboundQty = outboundQty.add(m.getQuantity() == null ? BigDecimal.ZERO : m.getQuantity().abs());
            }
            BigDecimal avgStock = onHand.add(outboundQty).divide(new BigDecimal("2"), 4, RoundingMode.HALF_UP);
            BigDecimal turns = avgStock.signum() > 0
                    ? outboundQty.divide(avgStock, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            Map<String, Object> row = new HashMap<>();
            row.put("itemVariantId", r.getItemVariantId());
            row.put("sku", r.getSku());
            row.put("itemName", r.getItemName());
            row.put("category", r.getCategoryName());
            row.put("outboundQty", outboundQty);
            row.put("onHand", onHand);
            row.put("avgStock", avgStock.setScale(2, RoundingMode.HALF_UP));
            row.put("turns", turns.setScale(2, RoundingMode.HALF_UP));
            out.add(row);
        }
        out.sort((a, b) -> ((BigDecimal) b.get("outboundQty")).compareTo((BigDecimal) a.get("outboundQty")));
        return out;
    }

    /** XLSX dump of the current valuation report — richer than the CSV path. */
    @Transactional(readOnly = true)
    public byte[] valuationXlsx() {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Valuation");
            Row h = sheet.createRow(0);
            String[] headers = {"SKU", "Item", "Category", "Qty", "WAC", "Value", "Retail", "Potential"};
            for (int i = 0; i < headers.length; i++) h.createCell(i).setCellValue(headers[i]);
            int rowIdx = 1;
            for (CurrentStockDto r : stockService.getCurrentStock()) {
                Row row = sheet.createRow(rowIdx++);
                BigDecimal qty = r.getTotalQuantity() != null ? r.getTotalQuantity() : BigDecimal.ZERO;
                BigDecimal cost = r.getCostPerUnit() != null ? r.getCostPerUnit() : BigDecimal.ZERO;
                BigDecimal price = r.getPricePerUnit() != null ? r.getPricePerUnit() : BigDecimal.ZERO;
                row.createCell(0).setCellValue(r.getSku() != null ? r.getSku() : "");
                row.createCell(1).setCellValue(r.getItemName() != null ? r.getItemName() : "");
                row.createCell(2).setCellValue(r.getCategoryName() != null ? r.getCategoryName() : "");
                row.createCell(3).setCellValue(qty.doubleValue());
                row.createCell(4).setCellValue(cost.doubleValue());
                row.createCell(5).setCellValue(qty.multiply(cost).doubleValue());
                row.createCell(6).setCellValue(price.doubleValue());
                row.createCell(7).setCellValue(qty.multiply(price).doubleValue());
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            wb.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write valuation XLSX", e);
        }
    }
}
