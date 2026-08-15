package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import com.desitech.vyaparsathi.purchaseorder.enums.PurchaseOrderStatus;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.entity.ReceivingItem;
import com.desitech.vyaparsathi.receiving.entity.ReceivingTicket;
import com.desitech.vyaparsathi.receiving.enums.ReceivingStatus;
import com.desitech.vyaparsathi.receiving.repository.ReceivingRepository;
import com.desitech.vyaparsathi.receiving.repository.ReceivingTicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enterprise reporting surface for the receiving module. Six reports emitted
 * as {@code Map<String, Object>} payloads so the FE can wire each into either
 * a DataGrid or a chart without a bespoke DTO per report.
 *
 * <p>All queries are shop-scoped via {@link TenantUtils} — the underlying
 * {@code ShopFilterAspect} already gates repository access, so these merely
 * post-process the returned rows.
 */
@Service
public class ReceivingReportService {

    private final ReceivingRepository receivingRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ReceivingTicketRepository ticketRepository;

    public ReceivingReportService(ReceivingRepository receivingRepository,
                                  PurchaseOrderRepository purchaseOrderRepository,
                                  ReceivingTicketRepository ticketRepository) {
        this.receivingRepository = receivingRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.ticketRepository = ticketRepository;
    }

    /** POs still awaiting first receipt or partial receipts. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> pendingReceivals() {
        return purchaseOrderRepository.findAll().stream()
                .filter(po -> po.getStatus() == PurchaseOrderStatus.SUBMITTED
                        || po.getStatus() == PurchaseOrderStatus.PARTIALLY_RECEIVED)
                .map(po -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("poId", po.getId());
                    row.put("poNumber", po.getPoNumber());
                    row.put("supplier", po.getSupplier() != null ? po.getSupplier().getName() : "-");
                    row.put("orderDate", po.getOrderDate());
                    row.put("expectedDeliveryDate", po.getExpectedDeliveryDate());
                    row.put("status", po.getStatus());
                    row.put("totalAmount", po.getTotalAmount());
                    int ordered = po.getItems() == null ? 0
                            : po.getItems().stream().mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum();
                    BigDecimal received = po.getItems() == null ? BigDecimal.ZERO
                            : po.getItems().stream()
                                .map(i -> Optional.ofNullable(i.getReceivedQuantity()).orElse(BigDecimal.ZERO))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                    row.put("orderedQty", ordered);
                    row.put("receivedQty", received);
                    return row;
                })
                .collect(Collectors.toList());
    }

    /** GRNs with any damaged / rejected / overage lines in a date range. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> discrepancyReport(LocalDate from, LocalDate to) {
        Long shopId = TenantUtils.getCurrentShopId();
        return receivingRepository.findAllByShopId(shopId).stream()
                .filter(r -> r.getReceivedAt() != null
                        && !r.getReceivedAt().toLocalDate().isBefore(from)
                        && !r.getReceivedAt().toLocalDate().isAfter(to))
                .filter(r -> r.getItems() != null && r.getItems().stream().anyMatch(it ->
                        (it.getDamagedQty() != null && it.getDamagedQty() > 0)
                        || (it.getRejectedQty() != null && it.getRejectedQty() > 0)
                        || Boolean.TRUE.equals(it.getIsOveraged())))
                .map(this::grnRow)
                .collect(Collectors.toList());
    }

    /** GRNs stuck in PENDING or PARTIALLY_RECEIVED for > threshold days. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> agingGrns(int thresholdDays) {
        Long shopId = TenantUtils.getCurrentShopId();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(thresholdDays);
        return receivingRepository.findAllByShopId(shopId).stream()
                .filter(r -> r.getStatus() == ReceivingStatus.PENDING
                        || r.getStatus() == ReceivingStatus.PARTIALLY_RECEIVED)
                .filter(r -> r.getReceivedAt() != null && r.getReceivedAt().isBefore(cutoff))
                .map(r -> {
                    Map<String, Object> row = grnRow(r);
                    row.put("ageDays", ChronoUnit.DAYS.between(r.getReceivedAt().toLocalDate(), LocalDate.now()));
                    return row;
                })
                .collect(Collectors.toList());
    }

    /** Received items expiring within N days — supermarket / pharma view. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> expiryTracking(int windowDays) {
        Long shopId = TenantUtils.getCurrentShopId();
        LocalDate horizon = LocalDate.now().plusDays(windowDays);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Receiving r : receivingRepository.findAllByShopId(shopId)) {
            if (r.getItems() == null) continue;
            for (ReceivingItem it : r.getItems()) {
                if (it.getExpiryDate() == null) continue;
                if (it.getExpiryDate().isAfter(horizon)) continue;
                Map<String, Object> row = new HashMap<>();
                row.put("grnNumber", r.getGrNumber());
                row.put("receivingId", r.getId());
                row.put("itemName", it.getPurchaseOrderItem() != null
                        && it.getPurchaseOrderItem().getItemVariant() != null
                        && it.getPurchaseOrderItem().getItemVariant().getItem() != null
                        ? it.getPurchaseOrderItem().getItemVariant().getItem().getName()
                        : "Item");
                row.put("batchNumber", it.getBatchNumber());
                row.put("expiryDate", it.getExpiryDate());
                row.put("acceptedQty", it.getAcceptedQty());
                row.put("daysToExpiry", ChronoUnit.DAYS.between(LocalDate.now(), it.getExpiryDate()));
                out.add(row);
            }
        }
        return out;
    }

    /** Per-supplier scorecard: on-time %, damage rate, rejection rate, avg lead time. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> supplierPerformance() {
        Long shopId = TenantUtils.getCurrentShopId();
        Map<Long, List<Receiving>> bySupplier = receivingRepository.findAllByShopId(shopId).stream()
                .filter(r -> r.getPurchaseOrder() != null && r.getPurchaseOrder().getSupplier() != null)
                .collect(Collectors.groupingBy(r -> r.getPurchaseOrder().getSupplier().getId()));

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Long, List<Receiving>> e : bySupplier.entrySet()) {
            List<Receiving> list = e.getValue();
            int total = list.size();
            long onTime = list.stream()
                    .filter(r -> r.getPurchaseOrder() != null && r.getPurchaseOrder().getExpectedDeliveryDate() != null
                            && r.getReceivedAt() != null
                            && !r.getReceivedAt().isAfter(r.getPurchaseOrder().getExpectedDeliveryDate()))
                    .count();
            int damaged = 0, rejected = 0, accepted = 0;
            for (Receiving r : list) {
                if (r.getItems() == null) continue;
                for (ReceivingItem it : r.getItems()) {
                    damaged += Optional.ofNullable(it.getDamagedQty()).orElse(0);
                    rejected += Optional.ofNullable(it.getRejectedQty()).orElse(0);
                    accepted += it.getAcceptedQty();
                }
            }
            int totalUnits = damaged + rejected + accepted;
            Map<String, Object> row = new HashMap<>();
            row.put("supplierId", e.getKey());
            row.put("supplierName", list.get(0).getPurchaseOrder().getSupplier().getName());
            row.put("grnCount", total);
            row.put("onTimeRate", total > 0 ? (100.0 * onTime / total) : 0.0);
            row.put("damageRate", totalUnits > 0 ? (100.0 * damaged / totalUnits) : 0.0);
            row.put("rejectionRate", totalUnits > 0 ? (100.0 * rejected / totalUnits) : 0.0);
            row.put("damagedUnits", damaged);
            row.put("rejectedUnits", rejected);
            out.add(row);
        }
        return out;
    }

    /** CSV export of the GRN register for the given date range. */
    @Transactional(readOnly = true)
    public byte[] exportGrnCsv(LocalDate from, LocalDate to) {
        Long shopId = TenantUtils.getCurrentShopId();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter pw = new PrintWriter(baos, false, StandardCharsets.UTF_8)) {
            pw.println("GRN,PO,Supplier,Received On,Status,Received Qty,Damaged,Rejected,Value");
            for (Receiving r : receivingRepository.findAllByShopId(shopId)) {
                if (from != null && r.getReceivedAt() != null && r.getReceivedAt().toLocalDate().isBefore(from)) continue;
                if (to != null && r.getReceivedAt() != null && r.getReceivedAt().toLocalDate().isAfter(to)) continue;
                int recv = 0, dmg = 0, rej = 0;
                BigDecimal value = BigDecimal.ZERO;
                if (r.getItems() != null) {
                    for (ReceivingItem it : r.getItems()) {
                        recv += Optional.ofNullable(it.getReceivedQty()).orElse(0);
                        dmg += Optional.ofNullable(it.getDamagedQty()).orElse(0);
                        rej += Optional.ofNullable(it.getRejectedQty()).orElse(0);
                        BigDecimal cost = Optional.ofNullable(it.getUnitCost())
                                .orElse(it.getPurchaseOrderItem() != null
                                        ? Optional.ofNullable(it.getPurchaseOrderItem().getUnitCost()).orElse(BigDecimal.ZERO)
                                        : BigDecimal.ZERO);
                        int qty = (it.getReceivedQty() == null ? 0 : it.getReceivedQty())
                                + (it.getDamagedQty() == null ? 0 : it.getDamagedQty())
                                + (it.getRejectedQty() == null ? 0 : it.getRejectedQty());
                        value = value.add(cost.multiply(BigDecimal.valueOf(qty)));
                    }
                }
                pw.println(String.join(",",
                        csv(r.getGrNumber()),
                        csv(r.getPurchaseOrder() != null ? r.getPurchaseOrder().getPoNumber() : ""),
                        csv(r.getPurchaseOrder() != null && r.getPurchaseOrder().getSupplier() != null
                                ? r.getPurchaseOrder().getSupplier().getName() : ""),
                        csv(r.getReceivedAt() != null ? r.getReceivedAt().toLocalDate().toString() : ""),
                        csv(r.getStatus() != null ? r.getStatus().name() : ""),
                        String.valueOf(recv),
                        String.valueOf(dmg),
                        String.valueOf(rej),
                        value.toPlainString()));
            }
        }
        return baos.toByteArray();
    }

    /** Ticket-aging report — anything unresolved past N hours. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> agingTickets(int hours) {
        Long shopId = TenantUtils.getCurrentShopId();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);
        return ticketRepository.findAll().stream()
                .filter(t -> t.getShop() != null && t.getShop().getId().equals(shopId))
                .filter(t -> t.getStatusEnum().isOpen())
                .filter(t -> t.getRaisedAt() != null && t.getRaisedAt().isBefore(cutoff))
                .map(this::ticketRow)
                .collect(Collectors.toList());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> grnRow(Receiving r) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", r.getId());
        row.put("grNumber", r.getGrNumber());
        row.put("poNumber", r.getPurchaseOrder() != null ? r.getPurchaseOrder().getPoNumber() : null);
        row.put("supplier", r.getPurchaseOrder() != null && r.getPurchaseOrder().getSupplier() != null
                ? r.getPurchaseOrder().getSupplier().getName() : null);
        row.put("receivedAt", r.getReceivedAt());
        row.put("status", r.getStatus());
        int recv = 0, dmg = 0, rej = 0;
        if (r.getItems() != null) {
            for (ReceivingItem it : r.getItems()) {
                recv += Optional.ofNullable(it.getReceivedQty()).orElse(0);
                dmg += Optional.ofNullable(it.getDamagedQty()).orElse(0);
                rej += Optional.ofNullable(it.getRejectedQty()).orElse(0);
            }
        }
        row.put("receivedQty", recv);
        row.put("damagedQty", dmg);
        row.put("rejectedQty", rej);
        return row;
    }

    private Map<String, Object> ticketRow(ReceivingTicket t) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", t.getId());
        row.put("receivingId", t.getReceiving() != null ? t.getReceiving().getId() : null);
        row.put("reason", t.getReason());
        row.put("status", t.getStatus());
        row.put("raisedAt", t.getRaisedAt());
        row.put("raisedBy", t.getRaisedBy());
        return row;
    }

    private String csv(String v) {
        if (v == null) return "";
        String cleaned = v.replace("\"", "\"\"");
        return cleaned.contains(",") || cleaned.contains("\n") ? "\"" + cleaned + "\"" : cleaned;
    }
}
