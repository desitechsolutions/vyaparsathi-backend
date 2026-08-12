package com.desitech.vyaparsathi.einvoice.service;

import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.einvoice.dto.EInvoiceResponseDto;
import com.desitech.vyaparsathi.einvoice.provider.EInvoiceProvider;
import com.desitech.vyaparsathi.einvoice.provider.EInvoiceProviderException;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Orchestrator for the e-invoicing flow.
 *
 * Owns persistence + idempotency + status transitions. The actual IRN
 * acquisition (network call to NIC or a local mock hash) lives behind
 * {@link EInvoiceProvider} so this class stays provider-agnostic.
 */
@Service
public class EInvoiceService {

    private static final Logger log = LoggerFactory.getLogger(EInvoiceService.class);

    private final SaleRepository saleRepo;
    private final EInvoiceProvider provider;

    public EInvoiceService(SaleRepository saleRepo, EInvoiceProvider provider) {
        this.saleRepo = saleRepo;
        this.provider = provider;
    }

    @Transactional
    public EInvoiceResponseDto generateIrn(Long saleId) {
        Sale sale = saleRepo.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found with id: " + saleId));

        // Idempotency guard — the front-end can double-click "Generate", the
        // scheduler can retry, etc. If we already have an active IRN, echo it
        // instead of hitting NIC again (which would return a duplicate error
        // anyway, and duplicate errors are billed too).
        if ("GENERATED".equalsIgnoreCase(sale.getEinvoiceStatus())) {
            return toResponse(sale, "GENERATED");
        }

        try {
            EInvoiceProvider.GenerateResult result = provider.generateIrn(sale);
            sale.setIrn(result.irn);
            sale.setAckNo(result.ackNo);
            sale.setAckDate(result.ackDate);
            // Real NIC returns a signed-QR JWT. If the provider gave us a plain
            // URL (mock) we store it; otherwise the JWT — the PDF renderer
            // knows to encode the JWT into a QR image.
            String qrPath = result.signedQrCode != null && !result.signedQrCode.isBlank()
                    ? result.signedQrCode
                    : result.qrCodePath;
            sale.setQrCodePath(qrPath);
            sale.setEinvoiceStatus("GENERATED");
            saleRepo.save(sale);
            log.info("E-invoice generated via {} for sale {} (invoice {})",
                    provider.getProviderName(), sale.getId(), sale.getInvoiceNo());
            return toResponse(sale, "GENERATED");
        } catch (EInvoiceProviderException e) {
            log.error("E-invoice generation failed for sale {} via {}: code={} msg={}",
                    saleId, provider.getProviderName(), e.getErrorCode(), e.getMessage());
            throw e;
        }
    }

    /**
     * NIC's e-invoice cancellation window: 24 hours from IRN generation for a
     * production IRP. We enforce 24 h here (per the current GST portal rule).
     * Kept as a constant so it can be nudged if NIC changes the policy without
     * hunting through the code.
     */
    private static final Duration IRN_CANCEL_WINDOW = Duration.ofHours(24);

    @Transactional
    public EInvoiceResponseDto cancelIrn(Long saleId, String reason) {
        Sale sale = saleRepo.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found with id: " + saleId));

        if (!"GENERATED".equalsIgnoreCase(sale.getEinvoiceStatus())) {
            // Idempotent cancel — nothing to do; return current status.
            return toResponse(sale, sale.getEinvoiceStatus());
        }

        // Enforce NIC's cancellation window. Once the window has passed, the caller
        // must issue a credit note against the invoice instead of cancelling the IRN.
        // We compare against ackDate (when NIC accepted the IRN) — that's the clock
        // NIC starts from. Fall back to Sale.date if ackDate is missing (defensive).
        LocalDateTime issuedAt = sale.getAckDate() != null ? sale.getAckDate() : sale.getDate();
        if (issuedAt != null) {
            Duration age = Duration.between(issuedAt, LocalDateTime.now());
            if (age.compareTo(IRN_CANCEL_WINDOW) > 0) {
                long hoursLate = age.minus(IRN_CANCEL_WINDOW).toHours();
                log.warn("Rejecting IRN cancellation for sale {} — window expired {} h ago (issued {}, cutoff {}h)",
                        saleId, hoursLate, issuedAt, IRN_CANCEL_WINDOW.toHours());
                throw new BusinessValidationException(
                        "IRN cancellation window (" + IRN_CANCEL_WINDOW.toHours() + " hours from generation) has expired. " +
                                "Issue a credit note instead.");
            }
        }

        try {
            // Reason 1: Duplicate, 2: Data Entry Error, 3: Order Cancelled, 4: Other
            String cnlRsn = "3";
            provider.cancelIrn(sale, cnlRsn);
            sale.setEinvoiceStatus("CANCELLED");
            saleRepo.save(sale);
            log.info("E-invoice cancelled via {} for sale {} (irn {})",
                    provider.getProviderName(), sale.getId(), sale.getIrn());
            return toResponse(sale, "CANCELLED");
        } catch (EInvoiceProviderException e) {
            log.error("E-invoice cancellation failed for sale {} via {}: code={} msg={}",
                    saleId, provider.getProviderName(), e.getErrorCode(), e.getMessage());
            throw e;
        }
    }

    private EInvoiceResponseDto toResponse(Sale sale, String status) {
        EInvoiceResponseDto dto = new EInvoiceResponseDto();
        dto.setSaleId(sale.getId());
        dto.setInvoiceNo(sale.getInvoiceNo());
        dto.setIrn(sale.getIrn());
        dto.setAckNo(sale.getAckNo());
        dto.setAckDate(sale.getAckDate());
        dto.setQrCodePath(sale.getQrCodePath());
        dto.setEinvoiceStatus(status);
        return dto;
    }
}
