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
     * Daily digest at 09:00. Cron is minute-hour-DoM-Mon-DoW.
     */
    @Scheduled(cron = "0 0 9 * * *")
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
    protected boolean notifyShop(Shop shop) throws Exception {
        TenantContext.setCurrentShopId(shop.getId());

        List<LowStockAlertDto> allAlerts = stockService.getLowStockAlerts();
        if (allAlerts == null || allAlerts.isEmpty()) return false;

        // Only CRITICAL — Zoho-style. LOW-level rows accumulate quietly in
        // the UI; the email is reserved for "you might sell out today".
        LocalDateTime cutoff = LocalDateTime.now().minusHours(RESEND_WINDOW_HOURS);
        List<LowStockAlertDto> due = allAlerts.stream()
                .filter(a -> "CRITICAL".equals(a.getAlertLevel()))
                .filter(a -> !recentlyNotified(a.getItemVariantId(), cutoff))
                .collect(Collectors.toList());
        if (due.isEmpty()) return false;

        String html = buildDigest(shop, due);
        emailService.sendEmail(shop.getEmail(),
                buildSubject(shop, due.size()), html);

        // Bump the dedupe stamp on every variant we just notified about,
        // in a single saveAll to keep the round-trips down.
        LocalDateTime now = LocalDateTime.now();
        List<Long> notifiedIds = due.stream().map(LowStockAlertDto::getItemVariantId).collect(Collectors.toList());
        List<ItemVariant> touched = itemVariantRepository.findAllById(notifiedIds);
        for (ItemVariant v : touched) v.setAlertLastNotifiedAt(now);
        itemVariantRepository.saveAll(touched);

        logger.info("Sent low-stock digest for shop {} — {} variants", shop.getId(), due.size());
        return true;
    }

    private boolean recentlyNotified(Long variantId, LocalDateTime cutoff) {
        // Single-shot fetch — sits inside the same transaction as the parent
        // notifyShop, so no N+1 on the repo layer.
        return itemVariantRepository.findById(variantId)
                .map(v -> v.getAlertLastNotifiedAt() != null && v.getAlertLastNotifiedAt().isAfter(cutoff))
                .orElse(false);
    }

    private String buildSubject(Shop shop, int count) {
        return String.format("[%s] %d critical low-stock alert%s",
                shop.getName() != null ? shop.getName() : "VyaparSathi",
                count, count == 1 ? "" : "s");
    }

    /**
     * Builds a self-contained HTML digest. Inline CSS only — most inboxes
     * strip external stylesheets and many strip {@code <style>} blocks as
     * well. Keep the table narrow enough to render on mobile without
     * horizontal scroll.
     */
    private String buildDigest(Shop shop, List<LowStockAlertDto> due) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Arial,Helvetica,sans-serif;color:#1f2937;max-width:640px;\">");
        sb.append("<h2 style=\"color:#dc2626;margin:0 0 8px 0;\">Critical low-stock alerts</h2>");
        sb.append("<p style=\"color:#4b5563;margin:0 0 16px 0;\">The following variants for <strong>")
                .append(escape(shop.getName())).append("</strong> need attention today.</p>");

        sb.append("<table cellspacing=\"0\" cellpadding=\"8\" style=\"border-collapse:collapse;width:100%;")
                .append("border:1px solid #e5e7eb;\">");
        sb.append("<thead style=\"background:#f9fafb;\"><tr>")
                .append(th("Product")).append(th("Current")).append(th("Suggested"))
                .append(th("Supplier")).append(th("Investment"))
                .append("</tr></thead><tbody>");
        BigDecimal totalInvestment = BigDecimal.ZERO;
        for (LowStockAlertDto a : due) {
            BigDecimal qty = a.getSuggestedOrderQty() != null
                    ? a.getSuggestedOrderQty()
                    : (a.getThreshold() != null && a.getCurrentStock() != null
                        ? a.getThreshold().subtract(a.getCurrentStock()).max(BigDecimal.ZERO)
                        : BigDecimal.ZERO);
            BigDecimal cost = qty.multiply(a.getLastPurchasePrice() != null ? a.getLastPurchasePrice() : BigDecimal.ZERO);
            totalInvestment = totalInvestment.add(cost);
            sb.append("<tr style=\"border-top:1px solid #e5e7eb;\">")
                    .append(td("<strong>" + escape(a.getItemName()) + "</strong><br/>"
                            + "<span style=\"color:#6b7280;font-size:12px;font-family:monospace;\">"
                            + escape(a.getSku()) + "</span>"))
                    .append(td(a.getCurrentStock() + " " + safe(a.getUnit())))
                    .append(td("<strong>" + qty + " " + safe(a.getUnit()) + "</strong>"))
                    .append(td(safe(a.getSupplierName(), "—")))
                    .append(td("₹" + cost.setScale(0, java.math.RoundingMode.CEILING)))
                    .append("</tr>");
        }
        sb.append("</tbody></table>");
        sb.append("<p style=\"margin:16px 0 0 0;font-weight:700;\">Estimated total: ₹")
                .append(totalInvestment.setScale(0, java.math.RoundingMode.CEILING)).append("</p>");
        sb.append("<p style=\"color:#9ca3af;font-size:12px;margin-top:24px;\">")
                .append("Sent from VyaparSathi. Manage this digest in Shop Settings → Notifications.")
                .append("</p>");
        sb.append("</div>");
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
