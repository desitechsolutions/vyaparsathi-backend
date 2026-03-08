package com.desitech.vyaparsathi.invoice.controller;

import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.invoice.dto.InvoiceTokenData;
import com.desitech.vyaparsathi.invoice.service.InvoiceService;
import com.desitech.vyaparsathi.invoice.service.SubscriptionInvoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceController.class);
    @Autowired
    private JwtUtil jwtUtil;
    private final InvoiceService invoiceService;
    @Autowired
    private SubscriptionInvoiceService subscriptionInvoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    // ===============================
    // PRINT / PREVIEW (INLINE)
    // ===============================
    @GetMapping(
            value = "/print",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    @Deprecated
    public ResponseEntity<byte[]> printInvoice(
            @RequestParam(required = false) Long saleId,
            @RequestParam(required = false) String invoiceNo
    ) {
        logger.warn("Deprecated endpoint /print used - migrate to /signed");
        if (saleId == null && invoiceNo == null) {
            return ResponseEntity.badRequest().build();
        }

        byte[] pdf = invoiceService.generatePdfBySaleIdOrInvoiceNo(saleId, invoiceNo);

        String filename = "invoice_" +
                (invoiceNo != null ? invoiceNo : saleId) + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + filename + "\""
                )
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(pdf);
    }

    // ===============================
    // DOWNLOAD (ATTACHMENT)
    //Deprecated - use /signed with temporary token instead
    // ===============================
    @GetMapping(
            value = "/download",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    @Deprecated
    public ResponseEntity<byte[]> downloadInvoicePdf(
            @RequestParam(required = false) Long saleId,
            @RequestParam(required = false) String invoiceNo
    ) {
        logger.warn("Deprecated endpoint /download used - migrate to /signed");
        if (saleId == null && invoiceNo == null) {
            return ResponseEntity.badRequest().build();
        }

        byte[] pdf = invoiceService.generatePdfBySaleIdOrInvoiceNo(saleId, invoiceNo);

        String filename = "invoice_" +
                (invoiceNo != null ? invoiceNo : saleId) + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\""
                )
                .body(pdf);
    }
    @GetMapping(value = "/signed")
    public ResponseEntity<?> getSignedInvoice(
                                               @RequestParam String token,
                                               @RequestParam(defaultValue = "false") boolean download) {

        try {
            InvoiceTokenData data = jwtUtil.validateInvoiceToken(token);
            byte[] pdf = invoiceService.generatePdfBySaleIdOrInvoiceNo(data.saleId, data.invoiceNo);

            String filename = "invoice_" + (data.invoiceNo != null ? data.invoiceNo : data.saleId) + ".pdf";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentLength(pdf.length);

            headers.set(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");

            String disposition = download ? "attachment" : "inline";
            headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + filename + "\"");

            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);

        } catch (Exception e) {
            logger.error("Invalid or expired signed invoice token", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Invalid or expired access");
        }
    }

    // ===============================
    // SUBSCRIPTION INVOICE DOWNLOAD
    // ===============================
    @GetMapping(value = "/subscription/{paymentId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadSubscriptionInvoice(@PathVariable Long paymentId) {
        try {
            byte[] pdf = subscriptionInvoiceService.generateSubscriptionInvoicePdf(paymentId);

            String filename = "subscription_invoice_" + paymentId + ".pdf";

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .body(pdf);
        } catch (Exception e) {
            logger.error("Error generating subscription invoice for ID: " + paymentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
