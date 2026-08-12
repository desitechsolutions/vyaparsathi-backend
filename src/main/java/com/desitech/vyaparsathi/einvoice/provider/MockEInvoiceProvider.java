package com.desitech.vyaparsathi.einvoice.provider;

import com.desitech.vyaparsathi.sales.entity.Sale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * In-process fake IRP. Preserves the pre-Phase-3.6 behaviour so dev boxes,
 * integration tests, and shops still below the e-invoicing turnover
 * threshold keep working without any external dependency.
 *
 * Selected by default (property missing) or {@code einvoice.provider=MOCK}.
 * Should NEVER be active in production for a shop above the ₹5 crore threshold.
 */
@Component
@ConditionalOnProperty(prefix = "einvoice", name = "provider", havingValue = "MOCK", matchIfMissing = true)
public class MockEInvoiceProvider implements EInvoiceProvider {

    @Override
    public GenerateResult generateIrn(Sale sale) {
        Long shopId = sale.getShop() != null ? sale.getShop().getId() : 1L;
        String rawKey = shopId + ":" + sale.getInvoiceNo() + ":" + System.currentTimeMillis();
        String irn = hashSha256(rawKey);
        String ackNo = "11" + (System.currentTimeMillis() % 10000000000L);
        LocalDateTime ackDate = LocalDateTime.now();
        String qrCode = "https://einvoice.gst.gov.in/qr?irn=" + irn;
        return new GenerateResult(irn, ackNo, ackDate, null, null, qrCode);
    }

    @Override
    public CancelResult cancelIrn(Sale sale, String cancelReason) {
        return new CancelResult(sale.getIrn(), LocalDateTime.now());
    }

    @Override public String getProviderName() { return "MOCK"; }
    @Override public boolean isLive() { return false; }

    private String hashSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        }
    }
}
