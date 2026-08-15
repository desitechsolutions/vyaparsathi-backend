package com.desitech.vyaparsathi.einvoice.service;

import com.desitech.vyaparsathi.einvoice.entity.EInvoice;
import com.desitech.vyaparsathi.einvoice.repository.EInvoiceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Dev / self-hosted mock. Assigns a synthetic 64-char IRN, a random ACK
 * number, an ack date of now, and a base64 QR payload that encodes the
 * document number. Real integrations override this bean.
 */
@Service
@ConditionalOnProperty(name = "einvoice.provider", havingValue = "mock", matchIfMissing = true)
public class MockIrpService implements IrpService {

    private final EInvoiceRepository repository;

    @Value("${einvoice.mock.qr-prefix:VYA-QR}")
    private String qrPrefix;

    public MockIrpService(EInvoiceRepository repository) {
        this.repository = repository;
    }

    @Override
    public EInvoice generate(String documentType, Long documentId, String documentNumber, Map<String, Object> payload) {
        // Idempotent: return existing if already generated
        return repository.findFirstByDocumentTypeAndDocumentIdAndStatus(documentType, documentId, "GENERATED")
                .orElseGet(() -> {
                    EInvoice ei = new EInvoice();
                    ei.setDocumentType(documentType);
                    ei.setDocumentId(documentId);
                    ei.setDocumentNumber(documentNumber);
                    ei.setIrn(UUID.randomUUID().toString().replace("-", "")
                            + Long.toHexString(System.nanoTime()));
                    ei.setAckNumber(String.valueOf(100000000000L + (System.currentTimeMillis() % 900000000000L)));
                    ei.setAckDate(LocalDateTime.now());
                    ei.setQrPayload(Base64.getEncoder().encodeToString(
                            (qrPrefix + "|" + documentType + "|" + documentNumber + "|" + ei.getIrn())
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    ei.setStatus("GENERATED");
                    return repository.save(ei);
                });
    }

    @Override
    public EInvoice cancel(String irn, String reason) {
        return repository.findByIrn(irn).map(ei -> {
            if ("CANCELLED".equals(ei.getStatus())) return ei;
            ei.setStatus("CANCELLED");
            ei.setCancellationReason(reason);
            ei.setCancelledAt(LocalDateTime.now());
            return repository.save(ei);
        }).orElseThrow(() -> new IllegalArgumentException("Unknown IRN: " + irn));
    }
}
