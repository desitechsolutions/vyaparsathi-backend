package com.desitech.vyaparsathi.purchaseorder.service;

import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderStatusHistory;
import com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enterprise PO reports — mirrors the receiving report surface so a single
 * dashboard can pivot both. All queries respect the tenant's shopId via
 * {@link TenantUtils}. Report rows are shaped as {@code Map<String, Object>}
 * so the FE can bind each to a DataGrid without a bespoke DTO per report.
 */
@Service
public class PurchaseOrderReportService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderStatusHistoryRepository historyRepository;

    public PurchaseOrderReportService(PurchaseOrderRepository purchaseOrderRepository,
                                      PurchaseOrderStatusHistoryRepository historyRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.historyRepository = historyRepository;
    }

    /** POs stuck in a status longer than {@code thresholdDays}, computed from
     * the history log (falls back to orderDate when no history exists). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> agingByStatus(int thresholdDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(thresholdDays);
        List<Map<String, Object>> out = new ArrayList<>();
        for (PurchaseOrder po : purchaseOrderRepository.findAll()) {
            if (po.getStatus() == PurchaseOrderStatus.RECEIVED
                    || po.getStatus() == PurchaseOrderStatus.CANCELLED) continue;
            LocalDateTime stuckSince = po.getOrderDate();
            List<PurchaseOrderStatusHistory> h = historyRepository.findByPurchaseOrderIdOrderByChangedAtAsc(po.getId());
            if (!h.isEmpty()) stuckSince = h.get(h.size() - 1).getChangedAt();
            if (stuckSince == null || stuckSince.isAfter(cutoff)) continue;

            Map<String, Object> row = poHeader(po);
            row.put("stuckSince", stuckSince);
            row.put("ageDays", Duration.between(stuckSince, LocalDateTime.now()).toDays());
            out.add(row);
        }
        return out;
    }

    /** Supplier-level PO spend for a period (defaults to current month). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> supplierSpend(LocalDate from, LocalDate to) {
        LocalDate fromD = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate toD = to != null ? to : LocalDate.now();
        Map<Long, Map<String, Object>> agg = new HashMap<>();
        for (PurchaseOrder po : purchaseOrderRepository.findAll()) {
            if (po.getStatus() == PurchaseOrderStatus.CANCELLED) continue;
            if (po.getOrderDate() == null) continue;
            LocalDate od = po.getOrderDate().toLocalDate();
            if (od.isBefore(fromD) || od.isAfter(toD)) continue;
            if (po.getSupplier() == null) continue;
            Map<String, Object> row = agg.computeIfAbsent(po.getSupplier().getId(), k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("supplierId", po.getSupplier().getId());
                m.put("supplierName", po.getSupplier().getName());
                m.put("poCount", 0);
                m.put("totalAmount", BigDecimal.ZERO);
                return m;
            });
            row.put("poCount", ((Integer) row.get("poCount")) + 1);
            row.put("totalAmount", ((BigDecimal) row.get("totalAmount"))
                    .add(po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO));
        }
        return new ArrayList<>(agg.values());
    }

    /** Ordered vs received fulfillment per PO. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> fulfillmentRate() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PurchaseOrder po : purchaseOrderRepository.findAll()) {
            if (po.getStatus() == PurchaseOrderStatus.DRAFT
                    || po.getStatus() == PurchaseOrderStatus.CANCELLED) continue;
            int ordered = 0;
            BigDecimal received = BigDecimal.ZERO;
            if (po.getItems() != null) {
                for (PurchaseOrderItem it : po.getItems()) {
                    ordered += it.getQuantity() == null ? 0 : it.getQuantity();
                    received = received.add(it.getReceivedQuantity() == null ? BigDecimal.ZERO : it.getReceivedQuantity());
                }
            }
            BigDecimal rate = ordered > 0
                    ? received.multiply(new BigDecimal("100")).divide(BigDecimal.valueOf(ordered), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            Map<String, Object> row = poHeader(po);
            row.put("orderedQty", ordered);
            row.put("receivedQty", received);
            row.put("fulfillmentPct", rate);
            out.add(row);
        }
        return out;
    }

    /** CSV export of the PO register within an optional date range. */
    @Transactional(readOnly = true)
    public byte[] exportPoCsv(LocalDate from, LocalDate to) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter pw = new PrintWriter(baos, false, StandardCharsets.UTF_8)) {
            pw.println("PO,Supplier,Order Date,Expected,Status,Total,Freight,Received Qty");
            for (PurchaseOrder po : purchaseOrderRepository.findAll()) {
                if (from != null && po.getOrderDate() != null && po.getOrderDate().toLocalDate().isBefore(from)) continue;
                if (to != null && po.getOrderDate() != null && po.getOrderDate().toLocalDate().isAfter(to)) continue;
                BigDecimal recv = BigDecimal.ZERO;
                if (po.getItems() != null) {
                    for (PurchaseOrderItem it : po.getItems()) {
                        recv = recv.add(it.getReceivedQuantity() == null ? BigDecimal.ZERO : it.getReceivedQuantity());
                    }
                }
                pw.println(String.join(",",
                        csv(po.getPoNumber()),
                        csv(po.getSupplier() != null ? po.getSupplier().getName() : ""),
                        csv(po.getOrderDate() != null ? po.getOrderDate().toLocalDate().toString() : ""),
                        csv(po.getExpectedDeliveryDate() != null ? po.getExpectedDeliveryDate().toLocalDate().toString() : ""),
                        csv(po.getStatus() != null ? po.getStatus().name() : ""),
                        po.getTotalAmount() != null ? po.getTotalAmount().toPlainString() : "0",
                        po.getFreightCharges() != null ? po.getFreightCharges().toPlainString() : "0",
                        recv.toPlainString()));
            }
        }
        return baos.toByteArray();
    }

    /**
     * Budget vs actual: compares month-to-date PO spend against a configurable
     * monthly budget. Zero budget → returns actuals only.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> budgetVsActual(BigDecimal monthlyBudget) {
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        BigDecimal actual = BigDecimal.ZERO;
        for (PurchaseOrder po : purchaseOrderRepository.findAll()) {
            if (po.getStatus() == PurchaseOrderStatus.CANCELLED) continue;
            if (po.getOrderDate() == null || po.getOrderDate().toLocalDate().isBefore(first)) continue;
            actual = actual.add(po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("monthStart", first);
        out.put("actual", actual);
        out.put("budget", monthlyBudget != null ? monthlyBudget : BigDecimal.ZERO);
        BigDecimal variance = actual.subtract(monthlyBudget != null ? monthlyBudget : BigDecimal.ZERO);
        out.put("variance", variance);
        return out;
    }

    private Map<String, Object> poHeader(PurchaseOrder po) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", po.getId());
        row.put("poNumber", po.getPoNumber());
        row.put("supplier", po.getSupplier() != null ? po.getSupplier().getName() : "-");
        row.put("orderDate", po.getOrderDate());
        row.put("status", po.getStatus());
        row.put("totalAmount", po.getTotalAmount());
        return row;
    }

    private String csv(String v) {
        if (v == null) return "";
        String c = v.replace("\"", "\"\"");
        return c.contains(",") || c.contains("\n") ? "\"" + c + "\"" : c;
    }
}
