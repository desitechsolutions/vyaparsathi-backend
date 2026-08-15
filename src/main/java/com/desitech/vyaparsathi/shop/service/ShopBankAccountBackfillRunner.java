package com.desitech.vyaparsathi.shop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-shot backfill of the legacy {@code shop.bank_details} blob into the
 * new {@code shop_bank_account} table. Runs on every boot but is idempotent:
 * skips any shop that already has ≥1 row in {@code shop_bank_account}.
 *
 * <p>This mirrors what {@code v100_backfill_bank_accounts()} used to do
 * inside V100 — moved out because MySQL rejects {@code CREATE FUNCTION}
 * with error 1419 when the user lacks {@code SUPER} and
 * {@code log_bin_trust_function_creators} is off (common on managed
 * MySQL / RDS setups).
 *
 * <p>Uses raw JDBC (no repository) so the tenant filter aspect and JPA
 * cascade paths don't interfere. Set
 * {@code shop.bank-accounts.backfill.enabled=false} in application.yml to
 * disable — the default is on, but the CHECK below makes the runner cheap
 * once every shop has been backfilled.
 */
@Component
@ConditionalOnProperty(name = "shop.bank-accounts.backfill.enabled", havingValue = "true", matchIfMissing = true)
@Order(1000)  // Run after DataSeeder and Flyway
public class ShopBankAccountBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ShopBankAccountBackfillRunner.class);

    private final JdbcTemplate jdbc;

    public ShopBankAccountBackfillRunner(@Lazy JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        try {
            // Bail out immediately if there's nothing to migrate. This runs on
            // every boot, and after the first successful backfill every shop
            // already has ≥1 row and the query below returns 0 candidates.
            List<Map<String, Object>> candidates = jdbc.queryForList(
                    "SELECT s.id AS shop_id, s.name AS shop_name, s.upi_id AS upi_id, " +
                    "       s.bank_details AS bank_details " +
                    "  FROM shop s " +
                    " WHERE s.bank_details IS NOT NULL " +
                    "   AND CHAR_LENGTH(TRIM(s.bank_details)) > 0 " +
                    "   AND NOT EXISTS ( " +
                    "         SELECT 1 FROM shop_bank_account b WHERE b.shop_id = s.id " +
                    "   )");

            if (candidates.isEmpty()) {
                log.debug("ShopBankAccountBackfill: nothing to do — all shops already have accounts (or none have blobs).");
                return;
            }

            int inserted = 0;
            for (Map<String, Object> row : candidates) {
                Long shopId = ((Number) row.get("shop_id")).longValue();
                String shopName = (String) row.get("shop_name");
                String upiId = (String) row.get("upi_id");
                String blob = (String) row.get("bank_details");

                Map<String, String> bank = parseBankBlob(blob);
                String holder = pick(bank, "a/c name", "account name", "acc name", "beneficiary", "holder");
                if (holder == null || holder.isBlank()) holder = shopName;

                String number = pick(bank, "a/c number", "account number", "ac number",
                        "a/c no", "account no", "acc no", "acc number");
                String bankName = pick(bank, "bank name", "bank");
                String ifsc = pick(bank, "ifsc code", "ifsc");
                String branch = pick(bank, "branch");

                // Same guard the old SQL had: skip when we don't have enough
                // structured data to produce a useful row. Shop admin can add
                // one from Settings.
                if (isBlank(number) || isBlank(bankName)) {
                    log.info("ShopBankAccountBackfill: shop {} has bank_details blob but no parseable account number or bank name — skipping.", shopId);
                    continue;
                }

                jdbc.update(
                        "INSERT INTO shop_bank_account " +
                        " (shop_id, label, account_holder_name, account_number, bank_name, " +
                        "  ifsc_code, branch, account_type, currency_code, upi_id, " +
                        "  purpose, is_default, is_active, display_on_invoice, notes) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        shopId,
                        "Primary",
                        stripTrailingParen(holder),
                        stripTrailingParen(number),
                        stripTrailingParen(bankName),
                        emptyToNull(stripTrailingParen(ifsc)),
                        emptyToNull(stripTrailingParen(branch)),
                        null,
                        "INR",
                        emptyToNull(upiId),
                        "COLLECTIONS",
                        1,
                        1,
                        1,
                        "Backfilled from legacy shop.bank_details on boot");
                inserted++;
            }

            log.info("ShopBankAccountBackfill: inserted {} default accounts from legacy bank_details blobs.", inserted);
        } catch (RuntimeException e) {
            // Never block application startup on backfill failure.
            log.warn("ShopBankAccountBackfill: skipped due to error — {}", e.getMessage());
        }
    }

    /** Same parsing rules as PartyMapper — kept in sync deliberately. */
    private Map<String, String> parseBankBlob(String blob) {
        Map<String, String> out = new LinkedHashMap<>();
        if (blob == null || blob.isBlank()) return out;
        for (String raw : blob.split("\\r?\\n")) {
            String l = raw.trim();
            int idx = l.indexOf(':');
            if (idx <= 0) continue;
            String key = l.substring(0, idx).trim().toLowerCase().replaceAll("\\s+", " ");
            String val = l.substring(idx + 1).trim();
            if (!key.isEmpty() && !val.isEmpty()) out.put(key, val);
        }
        return out;
    }

    private String pick(Map<String, String> map, String... keys) {
        for (String k : keys) {
            String v = map.get(k);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    /** Strip trailing " (Current)" / " (SB)" annotations that clutter numeric fields. */
    private String stripTrailingParen(String v) {
        if (v == null) return null;
        int i = v.indexOf('(');
        return (i > 0) ? v.substring(0, i).trim() : v.trim();
    }

    private String emptyToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }

    private boolean isBlank(String v) {
        return v == null || v.trim().isEmpty();
    }
}
