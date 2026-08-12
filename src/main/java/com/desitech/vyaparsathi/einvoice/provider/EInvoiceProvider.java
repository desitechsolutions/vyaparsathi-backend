package com.desitech.vyaparsathi.einvoice.provider;

import com.desitech.vyaparsathi.sales.entity.Sale;

import java.time.LocalDateTime;

/**
 * Pluggable e-invoice provider.
 *
 * The IRP (Invoice Registration Portal) is the government-run gateway that
 * assigns an IRN (Invoice Reference Number) to each B2B tax invoice above
 * the turnover threshold. Two implementations exist:
 *
 *   • {@code MockEInvoiceProvider} — SHA-256 of shopId+invoiceNo+timestamp.
 *     For dev, integration testing, and shops still below the mandatory
 *     e-invoicing turnover. No network calls.
 *
 *   • {@code NicEInvoiceProvider} — real HTTP to NIC IRP sandbox/prod.
 *     Selected when {@code einvoice.provider=NIC}. Requires per-shop GSTIN
 *     client credentials on file.
 *
 * The provider owns only the IRN acquisition — persistence to {@link Sale}
 * and status-machine transitions live in {@link com.desitech.vyaparsathi.einvoice.service.EInvoiceService}
 * so all providers share the same idempotency + audit behaviour.
 */
public interface EInvoiceProvider {

    /** Result of a successful IRN generation. */
    class GenerateResult {
        public final String irn;
        public final String ackNo;
        public final LocalDateTime ackDate;
        public final String signedInvoice; // JWT payload with signed e-invoice JSON — optional
        public final String signedQrCode;  // JWT string, must be rendered as a QR code on the PDF
        public final String qrCodePath;    // convenience URL for immediate rendering (mock provider)

        public GenerateResult(String irn, String ackNo, LocalDateTime ackDate,
                              String signedInvoice, String signedQrCode, String qrCodePath) {
            this.irn = irn;
            this.ackNo = ackNo;
            this.ackDate = ackDate;
            this.signedInvoice = signedInvoice;
            this.signedQrCode = signedQrCode;
            this.qrCodePath = qrCodePath;
        }
    }

    /** Result of a successful IRN cancellation. */
    class CancelResult {
        public final String irn;
        public final LocalDateTime cancelDate;

        public CancelResult(String irn, LocalDateTime cancelDate) {
            this.irn = irn;
            this.cancelDate = cancelDate;
        }
    }

    /**
     * Register the sale with the IRP and return the assigned IRN + ack + QR.
     * Implementations must be idempotent — if the sale already has an IRN,
     * either return it as-is or return the freshly-generated one (the caller
     * checks status before invoking so double-registration should not happen
     * in practice).
     *
     * @throws EInvoiceProviderException on any failure — HTTP, timeout, invalid
     *     payload, IRP-side rejection. Message should include the IRP error
     *     code when available so the shop owner can act on it.
     */
    GenerateResult generateIrn(Sale sale);

    /**
     * Cancel a previously-issued IRN on the IRP. Cancellation is only allowed
     * within 24 hours of generation per NIC rules; providers should surface
     * the IRP's error code verbatim if the window has expired.
     *
     * @param cancelReason one of "1" (Duplicate), "2" (Data Entry Error),
     *     "3" (Order Cancelled), "4" (Other). Mapped from the free-text
     *     reason by the caller.
     */
    CancelResult cancelIrn(Sale sale, String cancelReason);

    /** Short identifier used in logs / audit trail. */
    String getProviderName();

    /** Signals whether this provider talks to a real government endpoint. */
    boolean isLive();
}
