package com.desitech.vyaparsathi.inventory.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.inventory.dto.LowStockAlertDto;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.notification.service.EmailService;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sends a daily HTML digest of critical low-stock alerts to each shop owner
 * who opted in via {@code Shop.lowStockAlertsEnabled}. Runs at 09:00 local
 * time — matches when a buyer typically starts planning their morning
 * ordering (before the analytics-scheduler's 06:00 admin-only alert).
 *
 * <p>Dedupe strategy: each variant carries an {@code alert_last_notified_at}
 * stamp on {@link ItemVariant}. A row is included in a digest only if that
 * stamp is null or older than {@link #RESEND_WINDOW_HOURS}. After a
 * successful send, the stamp is bumped forward on every notified variant
 * in one batch save.
 *
 * <p>Silent per-shop failures — an SMTP hiccup on one tenant does NOT
 * abort the whole loop. Errors are logged; the next daily run retries.
 */
@Service
public class LowStockAlertNotificationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(LowStockAlertNotificationScheduler.class);

    // Resend cooldown per variant. 24 h matches Zoho's default digest cadence;
    // adjust here if a shop preference emerges.
    private static final int RESEND_WINDOW_HOURS = 24;

    @Autowired private ShopRepository shopRepository;
    @Autowired private StockService stockService;
    @Autowired private ItemVariantRepository itemVariantRepository;
    @Autowired private EmailService emailService;

    /**
     * Daily low-stock digest in Asia/Kolkata (IST) timezone scheduled at 18:22 (6:22 PM).
     * Can be overridden via app.inventory.low-stock-cron and app.timezone.
     */
    @Scheduled(cron = "${app.inventory.low-stock-cron:0 22 18 * * *}", zone = "${app.timezone:Asia/Kolkata}")
    public void sendDailyDigest() {
        List<Shop> shops = shopRepository.findAll();
        int notifiedShops = 0;
        for (Shop shop : shops) {
            if (!shouldNotify(shop)) continue;
            try {
                if (notifyShop(shop)) notifiedShops++;
            } catch (Exception ex) {
                logger.error("Low-stock digest failed for shop {}: {}", shop.getId(), ex.getMessage(), ex);
            } finally {
                // Always clear — the ThreadLocal is inheritable and would
                // leak into whatever thread the scheduler pool reuses next.
                TenantContext.clear();
            }
        }
        logger.info("Low-stock digest run complete — notified {} shop(s) out of {} scanned",
                notifiedShops, shops.size());
    }

    private boolean shouldNotify(Shop shop) {
        if (!Boolean.TRUE.equals(shop.getActive())) return false;
        if (!Boolean.TRUE.equals(shop.getLowStockAlertsEnabled())) return false;
        String email = shop.getEmail();
        if (email == null || email.isBlank()) return false;
        return true;
    }

    @Transactional
    public boolean notifyShop(Shop shop) throws Exception {
        TenantContext.setCurrentShopId(shop.getId());

        List<LowStockAlertDto> allAlerts = stockService.getLowStockAlerts();
        if (allAlerts == null || allAlerts.isEmpty()) return false;

        // Include all items breaching reorder/low-stock threshold that
        // haven't been notified in the cooldown window. Sort critical (stock <= 0) first.
        LocalDateTime cutoff = LocalDateTime.now().minusHours(RESEND_WINDOW_HOURS);
        List<LowStockAlertDto> due = allAlerts.stream()
                .filter(a -> !recentlyNotified(a.getItemVariantId(), cutoff))
                .sorted((a, b) -> {
                    boolean aCrit = "CRITICAL".equalsIgnoreCase(a.getAlertLevel());
                    boolean bCrit = "CRITICAL".equalsIgnoreCase(b.getAlertLevel());
                    if (aCrit != bCrit) return aCrit ? -1 : 1;
                    return 0;
                })
                .collect(Collectors.toList());
        if (due.isEmpty()) return false;

        String html = buildDigest(shop, due);
        emailService.sendEmail(shop.getEmail(),
                buildSubject(shop, due), html);

        // Bump the dedupe stamp on every variant we just notified about,
        // in a single saveAll to keep round-trips down.
        LocalDateTime now = LocalDateTime.now();
        List<Long> notifiedIds = due.stream().map(LowStockAlertDto::getItemVariantId).collect(Collectors.toList());
        List<ItemVariant> touched = itemVariantRepository.findAllById(notifiedIds);
        for (ItemVariant v : touched) v.setAlertLastNotifiedAt(now);
        itemVariantRepository.saveAll(touched);

        logger.info("Sent low-stock digest for shop {} — {} variants", shop.getId(), due.size());
        return true;
    }

    private boolean recentlyNotified(Long variantId, LocalDateTime cutoff) {
        return itemVariantRepository.findById(variantId)
                .map(v -> v.getAlertLastNotifiedAt() != null && v.getAlertLastNotifiedAt().isAfter(cutoff))
                .orElse(false);
    }

    private String buildSubject(Shop shop, List<LowStockAlertDto> due) {
        long criticalCount = due.stream()
                .filter(a -> "CRITICAL".equalsIgnoreCase(a.getAlertLevel()))
                .count();
        String shopName = shop.getName() != null && !shop.getName().isBlank() ? shop.getName() : "VyaparSathi";

        if (criticalCount > 0) {
            return String.format("[%s] %d low-stock alert%s (%d out of stock)",
                    shopName,
                    due.size(), due.size() == 1 ? "" : "s",
                    criticalCount);
        }
        return String.format("[%s] %d low-stock alert%s",
                shopName,
                due.size(), due.size() == 1 ? "" : "s");
    }

    /**
     * Builds a self-contained HTML digest with status badges and reorder suggestions.
     */
    private String buildDigest(Shop shop, List<LowStockAlertDto> due) {
        long criticalCount = due.stream().filter(a -> "CRITICAL".equalsIgnoreCase(a.getAlertLevel())).count();
        long lowCount = due.size() - criticalCount;

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Arial,Helvetica,sans-serif;color:#1f2937;max-width:680px;margin:0 auto;padding:16px;\">");
        sb.append("<div style=\"background:#1e3a8a;padding:20px;border-radius:8px 8px 0 0;color:#ffffff;\">");
        sb.append("<h2 style=\"margin:0;font-size:20px;\">Inventory Low-Stock Digest</h2>");
        sb.append("<p style=\"margin:6px 0 0 0;font-size:13px;opacity:0.9;\">Shop: <strong>")
                .append(escape(shop.getName())).append("</strong> | ")
                .append(due.size()).append(" item(s) require attention")
                .append("</p></div>");

        sb.append("<div style=\"border:1px solid #e5e7eb;border-top:none;padding:20px;border-radius:0 0 8px 8px;background:#ffffff;\">");
        
        sb.append("<p style=\"color:#4b5563;font-size:14px;margin-top:0;\">")
                .append("Summary: <strong style=\"color:#dc2626;\">").append(criticalCount).append(" Out of Stock</strong>, ")
                .append("<strong style=\"color:#d97706;\">").append(lowCount).append(" Low Stock</strong>.")
                .append("</p>");

        sb.append("<table cellspacing=\"0\" cellpadding=\"8\" style=\"border-collapse:collapse;width:100%;font-size:13px;border:1px solid #e5e7eb;\">");
        sb.append("<thead style=\"background:#f9fafb;\"><tr>")
                .append(th("Status"))
                .append(th("Product / SKU"))
                .append(th("Stock / Threshold"))
                .append(th("Suggested Qty"))
                .append(th("Supplier"))
                .append(th("Est. Cost"))
                .append("</tr></thead><tbody>");

        BigDecimal totalInvestment = BigDecimal.ZERO;
        for (LowStockAlertDto a : due) {
            boolean isCritical = "CRITICAL".equalsIgnoreCase(a.getAlertLevel());
            String badge = isCritical
                    ? "<span style=\"background:#fee2e2;color:#991b1b;padding:2px 8px;border-radius:4px;font-size:11px;font-weight:700;\">Out of Stock</span>"
                    : "<span style=\"background:#fef3c7;color:#92400e;padding:2px 8px;border-radius:4px;font-size:11px;font-weight:700;\">Low Stock</span>";

            BigDecimal qty = a.getSuggestedOrderQty() != null
                    ? a.getSuggestedOrderQty()
                    : (a.getThreshold() != null && a.getCurrentStock() != null
                        ? a.getThreshold().subtract(a.getCurrentStock()).max(BigDecimal.ZERO)
                        : BigDecimal.ZERO);
            BigDecimal cost = qty.multiply(a.getLastPurchasePrice() != null ? a.getLastPurchasePrice() : BigDecimal.ZERO);
            totalInvestment = totalInvestment.add(cost);

            String stockDisplay = (a.getCurrentStock() != null ? a.getCurrentStock().toPlainString() : "0")
                    + " / " + (a.getThreshold() != null ? a.getThreshold().toPlainString() : "—")
                    + " " + safe(a.getUnit());

            sb.append("<tr style=\"border-top:1px solid #e5e7eb;\">")
                    .append(td(badge))
                    .append(td("<strong>" + escape(a.getItemName()) + "</strong><br/>"
                            + "<span style=\"color:#6b7280;font-size:11px;font-family:monospace;\">"
                            + escape(a.getSku()) + "</span>"))
                    .append(td(stockDisplay))
                    .append(td("<strong>" + qty.toPlainString() + " " + safe(a.getUnit()) + "</strong>"))
                    .append(td(safe(a.getSupplierName(), "—")))
                    .append(td("Rs. " + cost.setScale(0, java.math.RoundingMode.CEILING)))
                    .append("</tr>");
        }
        sb.append("</tbody></table>");

        sb.append("<div style=\"margin-top:16px;padding:12px;background:#f9fafb;border-radius:6px;display:flex;justify-content:space-between;\">");
        sb.append("<span style=\"font-size:14px;color:#374151;\">Total Estimated Restock Investment:</span>");
        sb.append("<strong style=\"font-size:15px;color:#1e3a8a;\">Rs. ")
                .append(totalInvestment.setScale(0, java.math.RoundingMode.CEILING)).append("</strong>");
        sb.append("</div>");

        sb.append("<p style=\"color:#9ca3af;font-size:12px;margin-top:20px;text-align:center;\">")
                .append("Sent from VyaparSathi. Manage alert preferences in Shop Settings → Notifications.")
                .append("</p></div></div>");

        return sb.toString();
    }

    private String th(String label) {
        return "<th style=\"text-align:left;font-size:12px;color:#374151;\">" + escape(label) + "</th>";
    }

    private String td(String content) {
        return "<td style=\"font-size:14px;vertical-align:top;\">" + content + "</td>";
    }

    private String safe(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : escape(s);
    }

    private String safe(String s) { return safe(s, ""); }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
