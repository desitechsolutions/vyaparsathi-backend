package com.desitech.vyaparsathi.einvoice.service;

import com.desitech.vyaparsathi.einvoice.entity.EInvoice;

/**
 * Adaptor interface for e-Invoice generation via the NIC IRP or a mock
 * during development. Real production integrations swap
 * {@code MockIrpService} for a bean that calls the actual IRP API.
 */
public interface IrpService {

    /** Generate an IRN + QR for the given document. Throws on failure. */
    EInvoice generate(String documentType, Long documentId, String documentNumber, java.util.Map<String, Object> payload);

    /** Cancel a previously issued IRN. Idempotent when already cancelled. */
    EInvoice cancel(String irn, String reason);
}
