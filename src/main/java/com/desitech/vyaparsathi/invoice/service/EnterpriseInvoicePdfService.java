package com.desitech.vyaparsathi.invoice.service;

import com.desitech.vyaparsathi.common.exception.ExportAppException;
import com.desitech.vyaparsathi.document.dto.EnterpriseDocumentDto;
import com.desitech.vyaparsathi.document.mapper.SaleDocumentMapper;
import com.desitech.vyaparsathi.document.render.EnterpriseDocumentRenderer;
import com.desitech.vyaparsathi.invoice.utils.InvoiceUtil;
import com.desitech.vyaparsathi.payment.service.PaymentService;
import com.desitech.vyaparsathi.sales.entity.Sale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Phase-A enterprise Sales Invoice PDF. Generates a tax invoice using the
 * shared {@link EnterpriseDocumentRenderer}, so it looks identical to
 * every other enterprise document (GRN, PR, DN, CN, PO, Challan).
 *
 * <p>The old {@code InvoiceService.generatePdf} is preserved for
 * backwards compatibility and is used as a fallback until every route
 * migrates. New callers should route through this service.
 */
@Service
public class EnterpriseInvoicePdfService {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseInvoicePdfService.class);

    private final EnterpriseDocumentRenderer renderer;
    private final SaleDocumentMapper mapper;
    private final PaymentService paymentService;
    private final InvoiceUtil invoiceUtil;

    public EnterpriseInvoicePdfService(EnterpriseDocumentRenderer renderer,
                                       SaleDocumentMapper mapper,
                                       PaymentService paymentService,
                                       InvoiceUtil invoiceUtil) {
        this.renderer = renderer;
        this.mapper = mapper;
        this.paymentService = paymentService;
        this.invoiceUtil = invoiceUtil;
    }

    public byte[] generate(Sale sale) {
        if (sale == null) throw new IllegalArgumentException("Sale is required for invoice PDF");
        try {
            BigDecimal paid = paymentService.getTotalPaidBySaleIds(Set.of(sale.getId()))
                    .getOrDefault(sale.getId(), BigDecimal.ZERO);
            EnterpriseDocumentDto doc = mapper.map(sale, paid);
            // Attach logo bytes so the renderer can use them
            if (sale.getShop() != null && sale.getShop().getLogoPath() != null) {
                doc.setLogoBytes(invoiceUtil.loadImageBytes(sale.getShop().getLogoPath(), "logo"));
            }
            if (sale.getShop() != null && sale.getShop().getSignaturePath() != null) {
                doc.setSignatureBytes(invoiceUtil.loadImageBytes(sale.getShop().getSignaturePath(), "signature"));
            }
            return renderer.render(doc);
        } catch (Exception e) {
            log.error("EnterpriseInvoicePdfService failed for sale {}", sale.getId(), e);
            throw new ExportAppException("Failed to generate enterprise invoice PDF for sale " + sale.getId(), e);
        }
    }
}
